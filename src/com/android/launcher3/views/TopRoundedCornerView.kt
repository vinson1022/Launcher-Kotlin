/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import com.android.launcher3.R
import com.android.launcher3.util.getAttrColor

/**
 * View with top rounded corners.
 */
class TopRoundedCornerView @JvmOverloads constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int = 0) : SpringRelativeLayout(context, attrs, defStyleAttr) {
    private val rect = RectF()
    private val clipPath = Path()
    private val radiusList = resources.getDimensionPixelSize(R.dimen.bg_round_rect_radius).let {
        floatArrayOf(it.toFloat(), it.toFloat(), it.toFloat(), it.toFloat(), 0f, 0f, 0f, 0f)
    }
    private val navBarScrimPaint = Paint().also { it.color = getAttrColor(context!!, R.attr.allAppsNavBarScrimColor) }
    private var navBarScrimHeight = 0

    fun setNavBarScrimHeight(height: Int) {
        if (navBarScrimHeight != height) {
            navBarScrimHeight = height
            invalidate()
        }
    }

    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.clipPath(clipPath)
        super.draw(canvas)
        canvas.restore()
        if (navBarScrimHeight > 0) {
            canvas.drawRect(0f, height - navBarScrimHeight.toFloat(), width.toFloat(), height.toFloat(),
                    navBarScrimPaint)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        rect[0f, 0f, measuredWidth.toFloat()] = measuredHeight.toFloat()
        clipPath.reset()
        clipPath.addRoundRect(rect, radiusList, Path.Direction.CW)
    }
}