/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.appwidget.AppWidgetHostView
import android.os.Bundle
import com.android.launcher3.LauncherAppWidgetProviderInfo
import com.android.launcher3.LauncherSettings
import com.android.launcher3.PendingAddItemInfo

/**
 * Meta data used for late binding of [LauncherAppWidgetProviderInfo].
 *
 * @see {@link PendingAddItemInfo}
 */
open class PendingAddWidgetInfo(i: LauncherAppWidgetProviderInfo) : PendingAddItemInfo() {
    private var previewImage: Int
    var icon: Int
    @JvmField
    var info: LauncherAppWidgetProviderInfo
    @JvmField
    var boundWidget: AppWidgetHostView? = null
    @JvmField
    var bindOptions: Bundle? = null
    open val handler: WidgetAddFlowHandler?
        get() = WidgetAddFlowHandler(info)

    init {
        itemType = if (i.isCustomWidget) {
            LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET
        } else {
            LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
        }
        info = i
        user = i.profile
        componentName = i.provider
        previewImage = i.previewImage
        icon = i.icon
        spanX = i.spanX
        spanY = i.spanY
        minSpanX = i.minSpanX
        minSpanY = i.minSpanY
    }
}