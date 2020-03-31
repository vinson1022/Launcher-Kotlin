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
package com.android.launcher3.shortcuts

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.Toast
import com.android.launcher3.BubbleTextView
import com.android.launcher3.R
import com.android.launcher3.Utilities

/**
 * A [BubbleTextView] that has the shortcut icon on the left and drag handle on the right.
 */
class DeepShortcutTextView @JvmOverloads constructor(
        context: Context?,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : BubbleTextView(context, attrs, defStyle) {

    private val dragHandleBounds = Rect()
    private val dragHandleWidth = (resources.getDimensionPixelSize(R.dimen.popup_padding_end)
            + resources.getDimensionPixelSize(R.dimen.deep_shortcut_drag_handle_size)
            + resources.getDimensionPixelSize(R.dimen.deep_shortcut_drawable_padding) / 2)
    private var showInstructionToast = false
    private var instructionToast: Toast? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        dragHandleBounds[0, 0, dragHandleWidth] = measuredHeight
        if (!Utilities.isRtl(resources)) {
            dragHandleBounds.offset(measuredWidth - dragHandleBounds.width(), 0)
        }
    }

    override fun applyCompoundDrawables(icon: Drawable) {
        // The icon is drawn in a separate view.
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            // Show toast if user touches the drag handle (long clicks still start the drag).
            showInstructionToast = dragHandleBounds.contains(ev.x.toInt(), ev.y.toInt())
        }
        return super.onTouchEvent(ev)
    }

    override fun performClick(): Boolean {
        if (showInstructionToast) {
            showToast()
            return true
        }
        return super.performClick()
    }

    private fun showToast() {
        if (instructionToast != null) {
            instructionToast!!.cancel()
        }
        val msg = Utilities.wrapForTts(
                context.getText(R.string.long_press_shortcut_to_add),
                context.getString(R.string.long_accessible_way_to_add_shortcut))
        instructionToast = Toast.makeText(context, msg, Toast.LENGTH_SHORT).also { it.show() }
    }
}