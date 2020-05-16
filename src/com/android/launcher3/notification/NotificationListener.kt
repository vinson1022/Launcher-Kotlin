/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.notification

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import android.util.ArraySet
import android.util.Log
import android.util.Pair
import com.android.launcher3.LauncherModel
import com.android.launcher3.SettingsActivity
import com.android.launcher3.notification.NotificationListener.NotificationsChangedListener
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.PackageUserKey.Companion.fromNotification
import com.android.launcher3.util.Secure
import com.android.launcher3.util.SettingsObserver
import java.util.*

/**
 * A [NotificationListenerService] that sends updates to its
 * [NotificationsChangedListener] when notifications are posted or canceled,
 * as well and when this service first connects. An instance of NotificationListener,
 * and its methods for getting notifications, can be obtained via [.getInstanceIfConnected].
 */
@TargetApi(Build.VERSION_CODES.O)
class NotificationListener : NotificationListenerService() {
    private val workerHandler by lazy { Handler(LauncherModel.getWorkerLooper(), workerCallback) }
    private val uiHandler by lazy { Handler(Looper.getMainLooper(), uiCallback) }
    private val tempRanking = Ranking()

    /** Maps groupKey's to the corresponding group of notifications.  */
    private val notificationGroupMap: MutableMap<String, NotificationGroup> = HashMap()

    /** Maps keys to their corresponding current group key  */
    private val notificationGroupKeyMap: MutableMap<String, String> = HashMap()

    /** The last notification key that was dismissed from launcher UI  */
    private var lastKeyDismissedByLauncher: String? = null
    private var badgingObserver: SettingsObserver? = null

    private val uiCallback = Handler.Callback { message ->
        changedListener?.apply {
            when (message.what) {
                MSG_NOTIFICATION_POSTED -> {
                    val msg = message.obj as NotificationPostedMsg
                    onNotificationPosted(msg.packageUserKey, msg.notificationKey, msg.shouldBeFilteredOut)
                }
                MSG_NOTIFICATION_REMOVED -> {
                    val pair = message.obj as Pair<PackageUserKey, NotificationKeyData>
                    onNotificationRemoved(pair.first, pair.second)
                }
                MSG_NOTIFICATION_FULL_REFRESH -> {
                    onNotificationFullRefresh(message.obj as? List<StatusBarNotification>)
                }
            }
        }

        true
    }

    private val workerCallback = Handler.Callback { message ->
        when (message.what) {
            MSG_NOTIFICATION_POSTED -> uiHandler.obtainMessage(message.what, message.obj).sendToTarget()
            MSG_NOTIFICATION_REMOVED -> uiHandler.obtainMessage(message.what, message.obj).sendToTarget()
            MSG_NOTIFICATION_FULL_REFRESH -> {
                val activeNotifications = if (isConnected) {
                    try {
                        filterNotifications(activeNotifications)
                    } catch (ex: SecurityException) {
                        Log.e(TAG, "SecurityException: failed to fetch notifications")
                        emptyList<StatusBarNotification>()
                    }
                } else {
                    emptyList<StatusBarNotification>()
                }
                uiHandler.obtainMessage(message.what, activeNotifications).sendToTarget()
            }
        }
        true
    }

    init {
        instance = this
    }

    override fun onCreate() {
        super.onCreate()
        isCreated = true
    }

    override fun onDestroy() {
        super.onDestroy()
        isCreated = false
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        badgingObserver = object : Secure(contentResolver) {
            override fun onSettingChanged(keySettingEnabled: Boolean) {
                if (!keySettingEnabled) {
                    requestUnbind()
                }
            }
        }.also {
            it.register(SettingsActivity.NOTIFICATION_BADGING)
        }
        onNotificationFullRefresh()
    }

