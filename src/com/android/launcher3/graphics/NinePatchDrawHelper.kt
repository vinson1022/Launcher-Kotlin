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

import android.graphics.*

/**
 * Utility class which draws a bitmap by dissecting it into 3 segments and stretching
 * the middle segment.
 */
class NinePatchDrawHelper {
    private val src = Rect()
    private val dst = RectF()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Draws the bitmap split into three parts horizontally, with the middle part having width
     * as [.EXTENSION_PX] in the center of the bitmap.
     */
    fun draw(bitmap: Bitmap, canvas: Canvas, left: Float, top: Float, right: Float) {
        val height = bitmap.height
        src.top = 0
        src.bottom = height
        dst.top = top
        dst.bottom = top + height
        draw3Patch(bitmap, canvas, left, right)
    }

    /**
     * Draws the bitmap split horizontally into 3 parts (same as [.draw]) and split
     * vertically into two parts, bottom part of size [.EXTENSION_PX] / 2 which is
     * stretched vertically.
     */
    fun drawVerticallyStretched(bitmap: Bitmap, canvas: Canvas, left: Float, top: Float,
                                right: Float, bottom: Float) {
        draw(bitmap, canvas, left, top, right)

        // Draw bottom stretched region.
        val height = bitmap.height
        src.top = height - EXTENSION_PX / 4
        src.bottom = height
        dst.top = top + height
        dst.bottom = bottom
        draw3Patch(bitmap, canvas, left, right)
    }

    private fun draw3Patch(bitmap: Bitmap, canvas: Canvas, left: Float, right: Float) {
        val width = bitmap.width
        val halfWidth = width / 2

        // Draw left edge
        drawRegion(bitmap, canvas, 0, halfWidth, left, left + halfWidth)

        // Draw right edge
        drawRegion(bitmap, canvas, halfWidth, width, right - halfWidth, right)

        // Draw middle stretched region
        val halfExt = EXTENSION_PX / 4
        drawRegion(bitmap, canvas, halfWidth - halfExt, halfWidth + halfExt,
                left + halfWidth, right - halfWidth)
    }

    private fun drawRegion(bitmap: Bitmap, c: Canvas,
                           srcLeft: Int, srcRight: Int, dstLeft: Float, dstRight: Float) {
        src.left = srcLeft
        src.right = srcRight
        dst.left = dstLeft
        dst.right = dstRight
        c.drawBitmap(bitmap, src, dst, paint)
    }

    companion object {
        // The extra width used for the bitmap. This portion of the bitmap is stretched to match the
        // width of the draw region. Randomly chosen, any value > 4 will be sufficient.
        private const val EXTENSION_PX = 20
    }
}