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
import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.PendingIntent.CanceledException
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.service.notification.StatusBarNotification
import android.view.View
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState.Companion.getInstance
import com.android.launcher3.graphics.IconPalette
import com.android.launcher3.util.aboveApi26

/**
 * An object that contains relevant information from a [StatusBarNotification]. This should
 * only be created when we need to show the notification contents on the UI; until then, a
 * [com.android.launcher3.badge.BadgeInfo] with only the notification key should
 * be passed around, and then this can be constructed using the StatusBarNotification from
 * [NotificationListener.getNotificationsForKeys].
 */
@TargetApi(26)
class NotificationInfo(context: Context, statusBarNotification: StatusBarNotification) : View.OnClickListener {

    @JvmField
    val notificationKey: String? = statusBarNotification.key

    @JvmField
    val title: CharSequence?
    @JvmField
    val text: CharSequence?
    @JvmField
    val intent: PendingIntent?
    private val autoCancel: Boolean
    @JvmField
    val dismissable: Boolean
    private var badgeIcon: Int = Notification.BADGE_ICON_NONE
    private var iconDrawable: Drawable? = null
    private var iconColor = 0
    var isIconLarge = false

    /**
     * Extracts the data that we need from the StatusBarNotification.
     */
    init {
        val notification = statusBarNotification.notification
        title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)
        text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)
        badgeIcon = notification.badgeIconType
        // Load the icon. Since it is backed by ashmem, we won't copy the entire bitmap
        // into our process as long as we don't touch it and it exists in systemui.
        when (badgeIcon) {
            Notification.BADGE_ICON_SMALL -> {
                // Use the small icon.
                iconDrawable = notification.smallIcon.loadDrawable(context)
                iconColor = statusBarNotification.notification.color
                isIconLarge = false
            }
            else -> {
                // Use the large icon.
                iconDrawable = notification.getLargeIcon().loadDrawable(context)
                isIconLarge = true
            }
        }
        if (iconDrawable == null) {
            iconDrawable = BitmapDrawable(context.resources, getInstance(context).iconCache
                    .getDefaultIcon(statusBarNotification.user).icon)
            badgeIcon = Notification.BADGE_ICON_NONE
        }
        intent = notification.contentIntent
        autoCancel = notification.flags and Notification.FLAG_AUTO_CANCEL != 0
        dismissable = notification.flags and Notification.FLAG_ONGOING_EVENT == 0
    }

    override fun onClick(view: View) {
        if (intent == null) return

        val launcher = Launcher.getLauncher(view.context)
        val activityOptions = ActivityOptions.makeClipRevealAnimation(
                view, 0, 0, view.width, view.height).toBundle()
        try {
            intent.send(null, 0, null, null, null, null, activityOptions)
            launcher.userEventDispatcher.logNotificationLaunch(view, intent)
        } catch (e: CanceledException) {
            e.printStackTrace()
        }
        if (autoCancel) {
            launcher.popupDataProvider.cancelNotification(notificationKey)
        }
        AbstractFloatingView.closeOpenContainer(launcher, AbstractFloatingView.TYPE_ACTION_POPUP)
    }

    fun getIconForBackground(context: Context?, background: Int): Drawable? {
        if (isIconLarge) {
            // Only small icons should be tinted.
            return iconDrawable
        }
        iconColor = IconPalette.resolveContrastColor(context, iconColor, background)
        val icon = iconDrawable!!.mutate()
        // DrawableContainer ignores the color filter if it's already set, so clear it first to
        // get it set and invalidated properly.
        icon.setTintList(null)
        icon.setTint(iconColor)
        return icon
    }

    fun shouldShowIconInBadge(): Boolean {
        // If the icon we're using for this notification matches what the Notification
        // specified should show in the badge, then return true.
        return (isIconLarge && badgeIcon == Notification.BADGE_ICON_LARGE
                || !isIconLarge && badgeIcon == Notification.BADGE_ICON_SMALL)
    }
}