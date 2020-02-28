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
package com.android.launcher3.touch

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Utility methods for overscroll damping and related effect.
 */
object OverScroll {
    private const val OVERSCROLL_DAMP_FACTOR = 0.07f
    /**
     * This curve determines how the effect of scrolling over the limits of the page diminishes
     * as the user pulls further and further from the bounds
     *
     * @param f The percentage of how much the user has overscrolled.
     * @return A transformed percentage based on the influence curve.
     */
    private fun overScrollInfluenceCurve(f: Float): Float {
        var _f = f
        _f -= 1.0f
        return _f * _f * _f + 1.0f
    }

    /**
     * @param amount The original amount overscrolled.
     * @param max The maximum amount that the View can overscroll.
     * @return The dampened overscroll amount.
     */
    @JvmStatic
    fun dampedScroll(amount: Float, max: Int): Int {
        if (amount.compareTo(0f) == 0) return 0
        var f = amount / max
        f = f / abs(f) * overScrollInfluenceCurve(abs(f))
        // Clamp this factor, f, to -1 < f < 1
        if (abs(f) >= 1) {
            f /= abs(f)
        }
        return (OVERSCROLL_DAMP_FACTOR * f * max).roundToInt()
    }
}