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
@file:JvmName("TraceHelper")

package com.android.launcher3.util

import android.os.SystemClock
import android.os.Trace
import android.util.ArrayMap
import android.util.Log
import android.util.Log.VERBOSE
import android.util.MutableLong
import com.android.launcher3.config.FeatureFlags

/**
 * A wrapper around [Trace] to allow easier proguarding for production builds.
 *
 * To enable any tracing log, execute the following command:
 * $ adb shell setprop log.tag.TAGNAME VERBOSE
 */
private const val ENABLED = FeatureFlags.IS_DOGFOOD_BUILD
private const val SYSTEM_TRACE = false
private val upTimes = if (ENABLED) ArrayMap<String, Long>() else null

fun beginSection(sectionName: String) {
    if (ENABLED) {
        val time = upTimes!![sectionName]
                ?: (if (Log.isLoggable(sectionName, VERBOSE)) 0L else -1L).also { upTimes[sectionName] = it }

        if (time >= 0) {
            if (SYSTEM_TRACE) {
                Trace.beginSection(sectionName)
            }
            upTimes[sectionName] = SystemClock.uptimeMillis()
        }
    }
}

fun partitionSection(sectionName: String, partition: String) {
    if (ENABLED) {
        val time = upTimes!![sectionName]?: return
        if (time >= 0) {
            if (SYSTEM_TRACE) {
                Trace.endSection()
                Trace.beginSection(sectionName)
            }
            val now = SystemClock.uptimeMillis()
            Log.d(sectionName, partition + " : " + (now - time))
            upTimes[sectionName] = now
        }
    }
}

fun endSection(sectionName: String) {
    if (ENABLED) {
        endSection(sectionName, "End")
    }
}

fun endSection(sectionName: String, msg: String) {
    if (ENABLED) {
        val time = upTimes!![sectionName]?: return
        if (time >= 0) {
            if (SYSTEM_TRACE) {
                Trace.endSection()
            }
            Log.d(sectionName, msg + " : " + (SystemClock.uptimeMillis() - time))
        }
    }
}
