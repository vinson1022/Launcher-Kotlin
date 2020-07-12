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
package com.android.launcher3.widget

import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Pair
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.Insettable
import com.android.launcher3.ItemInfo
import com.android.launcher3.LauncherAppState.Companion.getInstance
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.anim.Interpolators
import com.android.launcher3.util.PackageUserKey
import kotlinx.android.synthetic.main.widgets_bottom_sheet.view.*

/**
 * Bottom sheet for the "Widgets" system shortcut in the long-press popup.
 */
open class WidgetsBottomSheet @JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int = 0
) : BaseWidgetSheet(context, attrs, defStyleAttr), Insettable {

    private var originalItemInfo: ItemInfo? = null
    private val insets = Rect()

    init {
        setWillNotDraw(false)
        content = this
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        setTranslationShift(_translationShift)
    }

    fun populateAndShow(itemInfo: ItemInfo?) {
        originalItemInfo = itemInfo
        title.text = context.getString(
                R.string.widgets_bottom_sheet_title, originalItemInfo!!.title)
        onWidgetsBound()
        launcher.dragLayer.addView(this)
        isOpen = false
        animateOpen()
    }

    override fun onWidgetsBound() {
        val widgets = with(originalItemInfo!!) {
            launcher.popupDataProvider.getWidgetsForPackageUser(PackageUserKey(targetComponent.packageName, user))
        }
        widgets ?: return
        val widgetRow = findViewById<ViewGroup>(R.id.widgets)
        val widgetCells = widgetRow.findViewById<ViewGroup>(R.id.widgets_cell_list)
        widgetCells.removeAllViews()
        for (i in widgets.indices) {
            val widget = addItemCell(widgetCells)
            widget.applyFromCellItem(widgets[i], getInstance(launcher)
                    .widgetCache)
            widget.ensurePreview()
            widget.visibility = View.VISIBLE
            if (i < widgets.size - 1) {
                addDivider(widgetCells)
            }
        }
        if (widgets.size == 1) {
            // If there is only one widget, we want to center it instead of left-align.
            val params = widgetRow.layoutParams as LayoutParams
            params.gravity = Gravity.CENTER_HORIZONTAL
        } else {
            // Otherwise, add an empty view to the start as padding (but still scroll edge to edge).
            val leftPaddingView = LayoutInflater.from(context).inflate(
                    R.layout.widget_list_divider, widgetRow, false)
            leftPaddingView.layoutParams.width = Utilities.pxFromDp(16f, resources.displayMetrics)
            widgetCells.addView(leftPaddingView, 0)
        }
    }

    private fun addDivider(parent: ViewGroup) {
        LayoutInflater.from(context).inflate(R.layout.widget_list_divider, parent, true)
    }

    private fun addItemCell(parent: ViewGroup): WidgetCell {
        val widget = LayoutInflater.from(context).inflate(
                R.layout.widget_cell, parent, false) as WidgetCell
        widget.setOnClickListener(this)
        widget.setOnLongClickListener(this)
        widget.setAnimatePreview(false)
        parent.addView(widget)
        return widget
    }

    private fun animateOpen() {
        if (isOpen || openCloseAnimator.isRunning) return

        isOpen = true
        setupNavBarColor()
        openCloseAnimator.setValues(PropertyValuesHolder.ofFloat(TRANSLATION_SHIFT, TRANSLATION_SHIFT_OPENED))
        openCloseAnimator.interpolator = Interpolators.FAST_OUT_SLOW_IN
        openCloseAnimator.start()
    }

    override fun handleClose(animate: Boolean) {
        handleClose(animate, DEFAULT_CLOSE_DURATION)
    }

    override fun isOfType(@FloatingViewType type: Int) = type and TYPE_WIDGETS_BOTTOM_SHEET != 0

    override fun setInsets(insets: Rect) {
        // Extend behind left, right, and bottom insets.
        val leftInset = insets.left - this.insets.left
        val rightInset = insets.right - this.insets.right
        val bottomInset = insets.bottom - this.insets.bottom
        this.insets.set(insets)
        setPadding(paddingLeft + leftInset, paddingTop,
                paddingRight + rightInset, paddingBottom + bottomInset)
    }

    override val elementsRowCount = 1

    override fun getAccessibilityTarget(): Pair<View, String> {
        return Pair.create(findViewById(R.id.title), context.getString(
                if (isOpen) R.string.widgets_list else R.string.widgets_list_closed))
    }

    companion object {
        private const val DEFAULT_CLOSE_DURATION = 200L
    }
}