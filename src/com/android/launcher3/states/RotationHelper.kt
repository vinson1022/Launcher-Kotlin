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
package com.android.launcher3.states

import android.app.Activity
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.pm.ActivityInfo
import android.content.res.Resources
import android.util.DisplayMetrics
import com.android.launcher3.R
import com.android.launcher3.Utilities

/**
 * Utility class to manage launcher rotation
 */
class RotationHelper(private val activity: Activity) : OnSharedPreferenceChangeListener {
    private var prefs: SharedPreferences?
    private val ignoreAutoRotateSettings = activity.resources.getBoolean(R.bool.allow_rotation)
    private var autoRotateEnabled = false
    /**
     * Rotation request made by [InternalStateHandler]. This supersedes any other request.
     */
    private var stateHandlerRequest = REQUEST_NONE
    /**
     * Rotation request made by a Launcher State
     */
    private var currentStateRequest = REQUEST_NONE
    // This is used to defer setting rotation flags until the activity is being created
    private var initialized = false
    var destroyed = false
    private var lastActivityFlags = -1

    init {
        // On large devices we do not handle auto-rotate differently.
        if (!ignoreAutoRotateSettings) {
            prefs = Utilities.getPrefs(activity).also {
                it.registerOnSharedPreferenceChangeListener(this)
                autoRotateEnabled = it.getBoolean(ALLOW_ROTATION_PREFERENCE_KEY,
                        allowRotationDefaultValue)

            }
        } else {
            prefs = null
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, s: String) {
        autoRotateEnabled = prefs!!.getBoolean(ALLOW_ROTATION_PREFERENCE_KEY,
                allowRotationDefaultValue)
        notifyChange()
    }

    fun setStateHandlerRequest(request: Int) {
        if (stateHandlerRequest != request) {
            stateHandlerRequest = request
            notifyChange()
        }
    }

    fun setCurrentStateRequest(request: Int) {
        if (currentStateRequest != request) {
            currentStateRequest = request
            notifyChange()
        }
    }

    fun initialize() {
        if (!initialized) {
            initialized = true
            notifyChange()
        }
    }

    fun destroy() {
        if (!destroyed) {
            destroyed = true
            prefs?.unregisterOnSharedPreferenceChangeListener(this)
        }
    }

    private fun notifyChange() {
        if (!initialized || destroyed) {
            return
        }
        val activityFlags: Int = if (stateHandlerRequest != REQUEST_NONE) {
            if (stateHandlerRequest == REQUEST_LOCK) ActivityInfo.SCREEN_ORIENTATION_LOCKED else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else if (currentStateRequest == REQUEST_LOCK) {
            ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else if (ignoreAutoRotateSettings || currentStateRequest == REQUEST_ROTATE || autoRotateEnabled) {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            // If auto rotation is off, allow rotation on the activity, in case the user is using
            // forced rotation.
            ActivityInfo.SCREEN_ORIENTATION_NOSENSOR
        }
        if (activityFlags != lastActivityFlags) {
            lastActivityFlags = activityFlags
            activity.requestedOrientation = activityFlags
        }
    }

    override fun toString(): String {
        return "[StateHandlerRequest=$stateHandlerRequest, CurrentStateRequest=$currentStateRequest, " +
                "LastActivityFlags=$lastActivityFlags, IgnoreAutoRotateSettings=$ignoreAutoRotateSettings, AutoRotateEnabled=$autoRotateEnabled]"
    }

    companion object {
        const val ALLOW_ROTATION_PREFERENCE_KEY = "pref_allowRotation"
        // If the device was scaled, used the original dimensions to determine if rotation
        // is allowed of not.
        @JvmStatic
        val allowRotationDefaultValue: Boolean
            get() {
                if (Utilities.ATLEAST_NOUGAT) {
                    // If the device was scaled, used the original dimensions to determine if rotation
                    // is allowed of not.
                    val res = Resources.getSystem()
                    val originalSmallestWidth = res.configuration.smallestScreenWidthDp * res.displayMetrics.densityDpi / DisplayMetrics.DENSITY_DEVICE_STABLE
                    return originalSmallestWidth >= 600
                }
                return false
            }

        const val REQUEST_NONE = 0
        const val REQUEST_ROTATE = 1
        const val REQUEST_LOCK = 2
    }
}