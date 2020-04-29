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
import android.animation.ObjectAnimator
import android.annotation.TargetApi
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.AttributeSet
import android.util.FloatProperty
import android.view.View
import android.widget.FrameLayout
import com.android.launcher3.ItemInfo
import com.android.launcher3.Launcher
import com.android.launcher3.anim.AnimationSuccessListener
import com.android.launcher3.anim.Interpolators
import com.android.launcher3.touch.OverScroll.dampedScroll
import com.android.launcher3.touch.SwipeDetector
import com.android.launcher3.touch.SwipeDetector.Companion.calculateDuration
import com.android.launcher3.userevent.nano.LauncherLogProto
import com.android.launcher3.util.getAttrColor
import kotlinx.android.synthetic.main.notification_content.view.*
import kotlin.math.abs
import kotlinx.android.synthetic.main.notification_content.view.title as contentTitle
import kotlinx.android.synthetic.main.notification_content.view.text as contentText

/**
 * A [android.widget.FrameLayout] that contains a single notification,
 * e.g. icon + title + text.
 */
@TargetApi(Build.VERSION_CODES.N)
class NotificationMainView
@JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle), SwipeDetector.Listener {
    private val contentTranslateAnimator = ObjectAnimator.ofFloat(this, CONTENT_TRANSLATION, 0f)
    var notificationInfo: NotificationInfo? = null
        private set
    private var mBackgroundColor = 0
    private var swipeDetector: SwipeDetector? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        val colorBackground = textAndBackground.background as ColorDrawable
        mBackgroundColor = colorBackground.color
        val rippleBackground = RippleDrawable(ColorStateList.valueOf(
                getAttrColor(context, android.R.attr.colorControlHighlight)),
                colorBackground, null)
        textAndBackground.background = rippleBackground
    }

    fun setSwipeDetector(swipeDetector: SwipeDetector) {
        this.swipeDetector = swipeDetector
    }

    /**
     * Sets the content of this view, animating it after a new icon shifts up if necessary.
     */
    fun applyNotificationInfo(mainNotification: NotificationInfo, animate: Boolean) {
        mainNotification.apply {
            notificationInfo = this
            if (!title.isNullOrEmpty() && !text.isNullOrEmpty()) {
                contentTitle.text = title.toString()
                contentText.text = text.toString()
            } else {
                contentTitle.maxLines = 2
                contentTitle.text = if (title.isNullOrEmpty()) text.toString() else title.toString()
                contentText.visibility = View.GONE
            }
            popupItemIcon.background = getIconForBackground(context, mBackgroundColor)
            if (intent != null) {
                setOnClickListener(this)
            }
            setContentTranslation(0f)
            // Add a dummy ItemInfo so that logging populates the correct container and item types
            // instead of DEFAULT_CONTAINERTYPE and DEFAULT_ITEMTYPE, respectively.
            tag = NOTIFICATION_ITEM_INFO
            if (animate) {
                ObjectAnimator.ofFloat(textAndBackground, View.ALPHA, 0f, 1f).setDuration(150).start()
            }
        }
    }

    fun setContentTranslation(translation: Float) {
        textAndBackground.translationX = translation
        popupItemIcon.translationX = translation
    }

    fun setContentVisibility(visibility: Int) {
        textAndBackground.visibility = visibility
        popupItemIcon.visibility = visibility
    }

    fun canChildBeDismissed(): Boolean {
        return notificationInfo != null && notificationInfo!!.dismissable
    }

    fun onChildDismissed() {
        val launcher = Launcher.getLauncher(context)
        launcher.popupDataProvider.cancelNotification(
                notificationInfo!!.notificationKey)
        launcher.userEventDispatcher.logActionOnItem(
                LauncherLogProto.Action.Touch.SWIPE,
                LauncherLogProto.Action.Direction.RIGHT,  // Assume all swipes are right for logging.
                LauncherLogProto.ItemType.NOTIFICATION)
    }

    // SwipeDetector.Listener's
    override fun onDragStart(start: Boolean) {}
    override fun onDrag(displacement: Float, velocity: Float): Boolean {
        setContentTranslation(if (canChildBeDismissed()) displacement else dampedScroll(displacement, width).toFloat())
        contentTranslateAnimator.cancel()
        return true
    }

    override fun onDragEnd(velocity: Float, fling: Boolean) {
        val willExit: Boolean
        val endTranslation: Float
        val startTranslation = textAndBackground.translationX
        if (!canChildBeDismissed()) {
            willExit = false
            endTranslation = 0f
        } else if (fling) {
            willExit = true
            endTranslation = (if (velocity < 0) -width else width).toFloat()
        } else if (abs(startTranslation) > width / 2) {
            willExit = true
            endTranslation = (if (startTranslation < 0) -width else width).toFloat()
        } else {
            willExit = false
            endTranslation = 0f
        }
        val duration = calculateDuration(velocity,
                (endTranslation - startTranslation) / width)
        contentTranslateAnimator.apply {
            removeAllListeners()
            setDuration(duration)
            interpolator = Interpolators.scrollInterpolatorForVelocity(velocity)
            setFloatValues(startTranslation, endTranslation)
            addListener(object : AnimationSuccessListener() {
                override fun onAnimationSuccess(animator: Animator) {
                    swipeDetector!!.finishedScrolling()
                    if (willExit) {
                        onChildDismissed()
                    }
                }
            })
        }
        contentTranslateAnimator.start()
    }

    companion object {
        private val CONTENT_TRANSLATION = object : FloatProperty<NotificationMainView>("contentTranslation") {
            override fun setValue(view: NotificationMainView, v: Float) {
                view.setContentTranslation(v)
            }

            override fun get(view: NotificationMainView): Float {
                return view.textAndBackground.translationX
            }
        }

        // This is used only to track the notification view, so that it can be properly logged.
        @JvmField
        val NOTIFICATION_ITEM_INFO = ItemInfo()
    }
}