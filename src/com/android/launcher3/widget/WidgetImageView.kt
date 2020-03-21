/*
 * Copyright (C) 2011 The Android Open Source Project
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
package com.android.launcher3.widget

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import com.android.launcher3.R
import com.android.launcher3.Utilities

/**
 * View that draws a bitmap horizontally centered. If the image width is greater than the view
 * width, the image is scaled down appropriately.
 */
class WidgetImageView @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val dstRectF = RectF()
    private val badgeMargin = context.resources
            .getDimensionPixelSize(R.dimen.profile_badge_margin)
    var bitmap: Bitmap? = null
        private set
    private var badge: Drawable? = null

    fun setBitmap(bitmap: Bitmap?, badge: Drawable?) {
        this.bitmap = bitmap
        this.badge = badge
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        bitmap?.apply {
            updateDstRectF()
            canvas.drawBitmap(this, null, dstRectF, paint)

            // Only draw the badge if a preview was drawn.
            badge?.draw(canvas)
        }
    }

    /**
     * Prevents the inefficient alpha view rendering.
     */
    override fun hasOverlappingRendering() = false

    private fun updateDstRectF() {
        bitmap?.also { it ->
            val myWidth = width.toFloat()
            val myHeight = height.toFloat()
            val bitmapWidth = it.width.toFloat()
            val scale = if (bitmapWidth > myWidth) myWidth / bitmapWidth else 1f
            val scaledWidth = bitmapWidth * scale
            val scaledHeight = it.height * scale
            dstRectF.left = (myWidth - scaledWidth) / 2
            dstRectF.right = (myWidth + scaledWidth) / 2
            if (scaledHeight > myHeight) {
                dstRectF.top = 0f
                dstRectF.bottom = scaledHeight
            } else {
                dstRectF.top = (myHeight - scaledHeight) / 2
                dstRectF.bottom = (myHeight + scaledHeight) / 2
            }
            badge?.apply {
                val left = Utilities.boundToRange(
                        (dstRectF.right + badgeMargin - bounds.width()).toInt(),
                        badgeMargin, width - bounds.width())
                val top = Utilities.boundToRange(
                        (dstRectF.bottom + badgeMargin - bounds.height()).toInt(),
                        badgeMargin, height - bounds.height())
                setBounds(left, top, bounds.width() + left, bounds.height() + top)
            }
        }
    }

    /**
     * @return the bounds where the image was drawn.
     */
    val bitmapBounds: Rect
        get() {
            updateDstRectF()
            val rect = Rect()
            dstRectF.round(rect)
            return rect
        }

}