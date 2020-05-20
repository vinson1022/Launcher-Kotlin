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

import android.graphics.Bitmap
import com.android.launcher3.ItemInfoWithIcon
import com.android.launcher3.graphics.ColorExtractor.findDominantColorByHue

open class BitmapInfo {
    @JvmField
    var icon: Bitmap? = null
    @JvmField
    var color = 0
    fun applyTo(info: ItemInfoWithIcon) {
        info.iconBitmap = icon
        info.iconColor = color
    }

    fun applyTo(info: BitmapInfo) {
        info.icon = icon
        info.color = color
    }

    companion object {
        @JvmStatic
        fun fromBitmap(bitmap: Bitmap): BitmapInfo {
            val info = BitmapInfo()
            info.icon = bitmap
            info.color = findDominantColorByHue(bitmap)
            return info
        }
    }
}