    private fun onNotificationFullRefresh() {
        workerHandler.obtainMessage(MSG_NOTIFICATION_FULL_REFRESH).sendToTarget()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isConnected = false
        badgingObserver?.unregister()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) {
            // There is a bug in platform where we can get a null notification; just ignore it.
            return
        }
        workerHandler.obtainMessage(MSG_NOTIFICATION_POSTED, NotificationPostedMsg(sbn))
                .sendToTarget()
    }

    /**
     * An object containing data to send to MSG_NOTIFICATION_POSTED targets.
     */
    private inner class NotificationPostedMsg internal constructor(sbn: StatusBarNotification) {
        val packageUserKey = fromNotification(sbn)
        val notificationKey: NotificationKeyData = NotificationKeyData.fromNotification(sbn)
        val shouldBeFilteredOut = shouldBeFilteredOut(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) {
            // There is a bug in platform where we can get a null notification; just ignore it.
            return
        }
        val packageUserKeyAndNotificationKey = Pair(fromNotification(sbn),
                NotificationKeyData.fromNotification(sbn))
        workerHandler.obtainMessage(MSG_NOTIFICATION_REMOVED, packageUserKeyAndNotificationKey)
                .sendToTarget()
        val key = sbn.key
        notificationGroupMap[sbn.groupKey]?.apply {
            removeChildKey(key)
            if (isEmpty) {
                if (key == lastKeyDismissedByLauncher) {
                    // Only cancel the group notification if launcher dismissed the last child.
                    cancelNotification(groupSummaryKey)
                }
                notificationGroupMap.remove(sbn.groupKey)
            }
        }

        if (key == lastKeyDismissedByLauncher) {
            lastKeyDismissedByLauncher = null
        }
    }

    fun cancelNotificationFromLauncher(key: String?) {
        lastKeyDismissedByLauncher = key
        cancelNotification(key)
    }

    override fun onNotificationRankingUpdate(rankingMap: RankingMap) {
        super.onNotificationRankingUpdate(rankingMap)
        val keys = rankingMap.orderedKeys
        for (sbn in getActiveNotifications(keys)) {
            updateGroupKeyIfNecessary(sbn)
        }
    }

    private fun updateGroupKeyIfNecessary(sbn: StatusBarNotification) {
        val childKey = sbn.key
        val oldGroupKey = notificationGroupKeyMap[childKey]
        val newGroupKey = sbn.groupKey
        if (oldGroupKey == null || oldGroupKey != newGroupKey) {
            // The group key has changed.
            notificationGroupKeyMap[childKey] = newGroupKey
            if (oldGroupKey != null && notificationGroupMap.containsKey(oldGroupKey)) {
                // Remove the child key from the old group.
                val oldGroup = notificationGroupMap[oldGroupKey]
                oldGroup!!.removeChildKey(childKey)
                if (oldGroup.isEmpty) {
                    notificationGroupMap.remove(oldGroupKey)
                }
            }
        }
        if (sbn.isGroup && newGroupKey != null) {
            // Maintain group info so we can cancel the summary when the last child is canceled.
            val notificationGroup = notificationGroupMap[newGroupKey]
                    ?: NotificationGroup().also { notificationGroupMap[newGroupKey] = it }
            val isGroupSummary = (sbn.notification.flags
                    and Notification.FLAG_GROUP_SUMMARY) != 0
            if (isGroupSummary) {
                notificationGroup.groupSummaryKey = childKey
            } else {
                notificationGroup.addChildKey(childKey)
            }
        }
    }

    /** This makes a potentially expensive binder call and should be run on a background thread.  */
    fun getNotificationsForKeys(keys: List<NotificationKeyData>): List<StatusBarNotification> {
        val notifications = getActiveNotifications(NotificationKeyData.extractKeysOnly(keys).toTypedArray())
        return if (notifications == null) emptyList() else listOf(*notifications)
    }

    /**
     * Filter out notifications that don't have an intent
     * or are headers for grouped notifications.
     *
     * @see .shouldBeFilteredOut
     */
    private fun filterNotifications(
            notifications: Array<StatusBarNotification>?): Array<StatusBarNotification>? {
        if (notifications == null) return null
        val removedNotifications: MutableSet<Int> = ArraySet()
        for (i in notifications.indices) {
            if (shouldBeFilteredOut(notifications[i])) {
                removedNotifications.add(i)
            }
        }
        val filteredNotifications = mutableListOf<StatusBarNotification>()
        for (i in notifications.indices) {
            if (!removedNotifications.contains(i)) {
                filteredNotifications.add(notifications[i])
            }
        }
        return filteredNotifications.toTypedArray()
    }

    private fun shouldBeFilteredOut(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        updateGroupKeyIfNecessary(sbn)
        currentRanking.getRanking(sbn.key, tempRanking)
        if (!tempRanking.canShowBadge()) {
            return true
        }
        if (tempRanking.channel.id == NotificationChannel.DEFAULT_CHANNEL_ID) {
            // Special filtering for the default, legacy "Miscellaneous" channel.
            if (notification.flags and Notification.FLAG_ONGOING_EVENT != 0) {
                return true
            }
        }
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)
        val missingTitleAndText = TextUtils.isEmpty(title) && TextUtils.isEmpty(text)
        val isGroupHeader = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        return isGroupHeader || missingTitleAndText
    }

    interface NotificationsChangedListener {
        fun onNotificationPosted(postedPackageUserKey: PackageUserKey,
                                 notificationKey: NotificationKeyData, shouldBeFilteredOut: Boolean)

        fun onNotificationRemoved(removedPackageUserKey: PackageUserKey,
                                  notificationKey: NotificationKeyData)

        fun onNotificationFullRefresh(activeNotifications: List<StatusBarNotification>?)
    }

    companion object {
        const val TAG = "NotificationListener"
        private const val MSG_NOTIFICATION_POSTED = 1
        private const val MSG_NOTIFICATION_REMOVED = 2
        private const val MSG_NOTIFICATION_FULL_REFRESH = 3
        private var instance: NotificationListener? = null
        private var changedListener: NotificationsChangedListener? = null

        private var isConnected = false
        private var isCreated = false
        @JvmStatic
        val instanceIfConnected: NotificationListener?
            get() = if (isConnected) instance else null

        @JvmStatic
        fun setNotificationsChangedListener(listener: NotificationsChangedListener?) {
            changedListener = listener
            val notificationListener = instanceIfConnected
            if (notificationListener != null) {
                notificationListener.onNotificationFullRefresh()
            } else if (!isCreated && changedListener != null) {
                // User turned off badging globally, so we unbound this service;
                // tell the listener that there are no notifications to remove dots.
                changedListener!!.onNotificationFullRefresh(emptyList())
            }
        }

        @JvmStatic
        fun removeNotificationsChangedListener() {
            changedListener = null
        }
    }
}