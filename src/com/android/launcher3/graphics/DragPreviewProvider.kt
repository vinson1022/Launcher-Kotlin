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
import android.graphics.drawable.Drawable
import android.os.Handler
import android.view.View
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.util.backgroundLooper
import com.android.launcher3.widget.LauncherAppWidgetHostView
import java.nio.ByteBuffer
import kotlin.experimental.and
import kotlin.math.roundToInt

/**
 * A utility class to generate preview bitmap for dragging.
 */
open class DragPreviewProvider
@JvmOverloads
constructor(
        protected val view: View,
        context: Context = view.context
) {
    private val tempRect = Rect()

    @JvmField
    protected val blurSizeOutline = context.resources.getDimensionPixelSize(R.dimen.blur_size_medium_outline)
    // The padding added to the drag view during the preview generation.
    @JvmField
    val previewPadding = if (view is BubbleTextView) {
        val d = view.icon
        val bounds = getDrawableBounds(d)
        blurSizeOutline - bounds.left - bounds.top
    } else {
        blurSizeOutline
    }
    private var outlineGeneratorCallback: OutlineGeneratorCallback? = null
    @JvmField
    var generatedDragOutline: Bitmap? = null

    /**
     * Draws the [.mView] into the given {@param destCanvas}.
     */
    private fun drawDragView(destCanvas: Canvas, scale: Float) {
        destCanvas.save()
        destCanvas.scale(scale, scale)
        if (view is BubbleTextView) {
            val d = view.icon
            val bounds = getDrawableBounds(d)
            destCanvas.translate(blurSizeOutline / 2 - bounds.left.toFloat(),
                    blurSizeOutline / 2 - bounds.top.toFloat())
            d.draw(destCanvas)
        } else {
            val clipRect = tempRect
            view.getDrawingRect(clipRect)
            var textVisible = false
            if (view is FolderIcon) {
                // For FolderIcons the text can bleed into the icon area, and so we need to
                // hide the text completely (which can't be achieved by clipping).
                if (view.textVisible) {
                    view.textVisible = false
                    textVisible = true
                }
            }
            destCanvas.translate(-view.scrollX + blurSizeOutline / 2.toFloat(),
                    -view.scrollY + blurSizeOutline / 2.toFloat())
            destCanvas.clipRect(clipRect)
            view.draw(destCanvas)

            // Restore text visibility of FolderIcon if necessary
            if (textVisible) {
                (view as FolderIcon).textVisible = true
            }
        }
        destCanvas.restore()
    }

    /**
     * Returns a new bitmap to show when the [.mView] is being dragged around.
     * Responsibility for the bitmap is transferred to the caller.
     */
    open fun createDragBitmap(): Bitmap? {
        var width = view.width
        var height = view.height
        if (view is BubbleTextView) {
            val d = view.icon
            val bounds = getDrawableBounds(d)
            width = bounds.width()
            height = bounds.height()
        } else if (view is LauncherAppWidgetHostView) {
            val scale = view.scaleToFit
            width = (view.getWidth() * scale).toInt()
            height = (view.getHeight() * scale).toInt()

            // Use software renderer for widgets as we know that they already work
            return BitmapRenderer.createSoftwareBitmap(width + blurSizeOutline,
                    height + blurSizeOutline) { c: Canvas -> drawDragView(c, scale) }
        }
        return BitmapRenderer.createHardwareBitmap(width + blurSizeOutline,
                height + blurSizeOutline) { c: Canvas -> drawDragView(c, 1f) }
    }

    fun generateDragOutline(preview: Bitmap) {
        if (FeatureFlags.IS_DOGFOOD_BUILD && outlineGeneratorCallback != null) {
            throw RuntimeException("Drag outline generated twice")
        }
        outlineGeneratorCallback = OutlineGeneratorCallback(preview)
        Handler(backgroundLooper).post(outlineGeneratorCallback)
    }

    open fun getScaleAndPosition(preview: Bitmap, outPos: IntArray): Float {
        var scale = Launcher.getLauncher(view.context)
                .dragLayer.getLocationInDragLayer(view, outPos)
        if (view is LauncherAppWidgetHostView) {
            // App widgets are technically scaled, but are drawn at their expected size -- so the
            // app widget scale should not affect the scale of the preview.
            scale /= view.scaleToFit
        }
        outPos[0] = (outPos[0] -
                (preview.width - scale * view.width * view.scaleX) / 2).roundToInt()
        outPos[1] = (outPos[1] - (1 - scale) * preview.height / 2 - previewPadding / 2).roundToInt()
        return scale
    }

    protected open fun convertPreviewToAlphaBitmap(preview: Bitmap)
            = preview.copy(Bitmap.Config.ALPHA_8, true)

    private inner class OutlineGeneratorCallback
    internal constructor(
            private val previewSnapshot: Bitmap
    ) : Runnable {
        private val context = view.context
        override fun run() {
            val preview = convertPreviewToAlphaBitmap(previewSnapshot)

            // We start by removing most of the alpha channel so as to ignore shadows, and
            // other types of partial transparency when defining the shape of the object
            val pixels = ByteArray(preview.width * preview.height)
            val buffer = ByteBuffer.wrap(pixels)
            buffer.rewind()
            preview.copyPixelsToBuffer(buffer)
            for (i in pixels.indices) {
                if (pixels[i] and 0xFF.toByte() < 188) {
                    pixels[i] = 0
                }
            }
            buffer.rewind()
            preview.copyPixelsFromBuffer(buffer)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val canvas = Canvas()

            // calculate the outer blur first
            paint.maskFilter = BlurMaskFilter(blurSizeOutline.toFloat(), BlurMaskFilter.Blur.OUTER)
            val outerBlurOffset = IntArray(2)
            val thickOuterBlur = preview.extractAlpha(paint, outerBlurOffset)
            paint.maskFilter = BlurMaskFilter(
                    context.resources.getDimension(R.dimen.blur_size_thin_outline),
                    BlurMaskFilter.Blur.OUTER)
            val brightOutlineOffset = IntArray(2)
            val brightOutline = preview.extractAlpha(paint, brightOutlineOffset)

            // calculate the inner blur
            canvas.setBitmap(preview)
            canvas.drawColor(-0x1000000, PorterDuff.Mode.SRC_OUT)
            paint.maskFilter = BlurMaskFilter(blurSizeOutline.toFloat(), BlurMaskFilter.Blur.NORMAL)
            val thickInnerBlurOffset = IntArray(2)
            val thickInnerBlur = preview.extractAlpha(paint, thickInnerBlurOffset)

            // mask out the inner blur
            paint.maskFilter = null
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            canvas.setBitmap(thickInnerBlur)
            canvas.drawBitmap(preview, -thickInnerBlurOffset[0].toFloat(),
                    -thickInnerBlurOffset[1].toFloat(), paint)
            canvas.drawRect(0f, 0f, (-thickInnerBlurOffset[0]).toFloat(), thickInnerBlur.height.toFloat(), paint)
            canvas.drawRect(0f, 0f, thickInnerBlur.width.toFloat(), (-thickInnerBlurOffset[1]).toFloat(), paint)

            // draw the inner and outer blur
            paint.xfermode = null
            canvas.setBitmap(preview)
            canvas.drawColor(0, PorterDuff.Mode.CLEAR)
            canvas.drawBitmap(thickInnerBlur, thickInnerBlurOffset[0].toFloat(), thickInnerBlurOffset[1].toFloat(),
                    paint)
            canvas.drawBitmap(thickOuterBlur, outerBlurOffset[0].toFloat(), outerBlurOffset[1].toFloat(), paint)

            // draw the bright outline
            canvas.drawBitmap(brightOutline, brightOutlineOffset[0].toFloat(), brightOutlineOffset[1].toFloat(), paint)

            // cleanup
            canvas.setBitmap(null)
            brightOutline.recycle()
            thickOuterBlur.recycle()
            thickInnerBlur.recycle()
            generatedDragOutline = preview
        }

    }

    protected fun getDrawableBounds(d: Drawable) = Rect().apply {
        d.copyBounds(this)
        if (width() == 0 || height() == 0) {
            this[0, 0, d.intrinsicWidth] = d.intrinsicHeight
        } else {
            offsetTo(0, 0)
        }
    }
}