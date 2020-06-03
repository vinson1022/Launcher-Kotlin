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
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Paint.FontMetricsInt
import android.text.style.DynamicDrawableSpan

/**
 * [DynamicDrawableSpan] which draws a drawable tinted with the current paint color.
 */
class TintedDrawableSpan(context: Context, resourceId: Int) : DynamicDrawableSpan(ALIGN_BOTTOM) {
    private val drawable = context.getDrawable(resourceId)!!.apply {
        setTint(0)
    }
    private var oldTint = 0

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: FontMetricsInt?): Int {
        val fm = fm ?: paint.fontMetricsInt
        val iconSize = fm.bottom - fm.top
        drawable.setBounds(0, 0, iconSize, iconSize)
        return super.getSize(paint, text, start, end, fm)
    }

    override fun draw(canvas: Canvas, text: CharSequence,
                      start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint) {
        val color = paint.color
        if (oldTint != color) {
            oldTint = color
            drawable.setTint(oldTint)
        }
        super.draw(canvas, text, start, end, x, top, y, bottom, paint)
    }

    override fun getDrawable() = drawable
}