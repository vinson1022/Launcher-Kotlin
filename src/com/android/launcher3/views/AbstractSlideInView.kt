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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.util.AttributeSet
import android.util.Property
import android.view.MotionEvent
import android.view.View
import android.view.animation.Interpolator
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAnimUtils
import com.android.launcher3.Utilities
import com.android.launcher3.anim.Interpolators
import com.android.launcher3.touch.SwipeDetector
import com.android.launcher3.touch.SwipeDetector.Companion.calculateDuration

/**
 * Extension of AbstractFloatingView with common methods for sliding in from bottom
 */
abstract class AbstractSlideInView(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int
) : AbstractFloatingView(context, attrs, defStyleAttr), SwipeDetector.Listener {

    @JvmField
    protected val launcher: Launcher = Launcher.getLauncher(context)
    protected val swipeDetector = SwipeDetector(context!!, this, SwipeDetector.VERTICAL)

    @JvmField
    protected val openCloseAnimator: ObjectAnimator = LauncherAnimUtils.ofPropertyValuesHolder(this)

    protected lateinit var content: View
    private var scrollInterpolator: Interpolator = Interpolators.SCROLL_CUBIC

    @JvmField
    protected var TRANSLATION_SHIFT =
            object : Property<AbstractSlideInView, Float>(Float::class.java, "translationShift") {
                override fun get(view: AbstractSlideInView): Float {
                    return view._translationShift
                }

                override fun set(view: AbstractSlideInView, value: Float) {
                    view.setTranslationShift(value)
                }
            }
    private val TRANSLATION_SHIFT_CLOSED = 1f
    @JvmField
    protected val TRANSLATION_SHIFT_OPENED = 0f

    // range [0, 1], 0=> completely open, 1=> completely closed
    @JvmField
    protected var _translationShift = TRANSLATION_SHIFT_CLOSED
    @JvmField
    protected var noIntercept = false

    init {
        openCloseAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                swipeDetector.finishedScrolling()
                announceAccessibilityChanges()
            }
        })
    }

    protected open fun setTranslationShift(translationShift: Float) {
        this._translationShift = translationShift
        content.translationY = this._translationShift * content.height
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (noIntercept) return false

        val directionsToDetectScroll = if (swipeDetector.isIdleState) SwipeDetector.DIRECTION_NEGATIVE else 0
        swipeDetector.setDetectableScrollConditions(
                directionsToDetectScroll, false)
        swipeDetector.onTouchEvent(ev)
        return (swipeDetector.isDraggingOrSettling
                || !launcher.dragLayer.isEventOverView(content, ev))
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        swipeDetector.onTouchEvent(ev)
        if (ev.action == MotionEvent.ACTION_UP && swipeDetector.isIdleState) {
            // If we got ACTION_UP without ever starting swipe, close the panel.
            if (!launcher.dragLayer.isEventOverView(content, ev)) {
                close(true)
            }
        }
        return true
    }

    /* SwipeDetector.Listener */
    override fun onDragStart(start: Boolean) {}
    override fun onDrag(displacement: Float, velocity: Float): Boolean {
        var displacement = displacement
        val range = content.height.toFloat()
        displacement = Utilities.boundToRange(displacement, 0f, range)
        setTranslationShift(displacement / range)
        return true
    }

    override fun onDragEnd(velocity: Float, fling: Boolean) {
        if (fling && velocity > 0 || _translationShift > 0.5f) {
            scrollInterpolator = Interpolators.scrollInterpolatorForVelocity(velocity)
            openCloseAnimator.duration = calculateDuration(
                    velocity, TRANSLATION_SHIFT_CLOSED - _translationShift)
            close(true)
        } else {
            openCloseAnimator.setValues(PropertyValuesHolder.ofFloat(
                    TRANSLATION_SHIFT, TRANSLATION_SHIFT_OPENED))
            openCloseAnimator.setDuration(
                    calculateDuration(velocity, _translationShift)).interpolator = Interpolators.DEACCEL
            openCloseAnimator.start()
        }
    }

    protected fun handleClose(animate: Boolean, defaultDuration: Long) {
        if (mIsOpen && !animate) {
            openCloseAnimator.cancel()
            setTranslationShift(TRANSLATION_SHIFT_CLOSED)
            onCloseComplete()
            return
        }
        if (!mIsOpen || openCloseAnimator.isRunning) return

        openCloseAnimator.setValues(
                PropertyValuesHolder.ofFloat(TRANSLATION_SHIFT, TRANSLATION_SHIFT_CLOSED))
        openCloseAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                onCloseComplete()
            }
        })
        if (swipeDetector.isIdleState) {
            openCloseAnimator
                    .setDuration(defaultDuration).interpolator = Interpolators.ACCEL
        } else {
            openCloseAnimator.interpolator = scrollInterpolator
        }
        openCloseAnimator.start()
    }

    protected open fun onCloseComplete() {
        mIsOpen = false
        launcher.dragLayer.removeView(this)
    }
}