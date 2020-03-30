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
package com.android.launcher3.widget.custom

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import com.android.launcher3.LauncherAppWidgetProviderInfo
import com.android.launcher3.Utilities

/**
 * Custom app widget provider info that can be used as a widget, but provide extra functionality
 * by allowing custom code and views.
 */
class CustomAppWidgetProviderInfo
constructor(
        parcel: Parcel,
        readSelf: Boolean,
        providerId: Int
) : LauncherAppWidgetProviderInfo(parcel), Parcelable {

    var providerId = 0
        private set

    init {
        if (readSelf) {
            this.providerId = parcel.readInt()
            provider = ComponentName(parcel.readString()!!, CLS_CUSTOM_WIDGET_PREFIX + providerId)
            label = parcel.readString()
            initialLayout = parcel.readInt()
            icon = parcel.readInt()
            previewImage = parcel.readInt()
            resizeMode = parcel.readInt()
            spanX = parcel.readInt()
            spanY = parcel.readInt()
            minSpanX = parcel.readInt()
            minSpanY = parcel.readInt()
        } else {
            this.providerId = providerId
        }
    }

    override fun initSpans(context: Context) {}
    override fun getLabel(packageManager: PackageManager): String {
        return Utilities.trim(label)
    }

    override fun toString(): String {
        return "WidgetProviderInfo($provider)"
    }

    override fun writeToParcel(out: Parcel, flags: Int) {
        super.writeToParcel(out, flags)
        out.writeInt(providerId)
        out.writeString(provider.packageName)
        out.writeString(label)
        out.writeInt(initialLayout)
        out.writeInt(icon)
        out.writeInt(previewImage)
        out.writeInt(resizeMode)
        out.writeInt(spanX)
        out.writeInt(spanY)
        out.writeInt(minSpanX)
        out.writeInt(minSpanY)
    }

    companion object CREATOR: Creator<CustomAppWidgetProviderInfo?> {
        override fun createFromParcel(parcel: Parcel): CustomAppWidgetProviderInfo? {
            return CustomAppWidgetProviderInfo(parcel, true, 0)
        }

        override fun newArray(size: Int): Array<CustomAppWidgetProviderInfo?> {
            return arrayOfNulls(size)
        }
    }
}