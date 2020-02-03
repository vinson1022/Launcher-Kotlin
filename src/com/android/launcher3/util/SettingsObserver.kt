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

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.provider.Settings

interface SettingsObserver {
    /**
     * Registers the content observer to call [.onSettingChanged] when any of the
     * passed settings change. The value passed to onSettingChanged() is based on the key setting.
     */
    fun register(keySetting: String, vararg dependentSettings: String)

    fun unregister()
    fun onSettingChanged(keySettingEnabled: Boolean)
}

abstract class Secure(private val resolver: ContentResolver) : ContentObserver(Handler()), SettingsObserver {
    private var key: String? = null
    override fun register(keySetting: String, vararg dependentSettings: String) {
        key = keySetting
        resolver.registerContentObserver(
                Settings.Secure.getUriFor(key), false, this)
        for (setting in dependentSettings) {
            resolver.registerContentObserver(
                    Settings.Secure.getUriFor(setting), false, this)
        }
        onChange(true)
    }

    override fun unregister() {
        resolver.unregisterContentObserver(this)
    }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        onSettingChanged(Settings.Secure.getInt(resolver, key, 1) == 1)
    }

}

abstract class System(private val resolver: ContentResolver) : ContentObserver(Handler()), SettingsObserver {
    private var key: String? = null
    override fun register(keySetting: String, vararg dependentSettings: String) {
        key = keySetting
        resolver.registerContentObserver(
                Settings.System.getUriFor(key), false, this)
        for (setting in dependentSettings) {
            resolver.registerContentObserver(
                    Settings.System.getUriFor(setting), false, this)
        }
        onChange(true)
    }

    override fun unregister() {
        resolver.unregisterContentObserver(this)
    }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        onSettingChanged(Settings.System.getInt(resolver, key, 1) == 1)
    }

}
