/*
 * Copyright (C) 2015 The Android Open Source Project
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
import android.graphics.Bitmap
import android.os.CancellationSignal
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.ViewGroup
import android.widget.LinearLayout
import com.android.launcher3.*
import com.android.launcher3.graphics.DrawableFactory
import com.android.launcher3.model.WidgetItem
import kotlinx.android.synthetic.main.widget_cell_content.view.*

/**
 * Represents the individual cell of the widget inside the widget tray. The preview is drawn
 * horizontally centered, and scaled down if needed.
 *
 * This view does not support padding. Since the image is scaled down to fit the view, padding will
 * further decrease the scaling factor. Drag-n-drop uses the view bounds for showing a smooth
 * transition from the view to drag view, so when adding padding support, DnD would need to
 * consider the appropriate scaling factor.
 */
open class WidgetCell @JvmOverloads constructor(
        context: Context?,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle), OnLayoutChangeListener {

    @JvmField
    protected var presetPreviewSize = 0
    private var cellSize = 0
    @JvmField
    protected var item: WidgetItem? = null
    private var widgetPreviewLoader: WidgetPreviewLoader? = null
    private val stylusEventHelper = StylusEventHelper(SimpleOnStylusPressListener(this), this)

    @JvmField
    protected var activeRequest: CancellationSignal? = null
    private var animatePreview = true
    private var applyBitmapDeferred = false
    private var deferredBitmap: Bitmap? = null
    @JvmField
    protected val activity = BaseActivity.fromContext(context)!!

    init {
        setContainerWidth()
        setWillNotDraw(false)
        clipToPadding = false
        accessibilityDelegate = activity.accessibilityDelegate
    }

    private fun setContainerWidth() {
        val profile = activity.deviceProfile
        cellSize = (profile.cellWidthPx * WIDTH_SCALE).toInt()
        presetPreviewSize = (cellSize * PREVIEW_SCALE).toInt()
    }

    /**
     * Called to clear the view and free attached resources. (e.g., [Bitmap]
     */
    fun clear() {
        if (DEBUG) {
            Log.d(TAG, "reset called on:" + widgetName.text)
        }
        widgetPreview.animate().cancel()
        widgetPreview.setBitmap(null, null)
        widgetName.text = null
        widgetDims.text = null
        if (activeRequest != null) {
            activeRequest!!.cancel()
            activeRequest = null
        }
    }

    fun applyFromCellItem(item: WidgetItem, loader: WidgetPreviewLoader?) {
        this.item = item
        widgetName.text = item.label
        widgetDims.text = context.getString(R.string.widget_dims_format, item.spanX, item.spanY)
        widgetDims.contentDescription = context.getString(
                R.string.widget_accessible_dims_format, item.spanX, item.spanY)
        widgetPreviewLoader = loader
        tag = if (item.activityInfo != null) {
            PendingAddShortcutInfo(item.activityInfo)
        } else {
            PendingAddWidgetInfo(item.widgetInfo)
        }
    }

    /**
     * Sets if applying bitmap preview should be deferred. The UI will still load the bitmap, but
     * will not cause invalidate, so that when deferring is disabled later, all the bitmaps are
     * ready.
     * This prevents invalidates while the animation is running.
     */
    fun setApplyBitmapDeferred(isDeferred: Boolean) {
        if (applyBitmapDeferred != isDeferred) {
            applyBitmapDeferred = isDeferred
            if (!applyBitmapDeferred && deferredBitmap != null) {
                applyPreview(deferredBitmap)
                deferredBitmap = null
            }
        }
    }

    fun setAnimatePreview(shouldAnimate: Boolean) {
        animatePreview = shouldAnimate
    }

    fun applyPreview(bitmap: Bitmap?) {
        if (applyBitmapDeferred) {
            deferredBitmap = bitmap
            return
        }
        if (bitmap != null) {
            widgetPreview.setBitmap(bitmap,
                    DrawableFactory.get(context).getBadgeForUser(item!!.user, context))
            if (animatePreview) {
                widgetPreview.alpha = 0f
                val anim = widgetPreview.animate()
                anim.alpha(1.0f).duration = FADE_IN_DURATION_MS
            } else {
                widgetPreview.alpha = 1f
            }
        }
    }

    open fun ensurePreview() {
        if (activeRequest != null) return

        activeRequest = widgetPreviewLoader!!.getPreview(
                item, presetPreviewSize, presetPreviewSize, this)
    }

    override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int,
                                oldTop: Int, oldRight: Int, oldBottom: Int) {
        removeOnLayoutChangeListener(this)
        ensurePreview()
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.onTouchEvent(ev)
        return if (stylusEventHelper.onMotionEvent(ev)) true else handled
    }

    /**
     * Helper method to get the string info of the tag.
     */
    private val tagToString: String
        get() = if (tag is PendingAddWidgetInfo ||
                tag is PendingAddShortcutInfo) {
            tag.toString()
        } else ""

    override fun setLayoutParams(params: ViewGroup.LayoutParams) {
        params.height = cellSize
        params.width = params.height
        super.setLayoutParams(params)
    }

    override fun getAccessibilityClassName() = WidgetCell::class.java.name

    fun getWidgetPreview() = widgetPreview

    companion object {
        private const val TAG = "WidgetCell"
        private const val DEBUG = false
        private const val FADE_IN_DURATION_MS = 90L

        /** Widget cell width is calculated by multiplying this factor to grid cell width.  */
        private const val WIDTH_SCALE = 2.6f

        /** Widget preview width is calculated by multiplying this factor to the widget cell width.  */
        private const val PREVIEW_SCALE = 0.8f
    }
}