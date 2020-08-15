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
package com.android.launcher3.folder

import android.R
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.*
import android.support.v4.graphics.ColorUtils
import android.util.Property
import android.view.View
import com.android.launcher3.CellLayout
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAnimUtils
import com.android.launcher3.util.getAttrColor
import kotlin.math.min

/**
 * This object represents a FolderIcon preview background. It stores drawing / measurement
 * information, handles drawing, and animation (accept state <--> rest state).
 */
class PreviewBackground {
    private val clipPorterDuffXfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)

    // Create a RadialGradient such that it draws a black circle and then extends with
    // transparent. To achieve this, we keep the gradient to black for the range [0, 1) and
    // just at the edge quickly change it to transparent.
    private val clipShader = RadialGradient(0f, 0f, 1f, intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT), floatArrayOf(0f, 0.999f, 1f),
            Shader.TileMode.CLAMP)
    private val shadowPorterDuffXfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
    private var shadowShader: RadialGradient? = null
    private val shaderMatrix = Matrix()
    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    @JvmField
    var scale = 1f
    private var colorMultiplier = 1f
    var badgeColor = 0
        private set
    var strokeWidth = 0f
        private set
    private var strokeAlpha = MAX_BG_OPACITY
    private var shadowAlpha = 255
    private var invalidateDelegate: View? = null
    @JvmField
    var previewSize = 0
    @JvmField
    var basePreviewOffsetX = 0
    @JvmField
    var basePreviewOffsetY = 0
    private var drawingDelegate: CellLayout? = null
    @JvmField
    var delegateCellX = 0
    @JvmField
    var delegateCellY = 0

    // When the PreviewBackground is drawn under an icon (for creating a folder) the border
    // should not occlude the icon
    @JvmField
    var isClipping = true
    private var scaleAnimator: ValueAnimator? = null
    private var strokeAlphaAnimator: ObjectAnimator? = null
    private var shadowAnimator: ObjectAnimator? = null

    fun setup(launcher: Launcher, invalidateDelegate: View?,
              availableSpaceX: Int, topPadding: Int) {
        this.invalidateDelegate = invalidateDelegate
        badgeColor = getAttrColor(launcher, R.attr.colorPrimary)
        val grid = launcher.deviceProfile
        previewSize = grid.folderIconSizePx
        basePreviewOffsetX = (availableSpaceX - previewSize) / 2
        basePreviewOffsetY = topPadding + grid.folderIconOffsetYPx

        // Stroke width is 1dp
        strokeWidth = launcher.resources.displayMetrics.density
        val radius = scaledRadius.toFloat()
        val shadowRadius = radius + strokeWidth
        val shadowColor = Color.argb(SHADOW_OPACITY, 0, 0, 0)
        shadowShader = RadialGradient(0f, 0f, 1f, intArrayOf(shadowColor, Color.TRANSPARENT), floatArrayOf(radius / shadowRadius, 1f),
                Shader.TileMode.CLAMP)
        invalidate()
    }

    val radius: Int
        get() = previewSize / 2

    val scaledRadius: Int
        get() = (scale * radius).toInt()

    val offsetX: Int
        get() = basePreviewOffsetX - (scaledRadius - radius)

    val offsetY: Int
        get() = basePreviewOffsetY - (scaledRadius - radius)

    /**
     * Returns the progress of the scale animation, where 0 means the scale is at 1f
     * and 1 means the scale is at ACCEPT_SCALE_FACTOR.
     */
    val scaleProgress: Float
        get() = (scale - 1f) / (ACCEPT_SCALE_FACTOR - 1f)

    fun invalidate() {
        invalidateDelegate?.invalidate()
        drawingDelegate?.invalidate()
    }

    fun setInvalidateDelegate(invalidateDelegate: View?) {
        this.invalidateDelegate = invalidateDelegate
        invalidate()
    }

    val bgColor: Int
        get() {
            val alpha = min(MAX_BG_OPACITY.toFloat(), BG_OPACITY * colorMultiplier).toInt()
            return ColorUtils.setAlphaComponent(badgeColor, alpha)
        }

    fun drawBackground(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.color = bgColor
        drawCircle(canvas, 0f)
        drawShadow(canvas)
    }

    fun drawShadow(canvas: Canvas) {
        if (shadowShader == null) {
            return
        }
        val radius = scaledRadius.toFloat()
        val shadowRadius = radius + strokeWidth
        paint.style = Paint.Style.FILL
        paint.color = Color.BLACK
        val offsetX = offsetX
        val offsetY = offsetY
        val saveCount: Int
        if (canvas.isHardwareAccelerated) {
            saveCount = canvas.saveLayer(offsetX - strokeWidth, offsetY.toFloat(),
                    offsetX + radius + shadowRadius, offsetY + shadowRadius + shadowRadius, null)
        } else {
            saveCount = canvas.save()
            canvas.clipPath(clipPath, Region.Op.DIFFERENCE)
        }
        shaderMatrix.setScale(shadowRadius, shadowRadius)
        shaderMatrix.postTranslate(radius + offsetX, shadowRadius + offsetY)
        shadowShader!!.setLocalMatrix(shaderMatrix)
        with(paint) {
            alpha = shadowAlpha
            shader = shadowShader
            canvas.drawPaint(this)
            alpha = 255
            shader = null
            if (canvas.isHardwareAccelerated) {
                xfermode = shadowPorterDuffXfermode
                canvas.drawCircle(radius + offsetX, radius + offsetY, radius, this)
                xfermode = null
            }
        }

        canvas.restoreToCount(saveCount)
    }

    fun fadeInBackgroundShadow() {
        shadowAnimator?.cancel()
        shadowAnimator = ObjectAnimator.ofInt(this, SHADOW_ALPHA, 0, 255).apply {
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    shadowAnimator = null
                }
            })
            duration = 100
            start()
        }
    }

    fun animateBackgroundStroke() {
        strokeAlphaAnimator?.cancel()
        strokeAlphaAnimator = ObjectAnimator.ofInt(this, STROKE_ALPHA, MAX_BG_OPACITY / 2, MAX_BG_OPACITY).apply {
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    strokeAlphaAnimator = null
                }
            })
            duration = 100
            start()
        }
    }

    fun drawBackgroundStroke(canvas: Canvas) {
        paint.color = ColorUtils.setAlphaComponent(badgeColor, strokeAlpha)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        drawCircle(canvas, 1f)
    }

    fun drawLeaveBehind(canvas: Canvas) {
        val originalScale = scale
        scale = 0.5f
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(160, 245, 245, 245)
        drawCircle(canvas, 0f)
        scale = originalScale
    }

    private fun drawCircle(canvas: Canvas, deltaRadius: Float) {
        val radius = scaledRadius.toFloat()
        canvas.drawCircle(radius + offsetX, radius + offsetY,
                radius - deltaRadius, paint)
    }

    val clipPath: Path
        get() {
            path.reset()
            val r = scaledRadius.toFloat()
            path.addCircle(r + offsetX, r + offsetY, r, Path.Direction.CW)
            return path
        }

    // It is the callers responsibility to save and restore the canvas layers.
    fun clipCanvasHardware(canvas: Canvas) {
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL
        paint.xfermode = clipPorterDuffXfermode
        val radius = scaledRadius.toFloat()
        shaderMatrix.setScale(radius, radius)
        shaderMatrix.postTranslate(radius + offsetX, radius + offsetY)
        clipShader.setLocalMatrix(shaderMatrix)
        paint.shader = clipShader
        canvas.drawPaint(paint)
        paint.xfermode = null
        paint.shader = null
    }

    private fun delegateDrawing(delegate: CellLayout?, cellX: Int, cellY: Int) {
        if (drawingDelegate != delegate) {
            delegate?.addFolderBackground(this)
        }
        drawingDelegate = delegate
        delegateCellX = cellX
        delegateCellY = cellY
        invalidate()
    }

    private fun clearDrawingDelegate() {
        drawingDelegate?.removeFolderBackground(this)
        drawingDelegate = null
        isClipping = true
        invalidate()
    }

    fun drawingDelegated() = drawingDelegate != null

    private fun animateScale(finalScale: Float, finalMultiplier: Float,
                             onStart: Runnable?, onEnd: Runnable?) {
        val scale0 = scale
        val bgMultiplier0 = colorMultiplier
        scaleAnimator?.cancel()
        scaleAnimator = LauncherAnimUtils.ofFloat(0f, 1.0f).apply {
            addUpdateListener { animation ->
                val prog = animation.animatedFraction
                scale = prog * finalScale + (1 - prog) * scale0
                colorMultiplier = prog * finalMultiplier + (1 - prog) * bgMultiplier0
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    onStart?.run()
                }

                override fun onAnimationEnd(animation: Animator) {
                    onEnd?.run()
                    scaleAnimator = null
                }
            })
            duration = CONSUMPTION_ANIMATION_DURATION.toLong()
            start()
        }
    }

    fun animateToAccept(cl: CellLayout?, cellX: Int, cellY: Int) {
        val onStart = Runnable { delegateDrawing(cl, cellX, cellY) }
        animateScale(ACCEPT_SCALE_FACTOR, ACCEPT_COLOR_MULTIPLIER, onStart, null)
    }

    fun animateToRest() {
        // This can be called multiple times -- we need to make sure the drawing delegate
        // is saved and restored at the beginning of the animation, since cancelling the
        // existing animation can clear the delgate.
        val cl = drawingDelegate
        val cellX = delegateCellX
        val cellY = delegateCellY
        val onStart = Runnable { delegateDrawing(cl, cellX, cellY) }
        val onEnd = Runnable { clearDrawingDelegate() }
        animateScale(1f, 1f, onStart, onEnd)
    }

    val backgroundAlpha: Int
        get() = min(MAX_BG_OPACITY.toFloat(), BG_OPACITY * colorMultiplier).toInt()

    companion object {
        private const val CONSUMPTION_ANIMATION_DURATION = 100

        // Drawing / animation configurations
        private const val ACCEPT_SCALE_FACTOR = 1.20f
        private const val ACCEPT_COLOR_MULTIPLIER = 1.5f

        // Expressed on a scale from 0 to 255.
        private const val BG_OPACITY = 160
        private const val MAX_BG_OPACITY = 225
        private const val SHADOW_OPACITY = 40
        private val STROKE_ALPHA: Property<PreviewBackground, Int> = object : Property<PreviewBackground, Int>(Int::class.java, "strokeAlpha") {
            override fun get(previewBackground: PreviewBackground): Int {
                return previewBackground.strokeAlpha
            }

            override fun set(previewBackground: PreviewBackground, alpha: Int) {
                previewBackground.strokeAlpha = alpha
                previewBackground.invalidate()
            }
        }
        private val SHADOW_ALPHA: Property<PreviewBackground, Int> = object : Property<PreviewBackground, Int>(Int::class.java, "shadowAlpha") {
            override fun get(previewBackground: PreviewBackground): Int {
                return previewBackground.shadowAlpha
            }

            override fun set(previewBackground: PreviewBackground, alpha: Int) {
                previewBackground.shadowAlpha = alpha
                previewBackground.invalidate()
            }
        }
    }
}