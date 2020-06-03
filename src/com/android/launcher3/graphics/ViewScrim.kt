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
import android.util.Property
import android.view.View
import com.android.launcher3.R

/**
 * A utility class that can be used to draw a scrim behind a view
 */
abstract class ViewScrim<T : View>(protected val view: T) {
    protected var _progress = 0f
    fun attach() {
        view.setTag(R.id.view_scrim, this)
    }

    fun setProgress(progress: Float) {
        if (this._progress != progress) {
            this._progress = progress
            onProgressChanged()
            invalidate()
        }
    }

    abstract fun draw(canvas: Canvas, width: Int, height: Int)
    protected open fun onProgressChanged() {}
    fun invalidate() {
        (view.parent as? View)?.invalidate()
    }

    companion object {
        var PROGRESS: Property<ViewScrim<*>, Float> = object : Property<ViewScrim<*>, Float>(java.lang.Float.TYPE, "progress") {
            override fun get(viewScrim: ViewScrim<*>): Float {
                return viewScrim._progress
            }

            override fun set(`object`: ViewScrim<*>, value: Float) {
                `object`.setProgress(value)
            }
        }

        @JvmStatic
        operator fun get(view: View): ViewScrim<*>? {
            return view.getTag(R.id.view_scrim) as? ViewScrim<*>
        }
    }
}