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

import android.app.Notification
import android.graphics.Color
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import com.android.launcher3.R
import com.android.launcher3.graphics.IconPalette
import com.android.launcher3.notification.NotificationFooterLayout.IconAnimationEndListener
import com.android.launcher3.popup.PopupContainerWithArrow
import com.android.launcher3.touch.SwipeDetector
import com.android.launcher3.util.getAttrColor
import kotlinx.android.synthetic.main.notification_content.view.*

/**
 * Utility class to manage notification UI
 */
class NotificationItemView(private val container: PopupContainerWithArrow) {
    private val context = container.context
    private val headerText = container.notification_text
    private val headerCount = container.notification_count
    private val mainView = container.main_view
    private val footer = container.footer.also {
        it.setContainer(this)
    }
    private val swipeDetector = SwipeDetector(context, mainView, SwipeDetector.HORIZONTAL).also {
        it.setDetectableScrollConditions(SwipeDetector.DIRECTION_BOTH, false)
    }
    private val iconView = container.popupItemIcon
    private val header = container.header
    private val divider = container.divider
    private var gutter: View? = null
    private var ignoreTouch = false
    private var animatingNextIcon = false
    private var notificationHeaderTextColor = Notification.COLOR_DEFAULT

    private val tempRect = Rect()

    init {
        mainView.setSwipeDetector(swipeDetector)
    }

    fun addGutter() {
        if (gutter == null) {
            gutter = container.inflateAndAdd(R.layout.notification_gutter, container)
        }
    }

    fun removeFooter() {
        if (container.indexOfChild(footer) >= 0) {
            container.removeView(footer)
            container.removeView(divider)
        }
    }

    fun inverseGutterMargin() {
        val lp = gutter!!.layoutParams as MarginLayoutParams
        val top = lp.topMargin
        lp.topMargin = lp.bottomMargin
        lp.bottomMargin = top
    }

    fun removeAllViews() {
        container.removeView(mainView)
        container.removeView(header)
        if (container.indexOfChild(footer) >= 0) {
            container.removeView(footer)
            container.removeView(divider)
        }
        if (gutter != null) {
            container.removeView(gutter)
        }
    }

    fun updateHeader(notificationCount: Int, iconColor: Int) {
        headerCount.text = if (notificationCount <= 1) "" else notificationCount.toString()
        if (Color.alpha(iconColor) > 0) {
            if (notificationHeaderTextColor == Notification.COLOR_DEFAULT) {
                notificationHeaderTextColor = IconPalette.resolveContrastColor(context, iconColor,
                        getAttrColor(context, R.attr.popupColorPrimary))
            }
            headerText.setTextColor(notificationHeaderTextColor)
            headerCount.setTextColor(notificationHeaderTextColor)
        }
    }

    fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            tempRect[mainView.left, mainView.top, mainView.right] = mainView.bottom
            ignoreTouch = !tempRect.contains(ev.x.toInt(), ev.y.toInt())
            if (!ignoreTouch) {
                container.parent.requestDisallowInterceptTouchEvent(true)
            }
        }
        if (ignoreTouch) return false

        if (mainView.notificationInfo == null) {
            // The notification hasn't been populated yet.
            return false
        }
        swipeDetector.onTouchEvent(ev)
        return swipeDetector.isDraggingOrSettling
    }

    fun onTouchEvent(ev: MotionEvent?): Boolean {
        if (ignoreTouch) return false

        return if (mainView.notificationInfo == null) {
            // The notification hasn't been populated yet.
            false
        } else swipeDetector.onTouchEvent(ev!!)
    }

    fun applyNotificationInfos(notificationInfos: List<NotificationInfo>) {
        if (notificationInfos.isEmpty()) return

        val mainNotification = notificationInfos[0]
        mainView.applyNotificationInfo(mainNotification, false)
        for (i in 1 until notificationInfos.size) {
            footer.addNotificationInfo(notificationInfos[i])
        }
        footer.commitNotificationInfos()
    }

    fun trimNotifications(notificationKeys: List<String>) {
        val dismissedMainNotification = !notificationKeys.contains(
                mainView.notificationInfo?.notificationKey)
        if (dismissedMainNotification && !animatingNextIcon) {
            // Animate the next icon into place as the new main notification.
            animatingNextIcon = true
            mainView.setContentVisibility(View.INVISIBLE)
            mainView.setContentTranslation(0f)
            iconView.getGlobalVisibleRect(tempRect)
            footer.animateFirstNotificationTo(tempRect, object : IconAnimationEndListener {
                override fun onIconAnimationEnd(animatedNotification: NotificationInfo?) {
                    if (animatedNotification != null) {
                        mainView.applyNotificationInfo(animatedNotification, true)
                        mainView.setContentVisibility(View.VISIBLE)
                    }
                    animatingNextIcon = false
                }
            })
        } else {
            footer.trimNotifications(notificationKeys)
        }
    }
}