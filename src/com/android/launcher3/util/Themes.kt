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
@file:JvmName("Themes")

package com.android.launcher3.util

import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.drawable.Drawable

/**
 * Various utility methods associated with theming.
 */
fun getColorAccent(context: Context): Int {
    return getAttrColor(context, android.R.attr.colorAccent)
}

fun getAttrColor(context: Context, attr: Int): Int {
    val ta = context.obtainStyledAttributes(intArrayOf(attr))
    val colorAccent = ta.getColor(0, 0)
    ta.recycle()
    return colorAccent
}

fun getAttrBoolean(context: Context, attr: Int): Boolean {
    val ta = context.obtainStyledAttributes(intArrayOf(attr))
    val value = ta.getBoolean(0, false)
    ta.recycle()
    return value
}

fun getAttrDrawable(context: Context, attr: Int): Drawable? {
    val ta = context.obtainStyledAttributes(intArrayOf(attr))
    val value = ta.getDrawable(0)
    ta.recycle()
    return value
}

fun getAttrInteger(context: Context, attr: Int): Int {
    val ta = context.obtainStyledAttributes(intArrayOf(attr))
    val value = ta.getInteger(0, 0)
    ta.recycle()
    return value
}

/**
 * Returns the alpha corresponding to the theme attribute {@param attr}, in the range [0, 255].
 */
fun getAlpha(context: Context, attr: Int): Int {
    val ta = context.obtainStyledAttributes(intArrayOf(attr))
    val alpha = ta.getFloat(0, 0f)
    ta.recycle()
    return (255 * alpha + 0.5f).toInt()
}

/**
 * Scales a color matrix such that, when applied to color R G B A, it produces R' G' B' A' where
 * R' = r * R
 * G' = g * G
 * B' = b * B
 * A' = a * A
 *
 * The matrix will, for instance, turn white into r g b a, and black will remain black.
 *
 * @param color The color r g b a
 * @param target The ColorMatrix to scale
 */
fun setColorScaleOnMatrix(color: Int, target: ColorMatrix) {
    target.setScale(Color.red(color) / 255f, Color.green(color) / 255f,
            Color.blue(color) / 255f, Color.alpha(color) / 255f)
}

/**
 * Changes a color matrix such that, when applied to srcColor, it produces dstColor.
 *
 * Note that values on the last column of target ColorMatrix can be negative, and may result in
 * negative values when applied on a color. Such negative values will be automatically shifted
 * up to 0 by the framework.
 *
 * @param srcColor The color to start from
 * @param dstColor The color to create by applying target on srcColor
 * @param target The ColorMatrix to transform the color
 */
fun setColorChangeOnMatrix(srcColor: Int, dstColor: Int, target: ColorMatrix) {
    target.reset()
    target.array[4] = (Color.red(dstColor) - Color.red(srcColor)).toFloat()
    target.array[9] = (Color.green(dstColor) - Color.green(srcColor)).toFloat()
    target.array[14] = (Color.blue(dstColor) - Color.blue(srcColor)).toFloat()
    target.array[19] = (Color.alpha(dstColor) - Color.alpha(srcColor)).toFloat()
}