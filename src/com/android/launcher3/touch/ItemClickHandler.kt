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
package com.android.launcher3.touch

import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Process
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import com.android.launcher3.*
import com.android.launcher3.compat.AppWidgetManagerCompat
import com.android.launcher3.folder.FolderIcon
import com.android.launcher3.util.PackageManagerHelper
import com.android.launcher3.widget.PendingAppWidgetHostView
import com.android.launcher3.widget.WidgetAddFlowHandler

/**
 * Class for handling clicks on workspace and all-apps items
 */
object ItemClickHandler {
    /**
     * Instance used for click handling on items
     */
    @JvmField
    val clickListener = View.OnClickListener { v -> onClick(v) }

    private fun onClick(v: View) {
        // Make sure that rogue clicks don't get through while allapps is launching, or after the
        // view has detached (it's possible for this to happen if the view is removed mid touch).
        if (v.windowToken == null) return

        val launcher = Launcher.getLauncher(v.context)
        if (!launcher.workspace.isFinishedSwitchingState) return

        when(val tag = v.tag) {
            is ShortcutInfo -> onClickAppShortcut(v, tag, launcher)
            is FolderInfo -> (v as? FolderIcon)?.let { onClickFolderIcon(it) }
            is AppInfo -> startAppShortcutOrInfoActivity(v, tag, launcher)
            is LauncherAppWidgetInfo -> {
                if (v is PendingAppWidgetHostView) {
                    onClickPendingWidget(v, launcher)
                }
            }
        }
    }

    /**
     * Event handler for a folder icon click.
     *
     * @param v The view that was clicked. Must be an instance of [FolderIcon].
     */
    private fun onClickFolderIcon(v: View) {
        val folder = (v as FolderIcon).folder
        if (!folder.isOpen && !folder.isDestroyed) {
            // Open the requested folder
            folder.animateOpen()
        }
    }

    /**
     * Event handler for the app widget view which has not fully restored.
     */
    private fun onClickPendingWidget(v: PendingAppWidgetHostView, launcher: Launcher) {
        if (launcher.packageManager.isSafeMode) {
            Toast.makeText(launcher, R.string.safemode_widget_error, Toast.LENGTH_SHORT).show()
            return
        }

        val info = v.tag as LauncherAppWidgetInfo
        if (v.isReadyForClickSetup) {
            val appWidgetInfo = AppWidgetManagerCompat
                    .getInstance(launcher).findProvider(info.providerName, info.user)
                    ?: return
            val addFlowHandler = WidgetAddFlowHandler(appWidgetInfo)
            if (info.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)) {
                if (!info.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_ALLOCATED)) { // This should not happen, as we make sure that an Id is allocated during bind.
                    return
                }
                addFlowHandler.startBindFlow(launcher, info.appWidgetId, info,
                        Launcher.REQUEST_BIND_PENDING_APPWIDGET)
            } else {
                addFlowHandler.startConfigActivity(launcher, info, Launcher.REQUEST_RECONFIGURE_APPWIDGET)
            }
        } else {
            val packageName = info.providerName.packageName
            onClickPendingAppItem(v, launcher, packageName, info.installProgress >= 0)
        }
    }

    private fun onClickPendingAppItem(v: View, launcher: Launcher, packageName: String,
                                      downloadStarted: Boolean) {
        if (downloadStarted) {
            // If the download has started, simply direct to the market app.
            startMarketIntentForPackage(v, launcher, packageName)
            return
        }
        AlertDialog.Builder(launcher)
                .setTitle(R.string.abandoned_promises_title)
                .setMessage(R.string.abandoned_promise_explanation)
                .setPositiveButton(R.string.abandoned_search
                ) { _: DialogInterface?, _: Int -> startMarketIntentForPackage(v, launcher, packageName) }
                .setNeutralButton(R.string.abandoned_clean_this
                ) { _: DialogInterface?, _: Int ->
                    launcher.workspace
                            .removeAbandonedPromise(packageName, Process.myUserHandle())
                }
                .create().show()
    }

    private fun startMarketIntentForPackage(v: View, launcher: Launcher, packageName: String) {
        val item = v.tag as ItemInfo
        val intent = PackageManagerHelper(launcher).getMarketIntent(packageName)
        launcher.startActivitySafely(v, intent, item)
    }

    /**
     * Event handler for an app shortcut click.
     *
     * @param v The view that was clicked. Must be a tagged with a [ShortcutInfo].
     */
    private fun onClickAppShortcut(v: View, shortcut: ShortcutInfo, launcher: Launcher) {
        if (shortcut.isDisabled) {
            val disabledFlags = shortcut.runtimeStatusFlags and ShortcutInfo.FLAG_DISABLED_MASK
            if (disabledFlags and
                    ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED.inv() and
                    ItemInfoWithIcon.FLAG_DISABLED_QUIET_USER.inv() == 0) {
                // If the app is only disabled because of the above flags, launch activity anyway.
                // Framework will tell the user why the app is suspended.
            } else {
                if (!TextUtils.isEmpty(shortcut.disabledMessage)) { // Use a message specific to this shortcut, if it has one.
                    Toast.makeText(launcher, shortcut.disabledMessage, Toast.LENGTH_SHORT).show()
                    return
                }
                // Otherwise just use a generic error message.
                var error = R.string.activity_not_available
                if (shortcut.runtimeStatusFlags and ItemInfoWithIcon.FLAG_DISABLED_SAFEMODE != 0) {
                    error = R.string.safemode_shortcut_error
                } else if (shortcut.runtimeStatusFlags and ItemInfoWithIcon.FLAG_DISABLED_BY_PUBLISHER != 0 ||
                        shortcut.runtimeStatusFlags and ItemInfoWithIcon.FLAG_DISABLED_LOCKED_USER != 0) {
                    error = R.string.shortcut_not_available
                }
                Toast.makeText(launcher, error, Toast.LENGTH_SHORT).show()
                return
            }
        }
        // Check for abandoned promise
        if (v is BubbleTextView && shortcut.hasPromiseIconUi()) {
            val packageName = shortcut.intent.component?.packageName?: shortcut.intent.getPackage()!!
            if (!TextUtils.isEmpty(packageName)) {
                onClickPendingAppItem(v, launcher, packageName,
                        shortcut.hasStatusFlag(ShortcutInfo.FLAG_INSTALL_SESSION_ACTIVE))
                return
            }
        }
        // Start activities
        startAppShortcutOrInfoActivity(v, shortcut, launcher)
    }

    private fun startAppShortcutOrInfoActivity(v: View, item: ItemInfo, launcher: Launcher) {
        var intent = if (item is PromiseAppInfo) item.getMarketIntent(launcher) else item.intent

        requireNotNull(intent) { "Input must have a valid intent" }
        if (item is ShortcutInfo) {
            if (item.hasStatusFlag(ShortcutInfo.FLAG_SUPPORTS_WEB_UI)
                    && intent.action === Intent.ACTION_VIEW) {
                // make a copy of the intent that has the package set to null
                // we do this because the platform sometimes disables instant
                // apps temporarily (triggered by the user) and fallbacks to the
                // web ui. This only works though if the package isn't set
                intent = Intent(intent)
                intent.setPackage(null)
            }
        }
        launcher.startActivitySafely(v, intent, item)
    }
}