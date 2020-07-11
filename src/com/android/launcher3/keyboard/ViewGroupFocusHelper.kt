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

import android.graphics.Rect
import android.view.View
import com.android.launcher3.PagedView

/**
 * [FocusIndicatorHelper] for a generic view group.
 */
class ViewGroupFocusHelper(private val container: View) : FocusIndicatorHelper(container) {
    override fun viewToRect(v: View, outRect: Rect) {
        outRect.left = 0
        outRect.top = 0
        computeLocationRelativeToContainer(v, outRect)

        // If a view is scaled, its position will also shift accordingly. For optimization, only
        // consider this for the last node.
        outRect.left += ((1 - v.scaleX) * v.width / 2).toInt()
        outRect.top += ((1 - v.scaleY) * v.height / 2).toInt()
        outRect.right = outRect.left + (v.scaleX * v.width).toInt()
        outRect.bottom = outRect.top + (v.scaleY * v.height).toInt()
    }

    private fun computeLocationRelativeToContainer(child: View, outRect: Rect) {
        val parent = child.parent as View
        outRect.left += child.x.toInt()
        outRect.top += child.y.toInt()
        if (parent != container) {
            if (parent is PagedView<*>) {
                outRect.left -= parent.getScrollForPage(parent.indexOfChild(child))
            }
            computeLocationRelativeToContainer(parent, outRect)
        }
    }
}