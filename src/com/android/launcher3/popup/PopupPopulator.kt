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
package com.android.launcher3.popup

import android.os.Handler
import android.support.annotation.VisibleForTesting
import com.android.launcher3.ItemInfo
import com.android.launcher3.Launcher
import com.android.launcher3.ShortcutInfo
import com.android.launcher3.graphics.LauncherIcons
import com.android.launcher3.notification.NotificationInfo
import com.android.launcher3.notification.NotificationKeyData
import com.android.launcher3.shortcuts.DeepShortcutManager.Companion.getInstance
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.shortcuts.ShortcutInfoCompat
import com.android.launcher3.util.PackageUserKey.Companion.fromItemInfo
import java.util.*
import kotlin.math.max

/**
 * Contains logic relevant to populating a [PopupContainerWithArrow]. In particular,
 * this class determines which items appear in the container, and in what order.
 */
object PopupPopulator {
    const val MAX_SHORTCUTS = 4

    @VisibleForTesting
    const val NUM_DYNAMIC = 2
    const val MAX_SHORTCUTS_IF_NOTIFICATIONS = 2

    /**
     * Sorts shortcuts in rank order, with manifest shortcuts coming before dynamic shortcuts.
     */
    private val SHORTCUT_RANK_COMPARATOR = Comparator<ShortcutInfoCompat> { a, b ->
        if (a.isDeclaredInManifest && !b.isDeclaredInManifest) {
            return@Comparator -1
        }
        if (!a.isDeclaredInManifest && b.isDeclaredInManifest) {
            1
        } else a.rank.compareTo(b.rank)
    }

    /**
     * Filters the shortcuts so that only MAX_SHORTCUTS or fewer shortcuts are retained.
     * We want the filter to include both static and dynamic shortcuts, so we always
     * include NUM_DYNAMIC dynamic shortcuts, if at least that many are present.
     *
     * @param shortcutIdToRemoveFirst An id that should be filtered out first, if any.
     * @return a subset of shortcuts, in sorted order, with size <= MAX_SHORTCUTS.
     */
    @JvmStatic
    fun sortAndFilterShortcuts(
            shortcuts: List<ShortcutInfoCompat>, shortcutIdToRemoveFirst: String?): List<ShortcutInfoCompat> {
        val result = shortcuts.toMutableList()
        // Remove up to one specific shortcut before sorting and doing somewhat fancy filtering.
        result.find { it.id == shortcutIdToRemoveFirst }?.apply {
            result.remove(this)
        }
        result.sortWith(SHORTCUT_RANK_COMPARATOR)
        if (result.size <= MAX_SHORTCUTS) {
            return result
        }

        // The list of shortcuts is now sorted with static shortcuts followed by dynamic
        // shortcuts. We want to preserve this order, but only keep MAX_SHORTCUTS.
        val filteredShortcuts = mutableListOf<ShortcutInfoCompat>()
        var numDynamic = 0
        val size = result.size
        for (i in 0 until size) {
            val shortcut = result[i]
            val filteredSize = filteredShortcuts.size
            if (filteredSize < MAX_SHORTCUTS) {
                // Always add the first MAX_SHORTCUTS to the filtered list.
                filteredShortcuts.add(shortcut)
                if (shortcut.isDynamic) {
                    numDynamic++
                }
                continue
            }
            // At this point, we have MAX_SHORTCUTS already, but they may all be static.
            // If there are dynamic shortcuts, remove static shortcuts to add them.
            if (shortcut.isDynamic && numDynamic < NUM_DYNAMIC) {
                numDynamic++
                val lastStaticIndex = filteredSize - numDynamic
                filteredShortcuts.removeAt(lastStaticIndex)
                filteredShortcuts.add(shortcut)
            }
        }
        return filteredShortcuts
    }

    fun createUpdateRunnable(launcher: Launcher, originalInfo: ItemInfo,
                             uiHandler: Handler, container: PopupContainerWithArrow,
                             shortcutIds: List<String>?, shortcutViews: List<DeepShortcutView>,
                             notificationKeys: List<NotificationKeyData>): Runnable {
        val activity = originalInfo.targetComponent
        val user = originalInfo.user
        return Runnable {
            if (notificationKeys.isNotEmpty()) {
                val infos = launcher.popupDataProvider
                        .getStatusBarNotificationsForKeys(notificationKeys).map {
                            NotificationInfo(launcher, it)
                        }
                uiHandler.post { container.applyNotificationInfos(infos) }
            }
            var shortcuts = getInstance(launcher)
                    .queryForShortcutsContainer(activity, shortcutIds, user)
            val shortcutIdToDeDupe = if (notificationKeys.isEmpty()) null else notificationKeys[0].shortcutId
            shortcuts = sortAndFilterShortcuts(shortcuts, shortcutIdToDeDupe)
            for (i in 0 until max(shortcuts.size, shortcutViews.size)) {
                val shortcut = shortcuts[i]
                val si = ShortcutInfo(shortcut, launcher)
                // Use unbadged icon for the menu.
                LauncherIcons.obtain(launcher).apply {
                    createShortcutIcon(shortcut, false).applyTo(si)
                    recycle()
                }
                si.rank = i
                val view = shortcutViews[i]
                uiHandler.post { view.applyShortcutInfo(si, shortcut, container) }
            }

            // This ensures that mLauncher.getWidgetsForPackageUser()
            // doesn't return null (it puts all the widgets in memory).
            uiHandler.post {
                launcher.refreshAndBindWidgetsForPackageUser(
                        fromItemInfo(originalInfo))
            }
        }
    }
}