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
package com.android.launcher3.util

import android.util.Property
import android.view.View
import java.lang.Float.TYPE

/**
 * Utility class to handle separating a single value as a factor of multiple values
 */
class MultiValueAlpha(private val view: View, size: Int) {
    private val properties: Array<AlphaProperty?> = arrayOfNulls(size)
    private var validMask: Int

    init {
        validMask = 0
        for (i in 0 until size) {
            val myMask = 1 shl i
            validMask = validMask or myMask
            properties[i] = AlphaProperty(myMask)
        }
    }

    fun getProperty(index: Int): AlphaProperty? {
        return properties[index]
    }

    inner class AlphaProperty internal constructor(private val mask: Int) {
        // Factor of all other alpha channels, only valid if mask is present in validMask.
        private var others = 1f

        // Our cache value is not correct, recompute it.
        // Since we have changed our value, all other caches except our own need to be
        // recomputed. Change validMask to indicate the new valid caches (only our own).
        var value: Float = 1f
            private set

        fun setValue(newValue: Float) {
            if (value == newValue) return

            if ((validMask and mask) == 0) {
                // Our cache value is not correct, recompute it.
                others = 1f
                properties.forEach {
                    it?.apply { others *= value }
                }
            }
            // Since we have changed our value, all other caches except our own need to be
            // recomputed. Change validMask to indicate the new valid caches (only our own).
            validMask = mask
            value = newValue
            view.alpha = others * value
        }
    }

    companion object {
        @JvmField
        val VALUE = object : Property<AlphaProperty, Float>(TYPE, "value") {
            override fun get(alphaProperty: AlphaProperty) = alphaProperty.value

            override fun set(alphaProperty: AlphaProperty, value: Float) {
                alphaProperty.setValue(value)
            }
        }
    }
}