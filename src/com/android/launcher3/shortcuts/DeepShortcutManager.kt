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
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.LauncherApps.ShortcutQuery
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.UserHandle
import android.util.Log
import com.android.launcher3.ItemInfo
import com.android.launcher3.LauncherSettings
import com.android.launcher3.Utilities

/**
 * Performs operations related to deep shortcuts, such as querying for them, pinning them, etc.
 */
class DeepShortcutManager private constructor(context: Context) {

    private val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    var wasLastCallSuccess = false
        private set

    fun onShortcutsChanged(shortcuts: List<ShortcutInfoCompat?>?) {
        // mShortcutCache.removeShortcuts(shortcuts);
    }

    /**
     * Queries for the shortcuts with the package name and provided ids.
     *
     * This method is intended to get the full details for shortcuts when they are added or updated,
     * because we only get "key" fields in onShortcutsChanged().
     */
    fun queryForFullDetails(packageName: String?,
                            shortcutIds: List<String>?, user: UserHandle): List<ShortcutInfoCompat> {
        return query(FLAG_GET_ALL, user, packageName, null, shortcutIds)
    }

    /**
     * Gets all the manifest and dynamic shortcuts associated with the given package and user,
     * to be displayed in the shortcuts container on long press.
     */
    fun queryForShortcutsContainer(activity: ComponentName,
                                   ids: List<String>?, user: UserHandle): List<ShortcutInfoCompat> {
        //TODO, Remove Api relative
        return query(ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_DYNAMIC,
                user, activity.packageName, activity, ids)
    }

    /**
     * Removes the given shortcut from the current list of pinned shortcuts.
     * (Runs on background thread)
     */
    @TargetApi(25)
    fun unpinShortcut(key: ShortcutKey) {
        if (Utilities.ATLEAST_NOUGAT_MR1) {
            val packageName = key.componentName.packageName
            val id = key.id
            val user = key.user
            val pinnedIds = extractIds(queryForPinnedShortcuts(packageName, user))
            pinnedIds.remove(id)
            wasLastCallSuccess = try {
                launcherApps.pinShortcuts(packageName, pinnedIds, user)
                true
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed to unpin shortcut", e)
                false
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Failed to unpin shortcut", e)
                false
            }
        }
    }

    /**
     * Adds the given shortcut to the current list of pinned shortcuts.
     * (Runs on background thread)
     */
    @TargetApi(25)
    fun pinShortcut(key: ShortcutKey) {
        if (Utilities.ATLEAST_NOUGAT_MR1) {
            val packageName = key.componentName.packageName
            val id = key.id
            val user = key.user
            val pinnedIds = extractIds(queryForPinnedShortcuts(packageName, user))
            pinnedIds.add(id)
            wasLastCallSuccess = try {
                launcherApps.pinShortcuts(packageName, pinnedIds, user)
                true
            } catch (e: SecurityException) {
                Log.w(TAG, "Failed to pin shortcut", e)
                false
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Failed to pin shortcut", e)
                false
            }
        }
    }

    @TargetApi(25)
    fun startShortcut(packageName: String?, id: String?, sourceBounds: Rect?,
                      startActivityOptions: Bundle?, user: UserHandle?) {
        if (Utilities.ATLEAST_NOUGAT_MR1) {
            wasLastCallSuccess = try {
                launcherApps.startShortcut(packageName!!, id!!, sourceBounds,
                        startActivityOptions, user!!)
                true
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to start shortcut", e)
                false
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to start shortcut", e)
                false
            }
        }
    }

    @TargetApi(25)
    fun getShortcutIconDrawable(shortcutInfo: ShortcutInfoCompat, density: Int): Drawable? {
        if (Utilities.ATLEAST_NOUGAT_MR1) {
            try {
                val icon = launcherApps.getShortcutIconDrawable(
                        shortcutInfo.shortcutInfo, density)
                wasLastCallSuccess = true
                return icon
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to get shortcut icon", e)
                wasLastCallSuccess = false
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to get shortcut icon", e)
                wasLastCallSuccess = false
            }
        }
        return null
    }

    /**
     * Returns the id's of pinned shortcuts associated with the given package and user.
     *
     * If packageName is null, returns all pinned shortcuts regardless of package.
     */
    fun queryForPinnedShortcuts(packageName: String?, user: UserHandle): List<ShortcutInfoCompat> {
        return query(ShortcutQuery.FLAG_MATCH_PINNED, user, packageName)
    }

    fun queryForAllShortcuts(user: UserHandle): List<ShortcutInfoCompat> {
        return query(FLAG_GET_ALL, user)
    }

    private fun extractIds(shortcuts: List<ShortcutInfoCompat>) = shortcuts.map { it.id }.toMutableList()

    /**
     * Query the system server for all the shortcuts matching the given parameters.
     * If packageName == null, we query for all shortcuts with the passed flags, regardless of app.
     *
     * TODO: Use the cache to optimize this so we don't make an RPC every time.
     */
    @TargetApi(25)
    private fun query(flags: Int, user: UserHandle, packageName: String? = null,
                      activity: ComponentName? = null, shortcutIds: List<String>? = null): List<ShortcutInfoCompat> {
        return if (Utilities.ATLEAST_NOUGAT_MR1) {
            val q = ShortcutQuery()
            q.setQueryFlags(flags)
            if (packageName != null) {
                q.setPackage(packageName)
                q.setActivity(activity)
                q.setShortcutIds(shortcutIds)
            }
            var shortcutInfos: List<ShortcutInfo>? = null
            try {
                shortcutInfos = launcherApps.getShortcuts(q, user)
                wasLastCallSuccess = true
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to query for shortcuts", e)
                wasLastCallSuccess = false
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to query for shortcuts", e)
                wasLastCallSuccess = false
            }
            shortcutInfos?.map { ShortcutInfoCompat(it) } ?: listOf()
        } else {
            listOf()
        }
    }

    @TargetApi(25)
    fun hasHostPermission(): Boolean {
        if (Utilities.ATLEAST_NOUGAT_MR1) {
            try {
                return launcherApps.hasShortcutHostPermission()
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to make shortcut manager call", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Failed to make shortcut manager call", e)
            }
        }
        return false
    }

    companion object {
        private const val TAG = "DeepShortcutManager"
        private const val FLAG_GET_ALL = (ShortcutQuery.FLAG_MATCH_DYNAMIC
                or ShortcutQuery.FLAG_MATCH_MANIFEST or ShortcutQuery.FLAG_MATCH_PINNED)

        private var sInstance: DeepShortcutManager? = null

        @JvmStatic
        fun getInstance(context: Context) = synchronized(DeepShortcutManager::class.java) {
            sInstance ?: DeepShortcutManager(context.applicationContext).also { sInstance = it }
        }

        @JvmStatic
        fun supportsShortcuts(info: ItemInfo): Boolean {
            val isItemPromise = (info is com.android.launcher3.ShortcutInfo
                    && info.hasPromiseIconUi())
            return info.itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION && !info.isDisabled && !isItemPromise
        }
    }

}