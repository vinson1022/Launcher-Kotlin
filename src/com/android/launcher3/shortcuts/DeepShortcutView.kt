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
import android.graphics.Point
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import com.android.launcher3.*
import com.android.launcher3.popup.PopupContainerWithArrow
import com.android.launcher3.touch.ItemClickHandler
import kotlinx.android.synthetic.main.deep_shortcut.view.*

/**
 * A [android.widget.FrameLayout] that contains a [DeepShortcutView].
 * This lets us animate the DeepShortcutView (icon and text) separately from the background.
 */
class DeepShortcutView
@JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val pillRect = Rect()
    val bubbleText: BubbleTextView
        get() = findViewById(R.id.bubble_text)
    val iconView: View
        get() = icon
    private var info: ShortcutInfo? = null
    private var detail: ShortcutInfoCompat? = null
    private val tempPoint = Point()

    fun setDividerVisibility(visibility: Int) {
        divider.visibility = visibility
    }

    fun setWillDrawIcon(willDraw: Boolean) {
        icon.visibility = if (willDraw) View.VISIBLE else View.INVISIBLE
    }

    fun willDrawIcon() = icon.visibility == View.VISIBLE

    /**
     * Returns the position of the center of the icon relative to the container.
     */
    val iconCenter: Point
        get() {
            tempPoint.x = measuredHeight / 2
            tempPoint.y = tempPoint.x
            if (Utilities.isRtl(resources)) {
                tempPoint.x = measuredWidth - tempPoint.x
            }
            return tempPoint
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        pillRect[0, 0, measuredWidth] = measuredHeight
    }

    /** package private  */
    fun applyShortcutInfo(info: ShortcutInfo?, detail: ShortcutInfoCompat,
                          container: PopupContainerWithArrow?) {
        this.info = info
        this.detail = detail
        bubble_text.applyFromShortcutInfo(info)
        icon.background = bubble_text.icon

        bubble_text.bind(container)
    }

    private fun DeepShortcutTextView.bind(container: PopupContainerWithArrow?) {
        // Use the long label as long as it exists and fits.
        val longLabel = detail?.longLabel
        val availableWidth = (width - totalPaddingLeft
                - totalPaddingRight)
        val usingLongLabel = (!longLabel.isNullOrEmpty()
                && paint.measureText(longLabel.toString()) <= availableWidth)
        text = if (usingLongLabel) longLabel else detail!!.shortLabel

        // TODO: Add the click handler to this view directly and not the child view.
        setOnClickListener(ItemClickHandler.clickListener)
        setOnLongClickListener(container)
        setOnTouchListener(container)
    }

    /**
     * Returns the shortcut info that is suitable to be added on the homescreen
     */
    val finalInfo: ShortcutInfo
        get() {
            val badged = ShortcutInfo(info)
            // Queue an update task on the worker thread. This ensures that the badged
            // shortcut eventually gets its icon updated.
            Launcher.getLauncher(context).model
                    .updateAndBindShortcutInfo(badged, detail)
            return badged
        }
}