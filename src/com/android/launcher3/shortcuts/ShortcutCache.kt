/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.shortcuts

import android.annotation.TargetApi
import android.os.Build
import android.util.ArrayMap
import android.util.LruCache

/**
 * Loads [ShortcutInfoCompat]s on demand (e.g. when launcher
 * loads for pinned shortcuts and on long-press for dynamic shortcuts), and caches them
 * for handful of apps in an LruCache while launcher lives.
 */
@TargetApi(Build.VERSION_CODES.N)
class ShortcutCache {
    private val cachedShortcuts: LruCache<ShortcutKey, ShortcutInfoCompat> = LruCache(CACHE_SIZE)

    // We always keep pinned shortcuts in the cache.
    private val pinnedShortcuts: ArrayMap<ShortcutKey, ShortcutInfoCompat> = ArrayMap()

    /**
     * Removes shortcuts from the cache when shortcuts change for a given package.
     *
     * Returns a map of ids to their evicted shortcuts.
     *
     * @see android.content.pm.LauncherApps.Callback.onShortcutsChanged
     */
    fun removeShortcuts(shortcuts: List<ShortcutInfoCompat>) {
        for (shortcut in shortcuts) {
            val key = ShortcutKey.fromInfo(shortcut)
            cachedShortcuts.remove(key)
            pinnedShortcuts.remove(key)
        }
    }

    operator fun get(key: ShortcutKey): ShortcutInfoCompat? {
        return if (pinnedShortcuts.containsKey(key)) {
            pinnedShortcuts[key]
        } else cachedShortcuts[key]
    }

    fun put(key: ShortcutKey, shortcut: ShortcutInfoCompat) {
        if (shortcut.isPinned) {
            pinnedShortcuts[key] = shortcut
        } else {
            cachedShortcuts.put(key, shortcut)
        }
    }

    companion object {
        private const val CACHE_SIZE = 30 // Max number shortcuts we cache.
    }
}