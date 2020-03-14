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
package com.android.launcher3.widget

import android.graphics.*
import android.view.View
import android.widget.RemoteViews
import com.android.launcher3.DragSource
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState.Companion.getInstance
import com.android.launcher3.PendingAddItemInfo
import com.android.launcher3.R
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.dragndrop.LivePreviewWidgetCell
import com.android.launcher3.graphics.DragPreviewProvider
import com.android.launcher3.graphics.LauncherIcons
import kotlin.math.min

/**
 * Extension of [DragPreviewProvider] with logic specific to pending widgets/shortcuts
 * dragged from the widget tray.
 */
class PendingItemDragHelper(view: View) : DragPreviewProvider(view) {
    private val addInfo: PendingAddItemInfo = view.tag as PendingAddItemInfo
    private var estimatedCellSize: IntArray? = null
    private var preview: RemoteViews? = null

    fun setPreview(preview: RemoteViews?) {
        this.preview = preview
    }

    /**
     * Starts the drag for the pending item associated with the view.
     *
     * @param previewBounds The bounds where the image was displayed,
     * [WidgetImageView.getBitmapBounds]
     * @param previewBitmapWidth The actual width of the bitmap displayed in the view.
     * @param previewViewWidth The width of [WidgetImageView] displaying the preview
     * @param screenPos Position of [WidgetImageView] on the screen
     */
    fun startDrag(previewBounds: Rect, previewBitmapWidth: Int, previewViewWidth: Int,
                  screenPos: Point, source: DragSource?, options: DragOptions?) {
        val launcher = Launcher.getLauncher(view.context)
        val app = getInstance(launcher)
        var preview: Bitmap? = null
        val scale: Float
        val dragOffset: Point?
        val dragRegion: Rect?
        estimatedCellSize = launcher.workspace.estimateItemSize(addInfo)
        if (addInfo is PendingAddWidgetInfo) {
            val createWidgetInfo = addInfo
            val maxWidth = min((previewBitmapWidth * MAX_WIDGET_SCALE).toInt(), estimatedCellSize!![0])
            val previewSizeBeforeScale = IntArray(1)
            if (this.preview != null) {
                preview = LivePreviewWidgetCell.generateFromRemoteViews(launcher, this.preview,
                        createWidgetInfo.info, maxWidth, previewSizeBeforeScale)
            }
            if (preview == null) {
                preview = app.widgetCache.generateWidgetPreview(
                        launcher, createWidgetInfo.info, maxWidth, null, previewSizeBeforeScale)
            }
            if (previewSizeBeforeScale[0] < previewBitmapWidth) {
                // The icon has extra padding around it.
                var padding = (previewBitmapWidth - previewSizeBeforeScale[0]) / 2
                if (previewBitmapWidth > previewViewWidth) {
                    padding = padding * previewViewWidth / previewBitmapWidth
                }
                previewBounds.left += padding
                previewBounds.right -= padding
            }
            scale = previewBounds.width() / preview!!.width.toFloat()
            launcher.dragController.addDragListener(WidgetHostViewLoader(launcher, view))
            dragOffset = null
            dragRegion = null
        } else {
            val createShortcutInfo = addInfo as PendingAddShortcutInfo
            val icon = createShortcutInfo.activityInfo.getFullResIcon(app.iconCache)
            val li = LauncherIcons.obtain(launcher)
            preview = li.createScaledBitmapWithoutShadow(icon, 0)
            li.recycle()
            scale = launcher.deviceProfile.iconSizePx.toFloat() / preview.width
            dragOffset = Point(previewPadding / 2, previewPadding / 2)

            // Create a preview same as the workspace cell size and draw the icon at the
            // appropriate position.
            val dp = launcher.deviceProfile
            val iconSize = dp.iconSizePx
            val padding = launcher.resources
                    .getDimensionPixelSize(R.dimen.widget_preview_shortcut_padding)
            previewBounds.left += padding
            previewBounds.top += padding
            dragRegion = Rect()
            dragRegion.left = (estimatedCellSize!![0] - iconSize) / 2
            dragRegion.right = dragRegion.left + iconSize
            dragRegion.top = (estimatedCellSize!![1]
                    - iconSize - dp.iconTextSizePx - dp.iconDrawablePaddingPx) / 2
            dragRegion.bottom = dragRegion.top + iconSize
        }

        // Since we are not going through the workspace for starting the drag, set drag related
        // information on the workspace before starting the drag.
        launcher.workspace.prepareDragWithProvider(this)
        val dragLayerX = (screenPos.x + previewBounds.left
                + ((scale * preview!!.width - preview.width) / 2).toInt())
        val dragLayerY = (screenPos.y + previewBounds.top
                + ((scale * preview.height - preview.height) / 2).toInt())

        // Start the drag
        launcher.dragController.startDrag(preview, dragLayerX, dragLayerY, source, addInfo,
                dragOffset, dragRegion, scale, scale, options)
    }

    override fun convertPreviewToAlphaBitmap(preview: Bitmap): Bitmap {
        if (addInfo is PendingAddShortcutInfo || estimatedCellSize == null) {
            return super.convertPreviewToAlphaBitmap(preview)
        }
        val w = estimatedCellSize!![0]
        val h = estimatedCellSize!![1]
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val src = Rect(0, 0, preview.width, preview.height)
        val scaleFactor = min((w - blurSizeOutline) / preview.width.toFloat(),
                (h - blurSizeOutline) / preview.height.toFloat())
        val scaledWidth = (scaleFactor * preview.width).toInt()
        val scaledHeight = (scaleFactor * preview.height).toInt()
        val dst = Rect(0, 0, scaledWidth, scaledHeight)

        // center the image
        dst.offset((w - scaledWidth) / 2, (h - scaledHeight) / 2)
        Canvas(b).drawBitmap(preview, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        return b
    }

    companion object {
        private const val MAX_WIDGET_SCALE = 1.25f
    }
}