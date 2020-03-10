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
package com.android.launcher3.views

import android.R
import android.content.Context
import android.preference.Preference
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

/**
 * Extension of [Preference] which makes the widget layout clickable.
 *
 * @see .setWidgetLayoutResource
 */
class ButtonPreference : Preference {
    private var widgetFrameVisible = false

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?) : super(context)

    fun setWidgetFrameVisible(isVisible: Boolean) {
        if (widgetFrameVisible != isVisible) {
            widgetFrameVisible = isVisible
            notifyChanged()
        }
    }

    override fun onBindView(view: View) {
        super.onBindView(view)
        val widgetFrame = view.findViewById<ViewGroup>(R.id.widget_frame)
        if (widgetFrame != null) {
            widgetFrame.visibility = if (widgetFrameVisible) View.VISIBLE else View.GONE
        }
    }
}