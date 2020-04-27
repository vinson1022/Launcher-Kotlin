/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.launcher3

import android.content.ComponentName
import android.content.Context
import android.content.Intent.*
import android.content.IntentFilter
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import com.android.launcher3.LauncherProvider.AUTHORITY
import com.android.launcher3.SettingsActivity.NOTIFICATION_BADGING
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.compat.PackageInstallerCompat
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.notification.NotificationListener
import com.android.launcher3.util.ConfigMonitor
import com.android.launcher3.util.Preconditions
import com.android.launcher3.util.Secure
import com.android.launcher3.util.SettingsObserver
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException

class LauncherAppState private constructor(context: Context) {
    val context: Context
    val model: LauncherModel
    val iconCache: IconCache
    val widgetCache: WidgetPreviewLoader
    val invariantDeviceProfile: InvariantDeviceProfile
    private var notificationBadgingObserver: SettingsObserver? = null

    init {
        if (getLocalProvider(context) == null) {
            throw RuntimeException(
                    "Initializing LauncherAppState in the absence of LauncherProvider")
        }
        Log.v(Launcher.TAG, "LauncherAppState initiated")
        Preconditions.assertUIThread()
        this.context = context
        invariantDeviceProfile = InvariantDeviceProfile(this.context)
        iconCache = IconCache(this.context, invariantDeviceProfile)
        widgetCache = WidgetPreviewLoader(this.context, iconCache)
        model = LauncherModel(this, iconCache, AppFilter.newInstance(this.context))
        LauncherAppsCompat.getInstance(this.context).addOnAppsChangedCallback(model)
        // Register intent receivers
        val filter = IntentFilter()
        filter.addAction(ACTION_LOCALE_CHANGED)
        // For handling managed profiles
        filter.addAction(ACTION_MANAGED_PROFILE_ADDED)
        filter.addAction(ACTION_MANAGED_PROFILE_REMOVED)
        filter.addAction(ACTION_MANAGED_PROFILE_AVAILABLE)
        filter.addAction(ACTION_MANAGED_PROFILE_UNAVAILABLE)
        filter.addAction(ACTION_MANAGED_PROFILE_UNLOCKED)
        if (FeatureFlags.IS_DOGFOOD_BUILD) {
            filter.addAction(ACTION_FORCE_ROLOAD)
        }
        context.registerReceiver(model, filter)
        UserManagerCompat.getInstance(this.context).enableAndResetCache()
        ConfigMonitor(this.context).register()
        notificationBadgingObserver = if (!context.resources.getBoolean(R.bool.notification_badging_enabled)) {
            null
        } else { // Register an observer to rebind the notification listener when badging is re-enabled.
            object : Secure(context.contentResolver) {
                override fun onSettingChanged(isNotificationBadgingEnabled: Boolean) {
                    if (isNotificationBadgingEnabled) {
                        NotificationListenerService.requestRebind(ComponentName(
                                this@LauncherAppState.context, NotificationListener::class.java))
                    }
                }
            }.also { it.register(NOTIFICATION_BADGING) }
        }
    }

    /**
     * Call from Application.onTerminate(), which is not guaranteed to ever be called.
     */
    fun onTerminate() {
        context.unregisterReceiver(model)
        val launcherApps = LauncherAppsCompat.getInstance(context)
        launcherApps.removeOnAppsChangedCallback(model)
        PackageInstallerCompat.getInstance(context).onStop()
        notificationBadgingObserver?.apply { unregister() }
    }

    fun setLauncher(launcher: Launcher?): LauncherModel {
        getLocalProvider(context)!!.setLauncherProviderChangeListener(launcher)
        model.initialize(launcher)
        return model
    }

    companion object {
        const val ACTION_FORCE_ROLOAD = "force-reload-launcher"
        // We do not need any synchronization for this variable as its only written on UI thread.
        var instanceNoCreate: LauncherAppState? = null
            private set

        @JvmStatic
        fun getInstance(context: Context): LauncherAppState {
            return instanceNoCreate?: if (Looper.myLooper() == Looper.getMainLooper()) {
                LauncherAppState(context.applicationContext).also { instanceNoCreate = it }
            } else {
                return try {
                    MainThreadExecutor().submit(Callable { getInstance(context) }).get()
                } catch (e: InterruptedException) {
                    throw RuntimeException(e)
                } catch (e: ExecutionException) {
                    throw RuntimeException(e)
                }
            }
        }

        /**
         * Shorthand for [.getInvariantDeviceProfile]
         */
        @JvmStatic
        fun getIDP(context: Context): InvariantDeviceProfile {
            return getInstance(context).invariantDeviceProfile
        }

        private fun getLocalProvider(context: Context): LauncherProvider? {
            context.contentResolver
                    .acquireContentProviderClient(AUTHORITY).use { cl -> return cl!!.localContentProvider as LauncherProvider? }
        }
    }
}