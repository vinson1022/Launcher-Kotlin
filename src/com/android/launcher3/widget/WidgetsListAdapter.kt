/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.Context
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import com.android.launcher3.IconCache
import com.android.launcher3.R
import com.android.launcher3.WidgetPreviewLoader
import com.android.launcher3.util.LabelComparator
import java.util.*
import kotlin.math.max

/**
 * List view adapter for the widget tray.
 *
 *
 * Memory vs. Performance:
 * The less number of types of views are inserted into a [RecyclerView], the more recycling
 * happens and less memory is consumed. [.getItemViewType] was not overridden as there is
 * only a single type of view.
 */
class WidgetsListAdapter(
        context: Context,
        private val layoutInflater: LayoutInflater,
        private val widgetPreviewLoader: WidgetPreviewLoader,
        iconCache: IconCache,
        private val iconClickListener: View.OnClickListener,
        private val iconLongClickListener: OnLongClickListener
) : RecyclerView.Adapter<WidgetsRowViewHolder>() {

    private val indent = context.resources.getDimensionPixelSize(R.dimen.widget_section_indent)
    private val entries = ArrayList<WidgetListRowEntry>()
    private val diffReporter = WidgetsDiffReporter(iconCache, this)
    private var applyBitmapDeferred = false

    /**
     * Defers applying bitmap on all the [WidgetCell] in the {@param rv}
     *
     * @see WidgetCell.setApplyBitmapDeferred
     */
    fun setApplyBitmapDeferred(isDeferred: Boolean, rv: RecyclerView) {
        applyBitmapDeferred = isDeferred
        for (i in rv.childCount - 1 downTo 0) {
            val holder = rv.getChildViewHolder(rv.getChildAt(i)) as WidgetsRowViewHolder
            for (j in holder.cellContainer.childCount - 1 downTo 0) {
                val v = holder.cellContainer.getChildAt(j)
                if (v is WidgetCell) {
                    v.setApplyBitmapDeferred(applyBitmapDeferred)
                }
            }
        }
    }

    /**
     * Update the widget list.
     */
    fun setWidgets(tempEntries: ArrayList<WidgetListRowEntry>) {
        val rowComparator = WidgetListRowEntryComparator()
        Collections.sort(tempEntries, rowComparator)
        diffReporter.process(entries, tempEntries, rowComparator)
    }

    override fun getItemCount() = entries.size

    fun getSectionName(pos: Int) = entries[pos].titleSectionName

    override fun onBindViewHolder(holder: WidgetsRowViewHolder, pos: Int) {
        val entry = entries[pos]
        val infoList = entry.widgets
        val row = holder.cellContainer
        if (DEBUG) {
            Log.d(TAG, String.format(
                    "onBindViewHolder [pos=%d, widget#=%d, row.getChildCount=%d]",
                    pos, infoList.size, row.childCount))
        }

        // Add more views.
        // if there are too many, hide them.
        val expectedChildCount = infoList.size + max(0, infoList.size - 1)
        val childCount = row.childCount
        if (expectedChildCount > childCount) {
            for (i in childCount until expectedChildCount) {
                if (i and 1 == 1) {
                    // Add a divider for odd index
                    layoutInflater.inflate(R.layout.widget_list_divider, row)
                } else {
                    // Add cell for even index
                    val widget = layoutInflater.inflate(
                            R.layout.widget_cell, row, false) as WidgetCell

                    // set up touch.
                    widget.setOnClickListener(iconClickListener)
                    widget.setOnLongClickListener(iconLongClickListener)
                    row.addView(widget)
                }
            }
        } else if (expectedChildCount < childCount) {
            for (i in expectedChildCount until childCount) {
                row.getChildAt(i).visibility = View.GONE
            }
        }

        // Bind the views in the application info section.
        holder.title.applyFromPackageItemInfo(entry.pkgItem)

        // Bind the view in the widget horizontal tray region.
        for (i in infoList.indices) {
            val widget = row.getChildAt(2 * i) as WidgetCell
            widget.applyFromCellItem(infoList[i], widgetPreviewLoader)
            widget.setApplyBitmapDeferred(applyBitmapDeferred)
            widget.ensurePreview()
            widget.visibility = View.VISIBLE
            if (i > 0) {
                row.getChildAt(2 * i - 1).visibility = View.VISIBLE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetsRowViewHolder {
        if (DEBUG) {
            Log.v(TAG, "\nonCreateViewHolder")
        }
        val container = layoutInflater.inflate(
                R.layout.widgets_list_row_view, parent, false) as ViewGroup

        // if the end padding is 0, then container view (horizontal scroll view) doesn't respect
        // the end of the linear layout width + the start padding and doesn't allow scrolling.
        container.findViewById<View>(R.id.widgets_cell_list).setPaddingRelative(indent, 0, 1, 0)
        return WidgetsRowViewHolder(container)
    }

    override fun onViewRecycled(holder: WidgetsRowViewHolder) {
        val total = holder.cellContainer.childCount
        var i = 0
        while (i < total) {
            val widget = holder.cellContainer.getChildAt(i) as WidgetCell
            widget.clear()
            i += 2
        }
    }

    override fun onFailedToRecycleView(holder: WidgetsRowViewHolder): Boolean {
        // If child views are animating, then the RecyclerView may choose not to recycle the view,
        // causing extraneous onCreateViewHolder() calls.  It is safe in this case to continue
        // recycling this view, and take care in onViewRecycled() to cancel any existing
        // animations.
        return true
    }

    override fun getItemId(pos: Int) = pos.toLong()

    /**
     * Comparator for sorting WidgetListRowEntry based on package title
     */
    class WidgetListRowEntryComparator : Comparator<WidgetListRowEntry> {
        private val comparator = LabelComparator()
        override fun compare(a: WidgetListRowEntry, b: WidgetListRowEntry): Int {
            return comparator.compare(a.pkgItem.title.toString(), b.pkgItem.title.toString())
        }
    }

    companion object {
        private const val TAG = "WidgetsListAdapter"
        private const val DEBUG = false
    }
}