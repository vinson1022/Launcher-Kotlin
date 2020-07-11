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
package com.android.launcher3.keyboard

import android.graphics.Canvas
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.RecyclerView.ItemDecoration
import android.view.View
import com.android.launcher3.keyboard.FocusIndicatorHelper.SimpleFocusIndicatorHelper

/**
 * [ItemDecoration] for drawing and animating focused view background.
 */
class FocusedItemDecorator(container: View?) : ItemDecoration() {
    private val helper: FocusIndicatorHelper = SimpleFocusIndicatorHelper(container)
    val focusListener: View.OnFocusChangeListener
        get() = helper

    override fun onDraw(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        helper.draw(c)
    }
}