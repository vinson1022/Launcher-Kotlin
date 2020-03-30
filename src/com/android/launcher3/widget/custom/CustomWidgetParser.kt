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

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.res.TypedArray
import android.os.Parcel
import android.os.Process
import android.util.SparseArray
import android.util.Xml
import com.android.launcher3.LauncherAppWidgetInfo
import com.android.launcher3.LauncherAppWidgetProviderInfo
import com.android.launcher3.R
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.util.*

/**
 * Utility class to parse {@ink CustomAppWidgetProviderInfo} definitions from xml
 */
object CustomWidgetParser {
    private var customWidgets: List<LauncherAppWidgetProviderInfo>? = null
    private var widgetsIdMap: SparseArray<ComponentName>? = null
    @JvmStatic
    fun getCustomWidgets(context: Context): List<LauncherAppWidgetProviderInfo>? {
        if (customWidgets == null) {
            // Synchronization not needed as it it safe to load multiple times
            parseCustomWidgets(context)
        }
        return customWidgets
    }

    @JvmStatic
    fun getWidgetIdForCustomProvider(context: Context, provider: ComponentName): Int {
        if (widgetsIdMap == null) {
            parseCustomWidgets(context)
        }
        val index = widgetsIdMap!!.indexOfValue(provider)
        return if (index >= 0) {
            LauncherAppWidgetInfo.CUSTOM_WIDGET_ID - widgetsIdMap!!.keyAt(index)
        } else {
            AppWidgetManager.INVALID_APPWIDGET_ID
        }
    }

    @JvmStatic
    fun getWidgetProvider(context: Context, widgetId: Int): LauncherAppWidgetProviderInfo? {
        if (widgetsIdMap == null || customWidgets == null) {
            parseCustomWidgets(context)
        }
        val cn = widgetsIdMap!![LauncherAppWidgetInfo.CUSTOM_WIDGET_ID - widgetId]
        for (info in customWidgets!!) {
            if (info.provider == cn) {
                return info
            }
        }
        return null
    }

    private fun parseCustomWidgets(context: Context) {
        val widgets = ArrayList<LauncherAppWidgetProviderInfo>()
        val idMap = SparseArray<ComponentName>()
        val providers = AppWidgetManager.getInstance(context)
                .getInstalledProvidersForProfile(Process.myUserHandle())
        if (providers.isEmpty()) {
            customWidgets = widgets
            widgetsIdMap = idMap
            return
        }
        val parcel = Parcel.obtain()
        providers[0].writeToParcel(parcel, 0)
        try {
            context.resources.getXml(R.xml.custom_widgets).use { parser ->
                val depth = parser.depth
                var type: Int
                while ((parser.next().also { type = it } != XmlPullParser.END_TAG ||
                                parser.depth > depth) && type != XmlPullParser.END_DOCUMENT) {
                    if (type == XmlPullParser.START_TAG && "widget" == parser.name) {
                        val a = context.obtainStyledAttributes(
                                Xml.asAttributeSet(parser), R.styleable.CustomAppWidgetProviderInfo)
                        parcel.setDataPosition(0)
                        val info = newInfo(a, parcel, context)
                        widgets.add(info)
                        a.recycle()
                        idMap.put(info.providerId, info.provider)
                    }
                }
            }
        } catch (e: IOException) {
            throw RuntimeException(e)
        } catch (e: XmlPullParserException) {
            throw RuntimeException(e)
        }
        parcel.recycle()
        customWidgets = widgets
        widgetsIdMap = idMap
    }

    private fun newInfo(a: TypedArray, parcel: Parcel, context: Context): CustomAppWidgetProviderInfo {
        val providerId = a.getInt(R.styleable.CustomAppWidgetProviderInfo_providerId, 0)
        val info = CustomAppWidgetProviderInfo(parcel, false, providerId)
        info.provider = ComponentName(context.packageName, LauncherAppWidgetProviderInfo.CLS_CUSTOM_WIDGET_PREFIX + providerId)
        info.label = a.getString(R.styleable.CustomAppWidgetProviderInfo_android_label)
        info.initialLayout = a.getResourceId(R.styleable.CustomAppWidgetProviderInfo_android_initialLayout, 0)
        info.icon = a.getResourceId(R.styleable.CustomAppWidgetProviderInfo_android_icon, 0)
        info.previewImage = a.getResourceId(R.styleable.CustomAppWidgetProviderInfo_android_previewImage, 0)
        info.resizeMode = a.getInt(R.styleable.CustomAppWidgetProviderInfo_android_resizeMode, 0)
        info.spanX = a.getInt(R.styleable.CustomAppWidgetProviderInfo_numColumns, 1)
        info.spanY = a.getInt(R.styleable.CustomAppWidgetProviderInfo_numRows, 1)
        info.minSpanX = a.getInt(R.styleable.CustomAppWidgetProviderInfo_numMinColumns, 1)
        info.minSpanY = a.getInt(R.styleable.CustomAppWidgetProviderInfo_numMinRows, 1)
        return info
    }
}