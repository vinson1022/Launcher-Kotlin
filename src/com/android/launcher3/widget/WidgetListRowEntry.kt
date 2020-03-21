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
package com.android.launcher3.widget

import com.android.launcher3.model.PackageItemInfo
import com.android.launcher3.model.WidgetItem
import java.util.*

/**
 * Holder class to store all the information related to a single row in the widget list
 */
class WidgetListRowEntry(val pkgItem: PackageItemInfo, val widgets: ArrayList<WidgetItem>) {

    /**
     * Character that is used as a section name for the [ItemInfo.title].
     * (e.g., "G" will be stored if title is "Google")
     */
    @JvmField
    var titleSectionName: String? = null
    override fun toString() = "${pkgItem.packageName}:${widgets.size}"
}