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
package com.android.launcher3.views

import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.TouchDelegate
import android.view.View
import android.view.accessibility.AccessibilityEvent
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Insettable
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.anim.Interpolators
import com.android.launcher3.compat.AccessibilityManagerCompat
import kotlinx.android.synthetic.main.work_tab_bottom_user_education_view.view.*

class BottomUserEducationView(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int
) : AbstractSlideInView(context, attrs, defStyleAttr), Insettable {

    private val insets = Rect()
    private lateinit var closeButton: View

    init {
        content = this
    }

    constructor(context: Context?, attr: AttributeSet?) : this(context, attr, 0)

    override fun onFinishInflate() {
        super.onFinishInflate()
        closeButton = close_bottom_user_tip
        closeButton.setOnClickListener { handleClose(true) }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        setTranslationShift(_translationShift)
        expandTouchAreaOfCloseButton()
    }

    override fun logActionCommand(command: Int) {
        // Since this is on-boarding popup, it is not a user controlled action.
    }

    override fun isOfType(type: Int): Boolean {
        return type and AbstractFloatingView.TYPE_ON_BOARD_POPUP != 0
    }

    override fun setInsets(insets: Rect) {
        // Extend behind left, right, and bottom insets.
        val leftInset = insets.left - this.insets.left
        val rightInset = insets.right - this.insets.right
        val bottomInset = insets.bottom - this.insets.bottom
        this.insets.set(insets)
        setPadding(paddingLeft + leftInset, paddingTop,
                paddingRight + rightInset, paddingBottom + bottomInset)
    }

    override fun handleClose(animate: Boolean) {
        handleClose(animate, DEFAULT_CLOSE_DURATION.toLong())
        if (animate) {
            // We animate only when the user is visible, which is a proxy for an explicit
            // close action.
            launcher.sharedPrefs.edit()
                    .putBoolean(KEY_SHOWED_BOTTOM_USER_EDUCATION, true).apply()
            AccessibilityManagerCompat.sendCustomAccessibilityEvent(
                    this@BottomUserEducationView,
                    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                    context.getString(R.string.bottom_work_tab_user_education_closed))
        }
    }

    private fun open(animate: Boolean) {
        if (isOpen || openCloseAnimator.isRunning) return

        isOpen = true
        if (animate) {
            openCloseAnimator.setValues(
                    PropertyValuesHolder.ofFloat(TRANSLATION_SHIFT, TRANSLATION_SHIFT_OPENED))
            openCloseAnimator.interpolator = Interpolators.FAST_OUT_SLOW_IN
            openCloseAnimator.start()
        } else {
            setTranslationShift(TRANSLATION_SHIFT_OPENED)
        }
    }

    private fun expandTouchAreaOfCloseButton() {
        val hitRect = Rect()
        closeButton.getHitRect(hitRect)
        hitRect.left -= closeButton.width
        hitRect.top -= closeButton.height
        hitRect.right += closeButton.width
        hitRect.bottom += closeButton.height
        val parent = closeButton.parent as View
        parent.touchDelegate = TouchDelegate(hitRect, closeButton)
    }

    companion object {
        private const val KEY_SHOWED_BOTTOM_USER_EDUCATION = "showed_bottom_user_education"
        private const val DEFAULT_CLOSE_DURATION = 200
        @JvmStatic
        fun showIfNeeded(launcher: Launcher) {
            if (launcher.sharedPrefs.getBoolean(KEY_SHOWED_BOTTOM_USER_EDUCATION, false)) {
                return
            }
            val layoutInflater = LayoutInflater.from(launcher)
            val bottomUserEducationView = layoutInflater.inflate(
                    R.layout.work_tab_bottom_user_education_view, launcher.dragLayer,
                    false) as BottomUserEducationView
            launcher.dragLayer.addView(bottomUserEducationView)
            bottomUserEducationView.open(true)
        }
    }
}