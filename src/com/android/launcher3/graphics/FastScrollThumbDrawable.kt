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
import android.graphics.drawable.Drawable

class FastScrollThumbDrawable(private val paint: Paint, private val isRtl: Boolean) : Drawable() {

    private val path = Path()

    override fun getOutline(outline: Outline) {
        if (path.isConvex) {
            outline.setConvexPath(path)
        }
    }

    override fun onBoundsChange(bounds: Rect) {
        path.reset()
        val r = bounds.height() * 0.5f
        // The path represents a rotate tear-drop shape, with radius of one corner is 1/5th of the
        // other 3 corners.
        val diameter = 2 * r
        val r2 = r / 5
        path.addRoundRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.left + diameter,
                bounds.top + diameter, floatArrayOf(r, r, r, r, r2, r2, r, r),
                Path.Direction.CCW)
        sMatrix.setRotate(-45f, bounds.left + r, bounds.top + r)
        if (isRtl) {
            sMatrix.postTranslate(bounds.width().toFloat(), 0f)
            sMatrix.postScale(-1f, 1f, bounds.width().toFloat(), 0f)
        }
        path.transform(sMatrix)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(i: Int) {
        // Not supported
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        // Not supported
    }

    override fun getOpacity() = PixelFormat.TRANSLUCENT

    companion object {
        private val sMatrix = Matrix()
    }

}