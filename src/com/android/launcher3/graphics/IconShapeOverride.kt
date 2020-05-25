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

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Process
import android.os.SystemClock
import android.preference.ListPreference
import android.preference.Preference
import android.preference.Preference.OnPreferenceChangeListener
import android.provider.Settings
import android.util.Log
import com.android.launcher3.LauncherAppState.Companion.getInstance
import com.android.launcher3.LauncherModel
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.util.LooperExecutor
import java.lang.reflect.Field

/**
 * Utility class to override shape of [android.graphics.drawable.AdaptiveIconDrawable].
 */
object IconShapeOverride {
    private const val TAG = "IconShapeOverride"
    const val KEY_PREFERENCE = "pref_override_icon_shape"

    // Time to wait before killing the process this ensures that the progress bar is visible for
    // sufficient time so that there is no flicker.
    private const val PROCESS_KILL_DELAY_MS = 1000L
    private const val RESTART_REQUEST_CODE = 42 // the answer to everything

    @JvmStatic
    fun isSupported(context: Context): Boolean {
        if (!Utilities.ATLEAST_OREO) {
            return false
        }
        // Only supported when developer settings is enabled
        if (Settings.Global.getInt(context.contentResolver,
                        Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 1) {
            return false
        }
        try {
            if (systemResField[null] !== Resources.getSystem()) {
                // Our assumption that mSystem is the system resource is not true.
                return false
            }
        } catch (e: Exception) {
            // Ignore, not supported
            return false
        }
        return configResId != 0
    }

    @JvmStatic
    fun apply(context: Context) {
        if (!Utilities.ATLEAST_OREO) return

        val path = getAppliedValue(context)
        if (path.isNullOrEmpty() || !isSupported(context)) return

        // magic
        try {
            val override: Resources = ResourcesOverride(Resources.getSystem(), configResId, path)
            systemResField[null] = override
        } catch (e: Exception) {
            Log.e(TAG, "Unable to override icon shape", e)
            // revert value.
            Utilities.getDevicePrefs(context).edit().remove(KEY_PREFERENCE).apply()
        }
    }

    @get:Throws(Exception::class)
    private val systemResField: Field
        get() {
            val staticField = Resources::class.java.getDeclaredField("mSystem")
            staticField.isAccessible = true
            return staticField
        }

    private val configResId: Int
        get() = Resources.getSystem().getIdentifier("config_icon_mask", "string", "android")

    private fun getAppliedValue(context: Context): String? {
        return Utilities.getDevicePrefs(context).getString(KEY_PREFERENCE, "")
    }

    @JvmStatic
    fun handlePreferenceUi(preference: ListPreference) {
        val context = preference.context
        preference.value = getAppliedValue(context)
        preference.onPreferenceChangeListener = PreferenceChangeHandler(context)
    }

    private class ResourcesOverride(
            parent: Resources,
            private val overrideId: Int,
            private val overrideValue: String
    ) : Resources(parent.assets, parent.displayMetrics, parent.configuration) {
        @Throws(NotFoundException::class)
        override fun getString(id: Int): String {
            return if (id == overrideId) {
                overrideValue
            } else super.getString(id)
        }
    }

    private class PreferenceChangeHandler(
            private val context: Context
    ) : OnPreferenceChangeListener {
        override fun onPreferenceChange(preference: Preference, o: Any): Boolean {
            val newValue = o as String
            if (getAppliedValue(context) != newValue) {
                // Value has changed
                ProgressDialog.show(context,
                        null,
                        context.getString(R.string.icon_shape_override_progress),
                        true,
                        false)
                LooperExecutor(LauncherModel.getWorkerLooper()).execute(
                        OverrideApplyHandler(context, newValue))
            }
            return false
        }

    }

    private class OverrideApplyHandler(
            private val context: Context,
            private val value: String
    ) : Runnable {
        override fun run() {
            // Synchronously write the preference.
            Utilities.getDevicePrefs(context).edit().putString(KEY_PREFERENCE, value).apply()
            // Clear the icon cache.
            getInstance(context).iconCache.clear()

            // Wait for it
            try {
                Thread.sleep(PROCESS_KILL_DELAY_MS)
            } catch (e: Exception) {
                Log.e(TAG, "Error waiting", e)
            }

            // Schedule an alarm before we kill ourself.
            val homeIntent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setPackage(context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pi = PendingIntent.getActivity(context, RESTART_REQUEST_CODE,
                    homeIntent, PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_ONE_SHOT)
            context.getSystemService(AlarmManager::class.java).setExact(
                    AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 50, pi)

            // Kill process
            Process.killProcess(Process.myPid())
        }

    }
}