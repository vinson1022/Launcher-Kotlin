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
package com.android.launcher3.util

import android.content.ComponentName
import android.os.UserHandle
import com.android.launcher3.FolderInfo
import com.android.launcher3.ItemInfo
import com.android.launcher3.LauncherAppWidgetInfo
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.ShortcutInfo
import com.android.launcher3.shortcuts.ShortcutKey
import java.util.*

/**
 * A utility class to check for [ItemInfo]
 */
abstract class ItemInfoMatcher {
    abstract fun matches(info: ItemInfo, cn: ComponentName?): Boolean
    /**
     * Filters {@param infos} to those satisfying the [.matches].
     */
    fun filterItemInfos(infos: Iterable<ItemInfo>): HashSet<ItemInfo> {
        val filtered = HashSet<ItemInfo>()
        infos.forEach {
            when (it) {
                is ShortcutInfo -> {
                    val cn = it.targetComponent
                    if (cn != null && matches(it, cn)) {
                        filtered.add(it)
                    }
                }
                is FolderInfo -> {
                    for (s in it.contents) {
                        val cn = s.targetComponent
                        if (cn != null && matches(s, cn)) {
                            filtered.add(s)
                        }
                    }
                }
                is LauncherAppWidgetInfo -> {
                    val cn = it.providerName
                    if (cn != null && matches(it, cn)) {
                        filtered.add(it)
                    }
                }
            }
        }
        return filtered
    }

    /**
     * Returns a new matcher with returns true if either this or {@param matcher} returns true.
     */
    fun or(matcher: ItemInfoMatcher): ItemInfoMatcher {
        val that = this
        return object : ItemInfoMatcher() {
            override fun matches(info: ItemInfo, cn: ComponentName?)
                    = that.matches(info, cn) || matcher.matches(info, cn)
        }
    }

    /**
     * Returns a new matcher with returns true if both this and {@param matcher} returns true.
     */
    fun and(matcher: ItemInfoMatcher): ItemInfoMatcher {
        val that = this
        return object : ItemInfoMatcher() {
            override fun matches(info: ItemInfo, cn: ComponentName?)
                    = that.matches(info, cn) && matcher.matches(info, cn)
        }
    }

    companion object {
        /**
         * Returns a new matcher which returns the opposite boolean value of the provided
         * {@param matcher}.
         */
        @JvmStatic
        fun not(matcher: ItemInfoMatcher) = object : ItemInfoMatcher() {
            override fun matches(info: ItemInfo, cn: ComponentName?) = !matcher.matches(info, cn)
        }

        @JvmStatic
        fun ofUser(user: UserHandle) = object : ItemInfoMatcher() {
            override fun matches(info: ItemInfo, cn: ComponentName?) = info.user == user
        }

        @JvmStatic
        fun ofComponents(components: HashSet<ComponentName>, user: UserHandle) = object : ItemInfoMatcher() {
            override fun matches(info: ItemInfo, cn: ComponentName?)
                    = cn != null && components.contains(cn) && info.user == user
        }

        @JvmStatic
        fun ofPackages(packageNames: HashSet<String>, user: UserHandle) = object : ItemInfoMatcher() {
            override fun matches(info: ItemInfo, cn: ComponentName?)
                    = cn != null && packageNames.contains(cn.packageName) && info.user == user
        }

        @JvmStatic
        fun ofShortcutKeys(keys: HashSet<ShortcutKey>) = object : ItemInfoMatcher() {
            override fun matches(info: ItemInfo, cn: ComponentName?)
                    = info.itemType == ITEM_TYPE_DEEP_SHORTCUT && keys.contains(ShortcutKey.fromItemInfo(info))
        }

        @JvmStatic
        fun ofItemIds(ids: LongArrayMap<Boolean>, matchDefault: Boolean) = object : ItemInfoMatcher() {
            override fun matches(info: ItemInfo, cn: ComponentName?) = ids[info.id, matchDefault]
        }
    }
}