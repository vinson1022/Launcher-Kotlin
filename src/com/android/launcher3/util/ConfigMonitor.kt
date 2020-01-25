package com.android.launcher3.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_CONFIGURATION_CHANGED
import android.content.IntentFilter
import android.os.Process
import android.util.Log

/**
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * [BroadcastReceiver] which watches configuration changes and
 * restarts the process in case changes which affect the device profile occur.
 */
class ConfigMonitor(private val context: Context) : BroadcastReceiver() {
    private val fontScale = context.resources.configuration.fontScale
    private val density = context.resources.configuration.densityDpi

    override fun onReceive(context: Context, intent: Intent) {
        val config = context.resources.configuration
        if (fontScale != config.fontScale || density != config.densityDpi) {
            Log.d("ConfigMonitor", "Configuration changed, restarting launcher")
            this.context.unregisterReceiver(this)
            Process.killProcess(Process.myPid())
        }
    }

    fun register() {
        context.registerReceiver(this, IntentFilter(ACTION_CONFIGURATION_CHANGED))
    }
}