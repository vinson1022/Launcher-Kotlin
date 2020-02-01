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
package com.android.launcher3.util

import android.os.Build
import android.support.annotation.RequiresApi
import android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
import android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
import android.view.Window
import com.android.launcher3.Utilities.ATLEAST_OREO

/**
 * Utility class to manage various window flags to control system UI.
 */
class SystemUiController(private val window: Window) {
    private val states = IntArray(5)

    fun updateUiState(uiState: Int, isLight: Boolean) {
        updateUiState(uiState, if (isLight) FLAG_LIGHT_NAV or FLAG_LIGHT_STATUS else FLAG_DARK_NAV or FLAG_DARK_STATUS)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    fun updateUiState(uiState: Int, flags: Int) {
        if (states[uiState] == flags) {
            return
        }
        states[uiState] = flags
        val oldFlags = window.decorView.systemUiVisibility
        // Apply the state flags in priority order
        var newFlags = oldFlags
        for (stateFlag in states) {
            if (ATLEAST_OREO) {
                if (stateFlag and FLAG_LIGHT_NAV != 0) {
                    newFlags = newFlags or SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else if (stateFlag and FLAG_DARK_NAV != 0) {
                    newFlags = newFlags and SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
                }
            }
            if (stateFlag and FLAG_LIGHT_STATUS != 0) {
                newFlags = newFlags or SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            } else if (stateFlag and FLAG_DARK_STATUS != 0) {
                newFlags = newFlags and SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            }
        }
        if (newFlags != oldFlags) {
            window.decorView.systemUiVisibility = newFlags
        }
    }

    override fun toString() = "mStates=${states.contentToString()}"

    companion object {
        // Various UI states in increasing order of priority
        const val UI_STATE_BASE_WINDOW = 0
        const val UI_STATE_ALL_APPS = 1
        const val UI_STATE_WIDGET_BOTTOM_SHEET = 2
        const val UI_STATE_ROOT_VIEW = 3
        const val UI_STATE_OVERVIEW = 4
        const val FLAG_LIGHT_NAV = 1 shl 0
        const val FLAG_DARK_NAV = 1 shl 1
        const val FLAG_LIGHT_STATUS = 1 shl 2
        const val FLAG_DARK_STATUS = 1 shl 3
    }

}