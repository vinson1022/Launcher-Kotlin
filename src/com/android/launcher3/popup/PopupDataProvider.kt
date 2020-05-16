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
package com.android.launcher3.popup

import android.service.notification.StatusBarNotification
import android.util.Log
import com.android.launcher3.ItemInfo
import com.android.launcher3.Launcher
import com.android.launcher3.Utilities
import com.android.launcher3.badge.BadgeInfo
import com.android.launcher3.model.WidgetItem
import com.android.launcher3.notification.NotificationKeyData
import com.android.launcher3.notification.NotificationListener.Companion.instanceIfConnected
import com.android.launcher3.notification.NotificationListener.NotificationsChangedListener
import com.android.launcher3.popup.PopupContainerWithArrow.Companion.getOpen
import com.android.launcher3.popup.SystemShortcut.Install
import com.android.launcher3.popup.SystemShortcut.Widgets
import com.android.launcher3.shortcuts.DeepShortcutManager.Companion.supportsShortcuts
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.MultiHashMap
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.PackageUserKey.Companion.fromItemInfo
import com.android.launcher3.widget.WidgetListRowEntry
import java.util.*

/**
 * Provides data for the popup menu that appears after long-clicking on apps.
 */
class PopupDataProvider(private val launcher: Launcher) : NotificationsChangedListener {

    /** Maps launcher activity components to their list of shortcut ids.  */
    private var deepShortcutMap = MultiHashMap<ComponentKey, String>()

    /** Maps packages to their BadgeInfo's .  */
    private val packageUserToBadgeInfos = mutableMapOf<PackageUserKey, BadgeInfo>()

    /** Maps packages to their Widgets  */
    var allWidgets = ArrayList<WidgetListRowEntry>()

    override fun onNotificationPosted(postedPackageUserKey: PackageUserKey,
                                      notificationKey: NotificationKeyData, shouldBeFilteredOut: Boolean) {
        val badgeInfo = packageUserToBadgeInfos[postedPackageUserKey]
        val badgeShouldBeRefreshed: Boolean
        if (badgeInfo == null) {
            if (!shouldBeFilteredOut) {
                val newBadgeInfo = BadgeInfo(postedPackageUserKey)
                newBadgeInfo.addOrUpdateNotificationKey(notificationKey)
                packageUserToBadgeInfos[postedPackageUserKey] = newBadgeInfo
                badgeShouldBeRefreshed = true
            } else {
                badgeShouldBeRefreshed = false
            }
        } else {
            badgeShouldBeRefreshed = if (shouldBeFilteredOut) badgeInfo.removeNotificationKey(notificationKey) else badgeInfo.addOrUpdateNotificationKey(notificationKey)
            if (badgeInfo.notificationKeys.size == 0) {
                packageUserToBadgeInfos.remove(postedPackageUserKey)
            }
        }
        if (badgeShouldBeRefreshed) {
            launcher.updateIconBadges(Utilities.singletonHashSet(postedPackageUserKey))
        }
    }

    override fun onNotificationRemoved(removedPackageUserKey: PackageUserKey,
                                       notificationKey: NotificationKeyData) {
        val oldBadgeInfo = packageUserToBadgeInfos[removedPackageUserKey]
        if (oldBadgeInfo != null && oldBadgeInfo.removeNotificationKey(notificationKey)) {
            if (oldBadgeInfo.notificationKeys.size == 0) {
                packageUserToBadgeInfos.remove(removedPackageUserKey)
            }
            launcher.updateIconBadges(Utilities.singletonHashSet(removedPackageUserKey))
            trimNotifications(packageUserToBadgeInfos)
        }
    }

