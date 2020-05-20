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

import android.app.Notification
import android.content.Context
import android.graphics.Color
import android.support.v4.graphics.ColorUtils
import android.util.Log
import com.android.launcher3.R
import com.android.launcher3.util.getColorAccent
import kotlin.math.max

/**
 * Contains colors based on the dominant color of an icon.
 */
object IconPalette {
    private const val DEBUG = false
    private const val TAG = "IconPalette"
    private const val MIN_PRELOAD_COLOR_SATURATION = 0.2f
    private const val MIN_PRELOAD_COLOR_LIGHTNESS = 0.6f

    /**
     * Returns a color suitable for the progress bar color of preload icon.
     */
    @JvmStatic
    fun getPreloadProgressColor(context: Context, dominantColor: Int): Int {
        var result = dominantColor

        // Make sure that the dominant color has enough saturation to be visible properly.
        val hsv = FloatArray(3)
        Color.colorToHSV(result, hsv)
        if (hsv[1] < MIN_PRELOAD_COLOR_SATURATION) {
            result = getColorAccent(context)
        } else {
            hsv[2] = max(MIN_PRELOAD_COLOR_LIGHTNESS, hsv[2])
            result = Color.HSVToColor(hsv)
        }
        return result
    }

    /**
     * Resolves a color such that it has enough contrast to be used as the
     * color of an icon or text on the given background color.
     *
     * @return a color of the same hue with enough contrast against the background.
     *
     * This was copied from com.android.internal.util.NotificationColorUtil.
     */
    fun resolveContrastColor(context: Context, color: Int, background: Int): Int {
        val resolvedColor = resolveColor(context, color)
        val contrastingColor = ensureTextContrast(resolvedColor, background)
        if (contrastingColor != resolvedColor) {
            if (DEBUG) {
                Log.w(TAG, String.format(
                        "Enhanced contrast of notification for %s " +
                                "%s (over background) by changing #%s to %s",
                        context.packageName,
                        contrastChange(resolvedColor, contrastingColor, background),
                        Integer.toHexString(resolvedColor), Integer.toHexString(contrastingColor)))
            }
        }
        return contrastingColor
    }

    /**
     * Resolves {@param color} to an actual color if it is [Notification.COLOR_DEFAULT]
     *
     * This was copied from com.android.internal.util.NotificationColorUtil.
     */
    private fun resolveColor(context: Context, color: Int): Int {
        return if (color == Notification.COLOR_DEFAULT) {
            context.getColor(R.color.notification_icon_default_color)
        } else color
    }

    /** For debugging. This was copied from com.android.internal.util.NotificationColorUtil.  */
    private fun contrastChange(colorOld: Int, colorNew: Int, bg: Int): String {
        return String.format("from %.2f:1 to %.2f:1",
                ColorUtils.calculateContrast(colorOld, bg),
                ColorUtils.calculateContrast(colorNew, bg))
    }

    /**
     * Finds a text color with sufficient contrast over bg that has the same hue as the original
     * color.
     *
     * This was copied from com.android.internal.util.NotificationColorUtil.
     */
    private fun ensureTextContrast(color: Int, bg: Int): Int {
        return findContrastColor(color, bg, 4.5)
    }

    /**
     * Finds a suitable color such that there's enough contrast.
     *
     * @param fg the color to start searching from.
     * @param bg the color to ensure contrast against.
     * @param minRatio the minimum contrast ratio required.
     * @return a color with the same hue as {@param color}, potentially darkened to meet the
     * contrast ratio.
     *
     * This was copied from com.android.internal.util.NotificationColorUtil.
     */
    private fun findContrastColor(fg: Int, bg: Int, minRatio: Double): Int {
        var fg = fg
        if (ColorUtils.calculateContrast(fg, bg) >= minRatio) {
            return fg
        }
        val lab = DoubleArray(3)
        ColorUtils.colorToLAB(bg, lab)
        val bgL = lab[0]
        ColorUtils.colorToLAB(fg, lab)
        val fgL = lab[0]
        val isBgDark = bgL < 50
        var low: Double = if (isBgDark) fgL else 0.toDouble()
        var high: Double = if (isBgDark) 100.toDouble() else fgL
        val a = lab[1]
        val b = lab[2]
        var i = 0
        while (i < 15 && high - low > 0.00001) {
            val l = (low + high) / 2
            fg = ColorUtils.LABToColor(l, a, b)
            if (ColorUtils.calculateContrast(fg, bg) > minRatio) {
                if (isBgDark) high = l else low = l
            } else {
                if (isBgDark) low = l else high = l
            }
            i++
        }
        return ColorUtils.LABToColor(low, a, b)
    }

    @JvmStatic
    fun getMutedColor(color: Int, whiteScrimAlpha: Float): Int {
        val whiteScrim = ColorUtils.setAlphaComponent(Color.WHITE, (255 * whiteScrimAlpha).toInt())
        return ColorUtils.compositeColors(whiteScrim, color)
    }
}