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
package com.android.launcher3.folder

import android.graphics.drawable.Drawable

/**
 * Manages the parameters used to draw a Folder preview item.
 */
class PreviewItemDrawingParams(
        @JvmField
        var transX: Float,
        @JvmField
        var transY: Float,
        @JvmField
        var scale: Float,
        @JvmField
        var overlayAlpha: Float
) {
    @JvmField
    var anim: FolderPreviewItemAnim? = null
    @JvmField
    var hidden = false
    @JvmField
    var drawable: Drawable? = null
    fun update(transX: Float, transY: Float, scale: Float) {
        // We ensure the update will not interfere with an animation on the layout params
        // If the final values differ, we cancel the animation.
        anim?.apply {
            if (finalTransX == transX || finalTransY == transY || finalScale == scale) return
            cancel()
        }
        this.transX = transX
        this.transY = transY
        this.scale = scale
    }
}