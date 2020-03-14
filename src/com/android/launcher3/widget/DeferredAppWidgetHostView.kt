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
package com.android.launcher3.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.widget.RemoteViews
import com.android.launcher3.R

/**
 * A widget host views created while the host has not bind to the system service.
 */
class DeferredAppWidgetHostView(context: Context?) : LauncherAppWidgetHostView(context) {
    private val paint: TextPaint = TextPaint().apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                mLauncher.deviceProfile.fullScreenProfile.iconTextSizePx.toFloat(),
                resources.displayMetrics)
    }
    private var setupTextLayout: Layout? = null

    init {
        setWillNotDraw(false)
        setBackgroundResource(R.drawable.bg_deferred_app_widget)
    }

    override fun updateAppWidget(remoteViews: RemoteViews) {
        // Not allowed
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val info = appWidgetInfo
        if (info == null || info.label.isEmpty()) return

        // Use double padding so that there is extra space between background and text
        val availableWidth = measuredWidth - 2 * (paddingLeft + paddingRight)
        if (setupTextLayout?.checkRefreshIfNeed(info.label, availableWidth) == true) {
            setupTextLayout = getTextLayout(info.label, availableWidth)
        }
    }
    
    private fun Layout.checkRefreshIfNeed(label: String, width: Int) = text == label && width == width

    private fun getTextLayout(label: String, width: Int)
            = StaticLayout(label, paint, width, Layout.Alignment.ALIGN_CENTER, 1f, 0f, true)


    override fun onDraw(canvas: Canvas) {
        setupTextLayout?.apply {
            canvas.translate(paddingLeft * 2f, (this@DeferredAppWidgetHostView.height - this.height) / 2f)
            draw(canvas)
        }
    }
}