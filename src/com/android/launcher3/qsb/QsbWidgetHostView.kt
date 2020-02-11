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
package com.android.launcher3.qsb

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewDebug.ExportedProperty
import android.view.ViewGroup
import android.widget.RemoteViews
import com.android.launcher3.Launcher
import com.android.launcher3.R

/**
 * Appwidget host view with QSB specific logic.
 */
class QsbWidgetHostView(context: Context?) : AppWidgetHostView(context) {
    @ExportedProperty(category = "launcher")
    private var previousOrientation = 0

    override fun updateAppWidget(remoteViews: RemoteViews) {
        // Store the orientation in which the widget was inflated
        previousOrientation = resources.configuration.orientation
        super.updateAppWidget(remoteViews)
    }

    fun isReinflateRequired(orientation: Int): Boolean {
        // Re-inflate is required if the orientation has changed since last inflation.
        return previousOrientation != orientation
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        try {
            super.onLayout(changed, left, top, right, bottom)
        } catch (e: RuntimeException) {
            post {
                // Update the widget with 0 Layout id, to reset the view to error view.
                updateAppWidget(RemoteViews(appWidgetInfo.provider.packageName, 0))
            }
        }
    }

    override fun getErrorView() = getDefaultView(this)

    override fun getDefaultView(): View
            = super.getDefaultView().also { setOnClickListener(getClickListener(context)) }

    companion object {
        fun getClickListener(context: Context): (View) -> Unit
                = { Launcher.getLauncher(context).startSearch("", false, null, true) }

        fun getDefaultView(parent: ViewGroup): View
                = LayoutInflater.from(parent.context)
                    .inflate(R.layout.qsb_default_view, parent, false).apply {
                        findViewById<View>(R.id.btn_qsb_search).setOnClickListener(getClickListener(parent.context))
                    }
    }
}