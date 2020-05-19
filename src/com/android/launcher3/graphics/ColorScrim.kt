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
package com.android.launcher3.graphics

import android.graphics.Canvas
import android.graphics.Color
import android.support.v4.graphics.ColorUtils
import android.view.View
import android.view.animation.Interpolator
import com.android.launcher3.R
import com.android.launcher3.anim.Interpolators
import com.android.launcher3.uioverrides.WallpaperColorInfo
import kotlin.math.roundToInt

/**
 * Simple scrim which draws a color
 */
class ColorScrim(
        view: View,
        private val color: Int,
        private val interpolator: Interpolator
) : ViewScrim<View>(view) {

    private var currentColor = 0

    override fun onProgressChanged() {
        currentColor = ColorUtils.setAlphaComponent(color,
                (interpolator.getInterpolation(mProgress) * Color.alpha(color)).roundToInt())
    }

    override fun draw(canvas: Canvas, width: Int, height: Int) {
        if (mProgress > 0) {
            canvas.drawColor(currentColor)
        }
    }

    companion object {
        fun createExtractedColorScrim(view: View): ColorScrim {
            val colors = WallpaperColorInfo.getInstance(view.context)
            val alpha = view.resources.getInteger(R.integer.extracted_color_gradient_alpha)
            val scrim = ColorScrim(view, ColorUtils.setAlphaComponent(
                    colors.secondaryColor, alpha), Interpolators.LINEAR)
            scrim.attach()
            return scrim
        }
    }
}