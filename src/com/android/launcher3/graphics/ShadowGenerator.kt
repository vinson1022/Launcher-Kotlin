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
package com.android.launcher3.graphics

import android.content.Context
import android.graphics.*
import android.graphics.BlurMaskFilter.Blur
import android.support.v4.graphics.ColorUtils
import com.android.launcher3.LauncherAppState.Companion.getIDP
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Utility class to add shadows to bitmaps.
 */
class ShadowGenerator(context: Context) {
    private val iconSize = getIDP(context).iconBitmapSize
    private val blurPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val defaultBlurMaskFilter = BlurMaskFilter(iconSize * BLUR_FACTOR, Blur.NORMAL)

    @Synchronized
    fun recreateIcon(icon: Bitmap, out: Canvas) {
        recreateIcon(icon, defaultBlurMaskFilter, AMBIENT_SHADOW_ALPHA, KEY_SHADOW_ALPHA, out)
    }

    @Synchronized
    fun recreateIcon(icon: Bitmap, blurMaskFilter: BlurMaskFilter?,
                     ambientAlpha: Int, keyAlpha: Int, out: Canvas) {
        val offset = IntArray(2)
        blurPaint.maskFilter = blurMaskFilter
        val shadow = icon.extractAlpha(blurPaint, offset)

        // Draw ambient shadow
        drawPaint.alpha = ambientAlpha
        out.drawBitmap(shadow, offset[0].toFloat(), offset[1].toFloat(), drawPaint)

        // Draw key shadow
        drawPaint.alpha = keyAlpha
        out.drawBitmap(shadow, offset[0].toFloat(), offset[1] + KEY_SHADOW_DISTANCE * iconSize, drawPaint)

        // Draw the icon
        drawPaint.alpha = 255
        out.drawBitmap(icon, 0f, 0f, drawPaint)
    }

    class Builder(val color: Int) {
        @JvmField
        val bounds = RectF()
        private var ambientShadowAlpha = AMBIENT_SHADOW_ALPHA
        @JvmField
        var shadowBlur = 0f
        @JvmField
        var keyShadowDistance = 0f
        private var keyShadowAlpha = KEY_SHADOW_ALPHA
        @JvmField
        var radius = 0f
        fun setupBlurForSize(height: Int): Builder {
            shadowBlur = height * 1f / 32
            keyShadowDistance = height * 1f / 16
            return this
        }

        fun createPill(width: Int, height: Int): Bitmap {
            radius = height / 2f
            val centerX = (width / 2 + shadowBlur).roundToInt()
            val centerY = (radius + shadowBlur + keyShadowDistance).roundToInt()
            val center = max(centerX, centerY)
            bounds[0f, 0f, width.toFloat()] = height.toFloat()
            bounds.offsetTo(center - width / 2.toFloat(), center - height / 2.toFloat())
            val size = center * 2
            val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawShadow(Canvas(result))
            return result
        }

        fun drawShadow(c: Canvas) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            p.color = color

            // Key shadow
            p.setShadowLayer(shadowBlur, 0f, keyShadowDistance,
                    ColorUtils.setAlphaComponent(Color.BLACK, keyShadowAlpha))
            c.drawRoundRect(bounds, radius, radius, p)

            // Ambient shadow
            p.setShadowLayer(shadowBlur, 0f, 0f,
                    ColorUtils.setAlphaComponent(Color.BLACK, ambientShadowAlpha))
            c.drawRoundRect(bounds, radius, radius, p)
            if (Color.alpha(color) < 255) {
                // Clear any content inside the pill-rect for translucent fill.
                p.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                p.clearShadowLayer()
                p.color = Color.BLACK
                c.drawRoundRect(bounds, radius, radius, p)
                p.xfermode = null
                p.color = color
                c.drawRoundRect(bounds, radius, radius, p)
            }
        }

    }

    companion object {
        // Percent of actual icon size
        private const val HALF_DISTANCE = 0.5f
        const val BLUR_FACTOR = 0.5f / 48

        // Percent of actual icon size
        const val KEY_SHADOW_DISTANCE = 1f / 48
        private const val KEY_SHADOW_ALPHA = 61
        private const val AMBIENT_SHADOW_ALPHA = 30

        /**
         * Returns the minimum amount by which an icon with {@param bounds} should be scaled
         * so that the shadows do not get clipped.
         */
        fun getScaleForBounds(bounds: RectF): Float {
            var scale = 1f

            // For top, left & right, we need same space.
            val minSide = min(min(bounds.left, bounds.right), bounds.top)
            if (minSide < BLUR_FACTOR) {
                scale = (HALF_DISTANCE - BLUR_FACTOR) / (HALF_DISTANCE - minSide)
            }
            val bottomSpace = BLUR_FACTOR + KEY_SHADOW_DISTANCE
            if (bounds.bottom < bottomSpace) {
                scale = min(scale, (HALF_DISTANCE - bottomSpace) / (HALF_DISTANCE - bounds.bottom))
            }
            return scale
        }
    }
}