    override fun onNotificationFullRefresh(activeNotifications: List<StatusBarNotification>?) {
        if (activeNotifications == null) return
        // This will contain the PackageUserKeys which have updated badges.
        val updatedBadges = HashMap(packageUserToBadgeInfos)
        packageUserToBadgeInfos.clear()
        for (notification in activeNotifications) {
            val packageUserKey = PackageUserKey.fromNotification(notification)
            var badgeInfo = packageUserToBadgeInfos[packageUserKey]
            if (badgeInfo == null) {
                badgeInfo = BadgeInfo(packageUserKey)
                packageUserToBadgeInfos[packageUserKey] = badgeInfo
            }
            badgeInfo.addOrUpdateNotificationKey(NotificationKeyData
                    .fromNotification(notification))
        }

        // Add and remove from updatedBadges so it contains the PackageUserKeys of updated badges.
        for (packageUserKey in packageUserToBadgeInfos.keys) {
            val prevBadge = updatedBadges[packageUserKey]
            val newBadge = packageUserToBadgeInfos[packageUserKey]
            if (prevBadge == null) {
                updatedBadges[packageUserKey] = newBadge
            } else {
                if (!prevBadge.shouldBeInvalidated(newBadge)) {
                    updatedBadges.remove(packageUserKey)
                }
            }
        }
        if (updatedBadges.isNotEmpty()) {
            launcher.updateIconBadges(updatedBadges.keys)
        }
        trimNotifications(updatedBadges)
    }

    private fun trimNotifications(updatedBadges: Map<PackageUserKey, BadgeInfo>) {
        val openContainer = getOpen(launcher)
        openContainer?.trimNotifications(updatedBadges)
    }

    fun setDeepShortcutMap(deepShortcutMapCopy: MultiHashMap<ComponentKey, String>) {
        deepShortcutMap = deepShortcutMapCopy
        if (LOGD) Log.d(TAG, "bindDeepShortcutMap: $deepShortcutMap")
    }

    fun getShortcutIdsForItem(info: ItemInfo): List<String> {
        if (!supportsShortcuts(info)) {
            return emptyList()
        }
        val component = info.targetComponent ?: return emptyList()
        val ids: List<String>? = deepShortcutMap[ComponentKey(component, info.user)]
        return ids ?: emptyList()
    }

    fun getBadgeInfoForItem(info: ItemInfo?): BadgeInfo? {
        return if (!supportsShortcuts(info!!)) {
            null
        } else packageUserToBadgeInfos[fromItemInfo(info)]
    }

    fun getNotificationKeysForItem(info: ItemInfo?): List<NotificationKeyData> {
        val badgeInfo = getBadgeInfoForItem(info)
        return if (badgeInfo == null) emptyList() else badgeInfo.notificationKeys
    }

    /** This makes a potentially expensive binder call and should be run on a background thread.  */
    fun getStatusBarNotificationsForKeys(
            notificationKeys: List<NotificationKeyData>): List<StatusBarNotification> {
        return instanceIfConnected?.getNotificationsForKeys(notificationKeys) ?: emptyList()
    }

    fun getEnabledSystemShortcutsForItem(info: ItemInfo?): List<SystemShortcut> {
        val systemShortcuts = mutableListOf<SystemShortcut>()
        for (systemShortcut in SYSTEM_SHORTCUTS) {
            if (systemShortcut.getOnClickListener(launcher, info!!) != null) {
                systemShortcuts.add(systemShortcut)
            }
        }
        return systemShortcuts
    }

    fun cancelNotification(notificationKey: String?) {
        val notificationListener = instanceIfConnected ?: return
        notificationListener.cancelNotificationFromLauncher(notificationKey)
    }

    fun getWidgetsForPackageUser(packageUserKey: PackageUserKey): List<WidgetItem>? {
        for (entry in allWidgets) {
            if (entry.pkgItem.packageName == packageUserKey.packageName) {
                val widgets = ArrayList(entry.widgets)
                // Remove widgets not associated with the correct user.
                val iterator = widgets.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().user != packageUserKey.userHandle) {
                        iterator.remove()
                    }
                }
                return if (widgets.isEmpty()) null else widgets
            }
        }
        return null
    }

    companion object {
        private const val LOGD = false
        private const val TAG = "PopupDataProvider"

        /** Note that these are in order of priority.  */
        private val SYSTEM_SHORTCUTS = listOf(
                SystemShortcut.AppInfo(),
                Widgets(),
                Install()
        )
    }

}