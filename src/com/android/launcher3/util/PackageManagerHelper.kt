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

import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_VIEW
import android.content.Intent.EXTRA_REFERRER
import android.content.pm.ApplicationInfo
import android.content.pm.ApplicationInfo.FLAG_EXTERNAL_STORAGE
import android.content.pm.ApplicationInfo.FLAG_SUSPENDED
import android.content.pm.PackageManager.*
import android.graphics.Rect
import android.net.Uri
import android.os.Build.VERSION_CODES.M
import android.os.Bundle
import android.os.UserHandle
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import com.android.launcher3.*
import com.android.launcher3.Utilities.ATLEAST_MARSHMALLOW
import com.android.launcher3.Utilities.ATLEAST_NOUGAT
import com.android.launcher3.compat.LauncherAppsCompat
import java.net.URISyntaxException

/**
 * Utility methods using package manager
 */
class PackageManagerHelper(private val context: Context) {
    private val packageManager = context.packageManager
    private val launcherApps = LauncherAppsCompat.getInstance(context)

    /**
     * Returns true if the app can possibly be on the SDCard. This is just a workaround and doesn't
     * guarantee that the app is on SD card.
     */
    fun isAppOnSdcard(packageName: String?, user: UserHandle?): Boolean {
        val info = launcherApps.getApplicationInfo(
                packageName, MATCH_UNINSTALLED_PACKAGES, user)
        return info != null && info.flags and FLAG_EXTERNAL_STORAGE != 0
    }

    /**
     * Returns whether the target app is suspended for a given user as per
     * [android.app.admin.DevicePolicyManager.isPackageSuspended].
     */
    fun isAppSuspended(packageName: String?, user: UserHandle?): Boolean {
        val info = launcherApps.getApplicationInfo(packageName, 0, user)
        return info != null && isAppSuspended(info)
    }

    val isSafeMode: Boolean
        get() = context.packageManager.isSafeMode

    fun getAppLaunchIntent(pkg: String?, user: UserHandle?): Intent? {
        val activities = launcherApps.getActivityList(pkg, user)
        return if (activities.isEmpty()) null else AppInfo.makeLaunchIntent(activities[0])
    }

    /**
     * Returns true if {@param srcPackage} has the permission required to start the activity from
     * {@param intent}. If {@param srcPackage} is null, then the activity should not need
     * any permissions
     */
    fun hasPermissionForActivity(intent: Intent?, srcPackage: String?): Boolean {
        val target = packageManager.resolveActivity(intent, 0)
                ?: return false // Not a valid target
        if (target.activityInfo.permission.isEmpty()) return true // No permission is needed
        if (srcPackage.isNullOrEmpty()) return false // The activity requires some permission but there is no source.

        if (packageManager.checkPermission(target.activityInfo.permission, srcPackage) != PERMISSION_GRANTED) {
            return false // Source does not have sufficient permissions.
        }
        if (!ATLEAST_MARSHMALLOW) return true // These checks are sufficient for below M devices.

        // On M and above also check AppOpsManager for compatibility mode permissions.
        if (AppOpsManager.permissionToOp(target.activityInfo.permission).isEmpty()) {
            return true // There is no app-op for this permission, which could have been disabled.
        }
        // There is no direct way to check if the app-op is allowed for a particular app. Since
        // app-op is only enabled for apps running in compatibility mode, simply block such apps.
        try {
            return packageManager.getApplicationInfo(srcPackage, 0).targetSdkVersion >= M
        } catch (e: NameNotFoundException) {
        }
        return false
    }

    fun getMarketIntent(packageName: String?): Intent {
        return Intent(ACTION_VIEW)
                .setData(Uri.Builder()
                        .scheme("market")
                        .authority("details")
                        .appendQueryParameter("id", packageName)
                        .build())
                .putExtra(EXTRA_REFERRER, Uri.Builder().scheme("android-app")
                        .authority(context.packageName).build())
    }

    /**
     * Starts the details activity for `info`
     */
    fun startDetailsActivityForInfo(info: ItemInfo, sourceBounds: Rect?, opts: Bundle?) {
        if (info is PromiseAppInfo) {
            context.startActivity(info.getMarketIntent(context))
            return
        }

        val componentName: ComponentName = when (info) {
            is AppInfo -> info.componentName
            is ShortcutInfo -> info.getTargetComponent()
            is PendingAddItemInfo -> info.componentName
            is LauncherAppWidgetInfo -> info.providerName
            else -> null
        }?: return

        try {
            launcherApps.showAppDetailsForProfile(
                    componentName, info.user, sourceBounds, opts)
        } catch (e: SecurityException) {
            Toast.makeText(context, R.string.activity_not_found, LENGTH_SHORT).show()
            Log.e(TAG, "Unable to launch settings", e)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.activity_not_found, LENGTH_SHORT).show()
            Log.e(TAG, "Unable to launch settings", e)
        }
    }

    companion object {
        private const val TAG = "PackageManagerHelper"
        /**
         * Returns whether an application is suspended as per
         * [android.app.admin.DevicePolicyManager.isPackageSuspended].
         */
        @JvmStatic
        fun isAppSuspended(info: ApplicationInfo): Boolean {
            // The value of FLAG_SUSPENDED was reused by a hidden constant
            // ApplicationInfo.FLAG_PRIVILEGED prior to N, so only check for suspended flag on N
            // or later.
            return if (ATLEAST_NOUGAT) {
                info.flags and FLAG_SUSPENDED != 0
            } else {
                false
            }
        }

        /**
         * Creates a new market search intent.
         */
        @JvmStatic
        fun getMarketSearchIntent(context: Context, query: String?): Intent {
            return try {
                val intent = Intent.parseUri(context.getString(R.string.market_search_intent), 0)
                if (!TextUtils.isEmpty(query)) {
                    intent.data = intent.data!!.buildUpon().appendQueryParameter("q", query).build()
                }
                intent
            } catch (e: URISyntaxException) {
                throw RuntimeException(e)
            }
        }
    }
}