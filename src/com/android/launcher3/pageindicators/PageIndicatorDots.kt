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
package com.android.launcher3.pageindicators

import android.animation.*
import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Property
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.getAttrColor
import com.android.launcher3.util.getColorAccent
import kotlin.math.abs

/**
 * [PageIndicator] which shows dots per page. The active page is shown with the current
 * accent color.
 */
class PageIndicatorDots
@JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), PageIndicator {

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.style = Paint.Style.FILL }
    private val dotRadius = resources.getDimension(R.dimen.page_indicator_dot_size) / 2
    private val activeColor = getColorAccent(context)
    private val inActiveColor = getAttrColor(context, android.R.attr.colorControlHighlight)
    private val isRtl = Utilities.isRtl(resources)
    private var numPages = 0
    private var activePage = 0

    /**
     * The current position of the active dot including the animation progress.
     * For ex:
     * 0.0  => Active dot is at position 0
     * 0.33 => Active dot is at position 0 and is moving towards 1
     * 0.50 => Active dot is at position [0, 1]
     * 0.77 => Active dot has left position 0 and is collapsing towards position 1
     * 1.0  => Active dot is at position 1
     */
    private var currentPosition = 0f
    private var finalPosition = 0f
    private var animator: ObjectAnimator? = null
    private var entryAnimationRadiusFactors: FloatArray? = null

    init {
        outlineProvider = MyOutlineProver()
    }

    override fun setScroll(currentScroll: Int, totalScroll: Int) {
        if (numPages > 1) {
            var _currentScroll = currentScroll
            if (isRtl) {
                _currentScroll = totalScroll - _currentScroll
            }
            val scrollPerPage = totalScroll / (numPages - 1)
            val pageToLeft = _currentScroll / scrollPerPage
            val pageToLeftScroll = pageToLeft * scrollPerPage
            val pageToRightScroll = pageToLeftScroll + scrollPerPage
            val scrollThreshold = SHIFT_THRESHOLD * scrollPerPage
            when {
                _currentScroll < pageToLeftScroll + scrollThreshold -> {
                    // scroll is within the left page's threshold
                    animateToPosition(pageToLeft.toFloat())
                }
                _currentScroll > pageToRightScroll - scrollThreshold -> {
                    // scroll is far enough from left page to go to the right page
                    animateToPosition(pageToLeft + 1.toFloat())
                }
                else -> {
                    // scroll is between left and right page
                    animateToPosition(pageToLeft + SHIFT_PER_ANIMATION)
                }
            }
        }
    }

    private fun animateToPosition(position: Float) {
        finalPosition = position
        if (abs(currentPosition - finalPosition) < SHIFT_THRESHOLD) {
            currentPosition = finalPosition
        }
        if (animator == null && currentPosition.compareTo(finalPosition) != 0) {
            val positionForThisAnim = if (currentPosition > finalPosition) currentPosition - SHIFT_PER_ANIMATION else currentPosition + SHIFT_PER_ANIMATION
            animator = ObjectAnimator.ofFloat(this, CURRENT_POSITION, positionForThisAnim).apply {
                addListener(AnimationCycleListener())
                duration = ANIMATION_DURATION
                start()
            }
        }
    }

    fun stopAllAnimations() {
        if (animator != null) {
            animator!!.cancel()
            animator = null
        }
        finalPosition = activePage.toFloat()
        CURRENT_POSITION.set(this, finalPosition)
    }

    /**
     * Sets up up the page indicator to play the entry animation.
     * [.playEntryAnimation] must be called after this.
     */
    fun prepareEntryAnimation() {
        entryAnimationRadiusFactors = FloatArray(numPages)
        invalidate()
    }

    fun playEntryAnimation() {
        val count = entryAnimationRadiusFactors?.size ?: 0
        if (count == 0) {
            entryAnimationRadiusFactors = null
            invalidate()
            return
        }
        val interpolator = OvershootInterpolator(ENTER_ANIMATION_OVERSHOOT_TENSION)
        val animSet = AnimatorSet()
        for (i in 0 until count) {
            val anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = ENTER_ANIMATION_DURATION
                addUpdateListener { animation ->
                    entryAnimationRadiusFactors!![i] = animation.animatedValue as Float
                    invalidate()
                }
                this.interpolator = interpolator
                startDelay = ENTER_ANIMATION_START_DELAY + ENTER_ANIMATION_STAGGERED_DELAY * i.toLong()
            }
            animSet.play(anim)
        }
        animSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                entryAnimationRadiusFactors = null
                invalidateOutline()
                invalidate()
            }
        })
        animSet.start()
    }

    override fun setActiveMarker(activePage: Int) {
        if (this.activePage != activePage) {
            this.activePage = activePage
        }
    }

    override fun setMarkersCount(numMarkers: Int) {
        numPages = numMarkers
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Add extra spacing of mDotRadius on all sides so than entry animation could be run.
        val width = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY)
            MeasureSpec.getSize(widthMeasureSpec)
        else ((numPages * 3 + 2) * dotRadius).toInt()
        val height = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY)
            MeasureSpec.getSize(heightMeasureSpec)
        else (4 * dotRadius).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        // Draw all page indicators;
        var circleGap = 3 * dotRadius
        val startX = (width - numPages * circleGap + dotRadius) / 2
        var x = startX + dotRadius
        val y = height / 2f
        entryAnimationRadiusFactors?.run {
            // During entry animation, only draw the circles
            if (isRtl) {
                x = width - x
                circleGap = -circleGap
            }
            for (i in indices) {
                circlePaint.color = if (i == activePage) activeColor else inActiveColor
                canvas.drawCircle(x, y, dotRadius * this[i], circlePaint)
                x += circleGap
            }
        } ?: run {
            circlePaint.color = inActiveColor
            for (i in 0 until numPages) {
                canvas.drawCircle(x, y, dotRadius, circlePaint)
                x += circleGap
            }
            circlePaint.color = activeColor
            canvas.drawRoundRect(activeRect, dotRadius, dotRadius, circlePaint)
        }
    }// Dot is leaving the left circle.

    // dot is capturing the right circle.
    private val activeRect: RectF
        get() {
            val startCircle: Float = currentPosition
            var delta = currentPosition - startCircle
            val diameter = 2 * dotRadius
            val circleGap = 3 * dotRadius
            val startX = (width - numPages * circleGap + dotRadius) / 2
            sTempRect.top = height * 0.5f - dotRadius
            sTempRect.bottom = height * 0.5f + dotRadius
            sTempRect.left = startX + startCircle * circleGap
            sTempRect.right = sTempRect.left + diameter
            if (delta < SHIFT_PER_ANIMATION) {
                // dot is capturing the right circle.
                sTempRect.right += delta * circleGap * 2
            } else {
                // Dot is leaving the left circle.
                sTempRect.right += circleGap
                delta -= SHIFT_PER_ANIMATION
                sTempRect.left += delta * circleGap * 2
            }
            if (isRtl) {
                val rectWidth = sTempRect.width()
                sTempRect.right = width - sTempRect.left
                sTempRect.left = sTempRect.right - rectWidth
            }
            return sTempRect
        }

    private inner class MyOutlineProver : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            if (entryAnimationRadiusFactors == null) {
                val activeRect = activeRect
                outline.setRoundRect(
                        activeRect.left.toInt(),
                        activeRect.top.toInt(),
                        activeRect.right.toInt(),
                        activeRect.bottom.toInt(),
                        dotRadius
                )
            }
        }
    }

    /**
     * Listener for keep running the animation until the final state is reached.
     */
    private inner class AnimationCycleListener : AnimatorListenerAdapter() {
        private var cancelled = false
        override fun onAnimationCancel(animation: Animator) {
            cancelled = true
        }

        override fun onAnimationEnd(animation: Animator) {
            if (!cancelled) {
                animator = null
                animateToPosition(finalPosition)
            }
        }
    }

    companion object {
        private const val SHIFT_PER_ANIMATION = 0.5f
        private const val SHIFT_THRESHOLD = 0.1f
        private const val ANIMATION_DURATION = 150L
        private const val ENTER_ANIMATION_START_DELAY = 300L
        private const val ENTER_ANIMATION_STAGGERED_DELAY = 150L
        private const val ENTER_ANIMATION_DURATION = 400L

        // This value approximately overshoots to 1.5 times the original size.
        private const val ENTER_ANIMATION_OVERSHOOT_TENSION = 4.9f
        private val sTempRect = RectF()
        private val CURRENT_POSITION = object : Property<PageIndicatorDots, Float>(Float::class.javaPrimitiveType, "current_position") {
            override fun get(obj: PageIndicatorDots): Float {
                return obj.currentPosition
            }

            override fun set(obj: PageIndicatorDots, pos: Float) {
                obj.currentPosition = pos
                obj.invalidate()
                obj.invalidateOutline()
            }
        }
    }
}