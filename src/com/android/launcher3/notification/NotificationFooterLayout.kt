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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.android.launcher3.LauncherAnimUtils
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.anim.PropertyListBuilder
import com.android.launcher3.anim.PropertyResetListener
import com.android.launcher3.util.getAttrColor
import kotlinx.android.synthetic.main.notification_content.view.*

/**
 * A [FrameLayout] that contains only icons of notifications.
 * If there are more than [.MAX_FOOTER_NOTIFICATIONS] icons, we add a "..." overflow.
 */
class NotificationFooterLayout
@JvmOverloads
constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    interface IconAnimationEndListener {
        fun onIconAnimationEnd(animatedNotification: NotificationInfo?)
    }

    private val notifications = mutableListOf<NotificationInfo>()
    private val overflowNotifications = mutableListOf<NotificationInfo>()
    private val isRtl = Utilities.isRtl(resources)
    private val backgroundColor = getAttrColor(context, R.attr.popupColorPrimary)
    private var iconLayoutParams = getIconLayoutParams()
    private var container: NotificationItemView? = null

    private fun getIconLayoutParams(): LayoutParams {
        val iconSize = resources.getDimensionPixelSize(R.dimen.notification_footer_icon_size)
        // Compute margin start for each icon such that the icons between the first one
        // and the ellipsis are evenly spaced out.
        val paddingEnd = resources.getDimensionPixelSize(R.dimen.notification_footer_icon_row_padding)
        val ellipsisSpace = (resources.getDimensionPixelSize(R.dimen.horizontal_ellipsis_offset)
                + resources.getDimensionPixelSize(R.dimen.horizontal_ellipsis_size))
        val footerWidth = resources.getDimensionPixelSize(R.dimen.bg_popup_item_width)
        val availableIconRowSpace = (footerWidth - paddingEnd - ellipsisSpace
                - iconSize * MAX_FOOTER_NOTIFICATIONS)
        return LayoutParams(iconSize, iconSize).also {
            it.gravity = Gravity.CENTER_VERTICAL
            it.marginStart = availableIconRowSpace / MAX_FOOTER_NOTIFICATIONS
        }
    }

    fun setContainer(container: NotificationItemView?) {
        this.container = container
    }

    /**
     * Keep track of the NotificationInfo, and then update the UI when
     * [.commitNotificationInfos] is called.
     */
    fun addNotificationInfo(notificationInfo: NotificationInfo) {
        if (notifications.size < MAX_FOOTER_NOTIFICATIONS) {
            notifications.add(notificationInfo)
        } else {
            overflowNotifications.add(notificationInfo)
        }
    }

    /**
     * Adds icons and potentially overflow text for all of the NotificationInfo's
     * added using [.addNotificationInfo].
     */
    fun commitNotificationInfos() {
        iconRow.removeAllViews()
        for (i in notifications.indices) {
            val info = notifications[i]
            addNotificationIconForInfo(info)
        }
        updateOverflowEllipsisVisibility()
    }

    private fun updateOverflowEllipsisVisibility() {
        overflow.visibility = if (overflowNotifications.isEmpty()) View.GONE else View.VISIBLE
    }

    /**
     * Creates an icon for the given NotificationInfo, and adds it to the icon row.
     * @return the icon view that was added
     */
    private fun addNotificationIconForInfo(info: NotificationInfo): View {
        val icon = View(context)
        icon.background = info.getIconForBackground(context, backgroundColor)
        icon.setOnClickListener(info)
        icon.tag = info
        icon.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        iconRow.addView(icon, 0, iconLayoutParams)
        return icon
    }

    fun animateFirstNotificationTo(toBounds: Rect,
                                   callback: IconAnimationEndListener) {
        val animation = LauncherAnimUtils.createAnimatorSet()
        val firstNotification = iconRow.getChildAt(iconRow.childCount - 1)
        val fromBounds = sTempRect
        firstNotification.getGlobalVisibleRect(fromBounds)
        val scale = toBounds.height().toFloat() / fromBounds.height()
        val moveAndScaleIcon: Animator = LauncherAnimUtils.ofPropertyValuesHolder(firstNotification,
                *PropertyListBuilder().scale(scale).translationY(toBounds.top - fromBounds.top
                        + (fromBounds.height() * scale - fromBounds.height()) / 2).build())
        moveAndScaleIcon.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                callback.onIconAnimationEnd(firstNotification.tag as NotificationInfo)
                removeViewFromIconRow(firstNotification)
            }
        })
        animation.play(moveAndScaleIcon)

        // Shift all notifications (not the overflow) over to fill the gap.
        var gapWidth = iconLayoutParams.width + iconLayoutParams.marginStart
        if (isRtl) {
            gapWidth = -gapWidth
        }
        if (overflowNotifications.isNotEmpty()) {
            val notification = overflowNotifications.removeAt(0)
            notifications.add(notification)
            val iconFromOverflow = addNotificationIconForInfo(notification)
            animation.play(ObjectAnimator.ofFloat(iconFromOverflow, View.ALPHA, 0f, 1f))
        }
        val numIcons = iconRow.childCount - 1
        // All children besides the one leaving.
        // We have to reset the translation X to 0 when the new main notification
        // is removed from the footer.
        val propertyResetListener = PropertyResetListener(View.TRANSLATION_X, 0f)
        for (i in 0 until numIcons) {
            val child = iconRow.getChildAt(i)
            val shiftChild: Animator = ObjectAnimator.ofFloat(child, View.TRANSLATION_X, gapWidth.toFloat())
            shiftChild.addListener(propertyResetListener)
            animation.play(shiftChild)
        }
        animation.start()
    }

    private fun removeViewFromIconRow(child: View) {
        iconRow.removeView(child)
        notifications.remove(child.tag)
        updateOverflowEllipsisVisibility()
        if (iconRow.childCount == 0) {
            // There are no more icons in the footer, so hide it.
            if (container != null) {
                container!!.removeFooter()
            }
        }
    }

    fun trimNotifications(notifications: List<String?>) {
        if (!isAttachedToWindow || iconRow.childCount == 0) {
            return
        }
        val overflowIterator = overflowNotifications.iterator()
        while (overflowIterator.hasNext()) {
            if (!notifications.contains(overflowIterator.next().notificationKey)) {
                overflowIterator.remove()
            }
        }
        for (i in iconRow.childCount - 1 downTo 0) {
            val child = iconRow.getChildAt(i)
            val childInfo = child.tag as NotificationInfo
            if (!notifications.contains(childInfo.notificationKey)) {
                removeViewFromIconRow(child)
            }
        }
    }

    companion object {
        private const val MAX_FOOTER_NOTIFICATIONS = 5
        private val sTempRect = Rect()
    }
}