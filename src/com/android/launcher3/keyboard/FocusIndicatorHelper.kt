/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.keyboard

import android.animation.*
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Property
import android.view.View
import android.view.View.OnFocusChangeListener
import com.android.launcher3.R

/**
 * A helper class to draw background of a focused view.
 */
abstract class FocusIndicatorHelper(
        private val container: View
) : OnFocusChangeListener, AnimatorUpdateListener {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val maxAlpha: Int
    private val dirtyRect = Rect()
    private var isDirty = false
    private var lastFocusedView: View? = null
    private var currentView: View? = null
    private var targetView: View? = null

    /**
     * The fraction indicating the position of the focusRect between [.currentView]
     * & [.targetView]
     */
    private var shift: Float
    private var currentAnimation: ObjectAnimator? = null
    private var alpha = 0f

    init {
        val color = container.resources.getColor(R.color.focused_background)
        maxAlpha = Color.alpha(color)
        paint.color = -0x1000000 or color
        setAlpha(0f)
        shift = 0f
    }

    protected fun setAlpha(alpha: Float) {
        this.alpha = alpha
        paint.alpha = (this.alpha * maxAlpha).toInt()
    }

    override fun onAnimationUpdate(animation: ValueAnimator) {
        invalidateDirty()
    }

    protected fun invalidateDirty() {
        if (isDirty) {
            container.invalidate(dirtyRect)
            isDirty = false
        }
        val newRect = drawRect
        if (newRect != null) {
            container.invalidate(newRect)
        }
    }

    fun draw(c: Canvas) {
        if (alpha > 0) {
            val newRect = drawRect
            if (newRect != null) {
                dirtyRect.set(newRect)
                c.drawRect(dirtyRect, paint)
                isDirty = true
            }
        }
    }

    private val drawRect: Rect?
        get() {
            currentView.takeIf { it?.isAttachedToWindow == true }?.apply {
                viewToRect(this, sTempRect1)
                return if (shift > 0 && targetView != null) {
                    viewToRect(targetView!!, sTempRect2)
                    RECT_EVALUATOR.evaluate(shift, sTempRect1, sTempRect2)
                } else {
                    sTempRect1
                }
            }
            return null
        }

    override fun onFocusChange(v: View, hasFocus: Boolean) {
        if (hasFocus) {
            endCurrentAnimation()
            if (alpha > MIN_VISIBLE_ALPHA) {
                targetView = v
                currentAnimation = ObjectAnimator.ofPropertyValuesHolder(this,
                        PropertyValuesHolder.ofFloat(ALPHA, 1f),
                        PropertyValuesHolder.ofFloat(SHIFT, 1f)).apply {
                    addListener(ViewSetListener(v, true))
                }
            } else {
                setCurrentView(v)
                currentAnimation = ObjectAnimator.ofPropertyValuesHolder(this,
                        PropertyValuesHolder.ofFloat(ALPHA, 1f))
            }
            lastFocusedView = v
        } else {
            if (lastFocusedView === v) {
                lastFocusedView = null
                endCurrentAnimation()
                currentAnimation = ObjectAnimator.ofPropertyValuesHolder(this,
                        PropertyValuesHolder.ofFloat(ALPHA, 0f)).apply {
                    addListener(ViewSetListener(null, false))
                }
            }
        }

        // invalidate once
        invalidateDirty()
        lastFocusedView = if (hasFocus) v else null
        currentAnimation?.apply {
            addUpdateListener(this@FocusIndicatorHelper)
            setDuration(ANIM_DURATION).start()
        }
    }

    protected fun endCurrentAnimation() {
        if (currentAnimation != null) {
            currentAnimation!!.cancel()
            currentAnimation = null
        }
    }

    protected fun setCurrentView(v: View?) {
        currentView = v
        shift = 0f
        targetView = null
    }

    /**
     * Gets the position of {@param v} relative to [.mContainer].
     */
    abstract fun viewToRect(v: View, outRect: Rect)
    private inner class ViewSetListener(
            private val viewToSet: View?, 
            private val callOnCancel: Boolean
    ) : AnimatorListenerAdapter() {
        private var called = false
        override fun onAnimationCancel(animation: Animator) {
            if (!callOnCancel) {
                called = true
            }
        }

        override fun onAnimationEnd(animation: Animator) {
            if (!called) {
                setCurrentView(viewToSet)
                called = true
            }
        }

    }

    /**
     * Simple subclass which assumes that the target view is a child of the container.
     */
    class SimpleFocusIndicatorHelper(container: View) : FocusIndicatorHelper(container) {
        override fun viewToRect(v: View, outRect: Rect) {
            outRect[v.left, v.top, v.right] = v.bottom
        }
    }

    companion object {
        private const val MIN_VISIBLE_ALPHA = 0.2f
        private const val ANIM_DURATION: Long = 150
        val ALPHA = object : Property<FocusIndicatorHelper, Float>(
                java.lang.Float.TYPE, "alpha") {
            override fun set(`object`: FocusIndicatorHelper, value: Float) {
                `object`.setAlpha(value)
            }

            override fun get(`object`: FocusIndicatorHelper): Float {
                return `object`.alpha
            }
        }
        val SHIFT = object : Property<FocusIndicatorHelper, Float>(
                java.lang.Float.TYPE, "shift") {
            override fun set(`object`: FocusIndicatorHelper, value: Float) {
                `object`.shift = value
            }

            override fun get(`object`: FocusIndicatorHelper): Float {
                return `object`.shift
            }
        }
        private val RECT_EVALUATOR = RectEvaluator(Rect())
        private val sTempRect1 = Rect()
        private val sTempRect2 = Rect()
    }
}