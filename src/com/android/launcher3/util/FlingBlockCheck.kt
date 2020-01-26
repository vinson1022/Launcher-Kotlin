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

import android.os.SystemClock

/**
 * Determines whether a fling should be blocked. Currently we block flings when crossing thresholds
 * to new states, and unblock after a short duration.
 */
class FlingBlockCheck {
    var isBlocked = false
        private set
    private var blockFlingTime = 0L
    fun blockFling() {
        isBlocked = true
        blockFlingTime = SystemClock.uptimeMillis()
    }

    fun unblockFling() {
        isBlocked = false
        blockFlingTime = 0
    }

    fun onEvent() {
        // We prevent flinging after passing a state, but allow it if the user pauses briefly.
        if (SystemClock.uptimeMillis() - blockFlingTime >= UNBLOCK_FLING_PAUSE_DURATION) {
            isBlocked = false
        }
    }

    companion object {
        // Allow flinging to a new state after waiting this many milliseconds.
        private const val UNBLOCK_FLING_PAUSE_DURATION = 200L
    }
}