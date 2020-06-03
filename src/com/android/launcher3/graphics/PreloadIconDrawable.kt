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
package com.android.launcher3.graphics

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.util.Property
import android.util.SparseArray
import com.android.launcher3.FastBitmapDrawable
import com.android.launcher3.ItemInfoWithIcon
import com.android.launcher3.anim.Interpolators
import com.android.launcher3.graphics.IconPalette.getPreloadProgressColor
import java.lang.ref.WeakReference
import kotlin.math.roundToInt

/**
 * Extension of [FastBitmapDrawable] which shows a progress bar around the icon.
 */
class PreloadIconDrawable(
        info: ItemInfoWithIcon?,
        /**
         * @param progressPath fixed path in the bounds [0, 0, 100, 100] representing a progress bar.
         */
        // Path in [0, 100] bounds.
        private val progressPath: Path,
        context: Context
) : FastBitmapDrawable(info) {
    private val tmpMatrix = Matrix()
    private val pathMeasure = PathMeasure()

    private val scaledTrackPath = Path()
    private val scaledProgressPath = Path()
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private var shadowBitmap: Bitmap? = null
    private val indicatorColor = getPreloadProgressColor(context, mIconColor)
    private var trackAlpha = 0
    private var trackLength = 0f
    private var iconScale = 0f
    private var ranFinishAnimation = false

    // Progress of the internal state. [0, 1] indicates the fraction of completed progress,
    // [1, (1 + COMPLETE_ANIM_FRACTION)] indicates the progress of zoom animation.
    private var internalStateProgress = 0f
    private var currentAnim: ObjectAnimator? = null

    init {
        setInternalProgress(0f)
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        tmpMatrix.setScale(
                (bounds.width() - 2 * PROGRESS_WIDTH - 2 * PROGRESS_GAP) / PATH_SIZE,
                (bounds.height() - 2 * PROGRESS_WIDTH - 2 * PROGRESS_GAP) / PATH_SIZE)
        tmpMatrix.postTranslate(
                bounds.left + PROGRESS_WIDTH + PROGRESS_GAP,
                bounds.top + PROGRESS_WIDTH + PROGRESS_GAP)
        progressPath.transform(tmpMatrix, scaledTrackPath)
        val scale = bounds.width() / PATH_SIZE.toFloat()
        progressPaint.strokeWidth = PROGRESS_WIDTH * scale
        shadowBitmap = getShadowBitmap(bounds.width(), bounds.height(),
                PROGRESS_GAP * scale)
        pathMeasure.setPath(scaledTrackPath, true)
        trackLength = pathMeasure.length
        setInternalProgress(internalStateProgress)
    }

    private fun getShadowBitmap(width: Int, height: Int, shadowRadius: Float): Bitmap? {
        val key = width shl 16 or height
        val shadowRef = sShadowCache[key]
        var shadow = shadowRef?.get()
        if (shadow != null) {
            return shadow
        }
        shadow = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(shadow)
        progressPaint.setShadowLayer(shadowRadius, 0f, 0f, COLOR_SHADOW)
        progressPaint.color = COLOR_TRACK
        progressPaint.alpha = MAX_PAINT_ALPHA
        c.drawPath(scaledTrackPath, progressPaint)
        progressPaint.clearShadowLayer()
        c.setBitmap(null)
        sShadowCache.put(key, WeakReference(shadow))
        return shadow
    }

    public override fun drawInternal(canvas: Canvas, bounds: Rect) {
        if (ranFinishAnimation) {
            super.drawInternal(canvas, bounds)
            return
        }

        // Draw track.
        progressPaint.color = indicatorColor
        progressPaint.alpha = trackAlpha
        shadowBitmap?.apply {
            canvas.drawBitmap(this, bounds.left.toFloat(), bounds.top.toFloat(), progressPaint)
        }
        canvas.drawPath(scaledProgressPath, progressPaint)
        val saveCount = canvas.save()
        canvas.scale(iconScale, iconScale, bounds.exactCenterX(), bounds.exactCenterY())
        super.drawInternal(canvas, bounds)
        canvas.restoreToCount(saveCount)
    }

    /**
     * Updates the install progress based on the level
     */
    override fun onLevelChange(level: Int): Boolean {
        // Run the animation if we have already been bound.
        updateInternalState(level * 0.01f, bounds.width() > 0, false)
        return true
    }

    /**
     * Runs the finish animation if it is has not been run after last call to
     * [.onLevelChange]
     */
    fun maybePerformFinishedAnimation() {
        // If the drawable was recently initialized, skip the progress animation.
        if (internalStateProgress == 0f) {
            internalStateProgress = 1f
        }
        updateInternalState(1 + COMPLETE_ANIM_FRACTION, true, true)
    }

    fun hasNotCompleted() = !ranFinishAnimation

    private fun updateInternalState(finalProgress: Float, shouldAnimate: Boolean, isFinish: Boolean) {
        var shouldAnimate = shouldAnimate
        if (currentAnim != null) {
            currentAnim!!.cancel()
            currentAnim = null
        }
        if (finalProgress.compareTo(internalStateProgress) == 0) {
            return
        }
        if (finalProgress < internalStateProgress) {
            shouldAnimate = false
        }
        if (!shouldAnimate || ranFinishAnimation) {
            setInternalProgress(finalProgress)
        } else {
            ObjectAnimator.ofFloat(this, INTERNAL_STATE, finalProgress).apply {
                duration = ((finalProgress - internalStateProgress) * DURATION_SCALE).toLong()
                interpolator = Interpolators.LINEAR
                if (isFinish) {
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            ranFinishAnimation = true
                        }
                    })
                }
                currentAnim = this
            }.start()
        }
    }

    /**
     * Sets the internal progress and updates the UI accordingly
     * for progress <= 0:
     * - icon in the small scale and disabled state
     * - progress track is visible
     * - progress bar is not visible
     * for 0 < progress < 1
     * - icon in the small scale and disabled state
     * - progress track is visible
     * - progress bar is visible with dominant color. Progress bar is drawn as a fraction of
     * [.mScaledTrackPath].
     * @see PathMeasure.getSegment
     */
    private fun setInternalProgress(progress: Float) {
        internalStateProgress = progress
        if (progress <= 0) {
            iconScale = SMALL_SCALE
            scaledTrackPath.reset()
            trackAlpha = MAX_PAINT_ALPHA
            setIsDisabled(true)
        }
        if (progress < 1 && progress > 0) {
            pathMeasure.getSegment(0f, progress * trackLength, scaledProgressPath, true)
            iconScale = SMALL_SCALE
            trackAlpha = MAX_PAINT_ALPHA
            setIsDisabled(true)
        } else if (progress >= 1) {
            setIsDisabled(false)
            scaledTrackPath.set(scaledProgressPath)
            val fraction = (progress - 1) / COMPLETE_ANIM_FRACTION
            if (fraction >= 1) {
                // Animation has completed
                iconScale = 1f
                trackAlpha = 0
            } else {
                trackAlpha = ((1 - fraction) * MAX_PAINT_ALPHA).roundToInt()
                iconScale = SMALL_SCALE + (1 - SMALL_SCALE) * fraction
            }
        }
        invalidateSelf()
    }

    companion object {
        private val INTERNAL_STATE = object : Property<PreloadIconDrawable, Float>(java.lang.Float.TYPE, "internalStateProgress") {
            override fun get(`object`: PreloadIconDrawable): Float {
                return `object`.internalStateProgress
            }

            override fun set(`object`: PreloadIconDrawable, value: Float) {
                `object`.setInternalProgress(value)
            }
        }
        const val PATH_SIZE = 100
        private const val PROGRESS_WIDTH = 7f
        private const val PROGRESS_GAP = 2f
        private const val MAX_PAINT_ALPHA = 255
        private const val DURATION_SCALE = 500L

        // The smaller the number, the faster the animation would be.
        // Duration = COMPLETE_ANIM_FRACTION * DURATION_SCALE
        private const val COMPLETE_ANIM_FRACTION = 0.3f
        private const val COLOR_TRACK = 0x77EEEEEE
        private const val COLOR_SHADOW = 0x55000000
        private const val SMALL_SCALE = 0.6f
        private val sShadowCache = SparseArray<WeakReference<Bitmap?>>()
    }
}