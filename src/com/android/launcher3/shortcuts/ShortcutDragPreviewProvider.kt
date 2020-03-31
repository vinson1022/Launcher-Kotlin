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
package com.android.launcher3.shortcuts

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.view.View
import com.android.launcher3.Launcher
import com.android.launcher3.Utilities
import com.android.launcher3.graphics.DragPreviewProvider
import kotlin.math.roundToInt

/**
 * Extension of [DragPreviewProvider] which generates bitmaps scaled to the default icon size.
 */
class ShortcutDragPreviewProvider(icon: View, private val positionShift: Point) : DragPreviewProvider(icon) {

    override fun createDragBitmap(): Bitmap? {
        val d = view.background
        val bounds = getDrawableBounds(d)
        val size = Launcher.getLauncher(view.context).deviceProfile.iconSizePx
        val b = Bitmap.createBitmap(
                size + blurSizeOutline,
                size + blurSizeOutline,
                Bitmap.Config.ARGB_8888)
        val canvas = Canvas(b)
        canvas.translate(blurSizeOutline / 2f, blurSizeOutline / 2f)
        canvas.scale(size.toFloat() / bounds.width(), size.toFloat() / bounds.height(), 0f, 0f)
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        d.draw(canvas)
        return b
    }

    override fun getScaleAndPosition(preview: Bitmap, outPos: IntArray): Float {
        val launcher = Launcher.getLauncher(view.context)
        val iconSize = getDrawableBounds(view.background).width()
        val scale = launcher.dragLayer.getLocationInDragLayer(view, outPos)
        var iconLeft = view.paddingStart
        if (Utilities.isRtl(view.resources)) {
            iconLeft = view.width - iconSize - iconLeft
        }
        outPos[0] += (scale * iconLeft + (scale * iconSize - preview.width) / 2 +
                positionShift.x).roundToInt()
        outPos[1] += ((scale * view.height - preview.height) / 2
                + positionShift.y).roundToInt()
        val size = launcher.deviceProfile.iconSizePx.toFloat()
        return scale * iconSize / size
    }

}