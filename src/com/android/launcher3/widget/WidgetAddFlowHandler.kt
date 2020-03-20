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
package com.android.launcher3.widget

import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import com.android.launcher3.ItemInfo
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppWidgetInfo
import com.android.launcher3.LauncherAppWidgetProviderInfo
import com.android.launcher3.util.PendingRequestArgs

/**
 * Utility class to handle app widget add flow.
 */
open class WidgetAddFlowHandler : Parcelable {
    private val providerInfo: AppWidgetProviderInfo

    constructor(providerInfo: AppWidgetProviderInfo) {
        this.providerInfo = providerInfo
    }

    protected constructor(parcel: Parcel?) {
        providerInfo = AppWidgetProviderInfo.CREATOR.createFromParcel(parcel)
    }

    override fun describeContents() = 0

    override fun writeToParcel(parcel: Parcel, i: Int) {
        providerInfo.writeToParcel(parcel, i)
    }

    fun startBindFlow(launcher: Launcher, appWidgetId: Int, info: ItemInfo?, requestCode: Int) {
        launcher.setWaitingForResult(PendingRequestArgs.forWidgetInfo(appWidgetId, this, info))
        launcher.appWidgetHost
                .startBindFlow(launcher, appWidgetId, providerInfo, requestCode)
    }

    /**
     * @see .startConfigActivity
     */
    fun startConfigActivity(launcher: Launcher, info: LauncherAppWidgetInfo, requestCode: Int)
            = startConfigActivity(launcher, info.appWidgetId, info, requestCode)

    /**
     * Starts the widget configuration flow if needed.
     * @return true if the configuration flow was started, false otherwise.
     */
    open fun startConfigActivity(launcher: Launcher, appWidgetId: Int, info: ItemInfo?,
                                 requestCode: Int): Boolean {
        if (!needsConfigure()) return false

        launcher.setWaitingForResult(PendingRequestArgs.forWidgetInfo(appWidgetId, this, info))
        launcher.appWidgetHost.startConfigActivity(launcher, appWidgetId, requestCode)
        return true
    }

    open fun needsConfigure() = providerInfo.configure != null

    fun getProviderInfo(context: Context): LauncherAppWidgetProviderInfo
            = LauncherAppWidgetProviderInfo.fromProviderInfo(context, providerInfo)

    companion object CREATOR : Creator<WidgetAddFlowHandler> {
        override fun createFromParcel(parcel: Parcel): WidgetAddFlowHandler {
            return WidgetAddFlowHandler(parcel)
        }

        override fun newArray(size: Int): Array<WidgetAddFlowHandler?> {
            return arrayOfNulls(size)
        }
    }
}