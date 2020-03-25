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

import android.support.v7.widget.RecyclerView
import android.util.Log
import com.android.launcher3.IconCache
import com.android.launcher3.model.PackageItemInfo
import com.android.launcher3.widget.WidgetsListAdapter.WidgetListRowEntryComparator
import java.util.*

/**
 * Do diff on widget's tray list items and call the [RecyclerView.Adapter]
 * methods accordingly.
 */
class WidgetsDiffReporter(private val iconCache: IconCache, private val listener: RecyclerView.Adapter<*>) {

    fun process(currentEntries: ArrayList<WidgetListRowEntry>,
                newEntries: ArrayList<WidgetListRowEntry>, comparator: WidgetListRowEntryComparator) {
        if (DEBUG) {
            Log.d(TAG, "process oldEntries#=${currentEntries.size} newEntries#=${newEntries.size}")
        }
        // Early exit if either of the list is empty
        if (currentEntries.isEmpty() || newEntries.isEmpty()) {
            // Skip if both list are empty.
            // On rotation, we open the widget tray with empty. Then try to fetch the list again
            // when the animation completes (which still gives empty). And we get the final result
            // when the bind actually completes.
            if (currentEntries.size != newEntries.size) {
                currentEntries.clear()
                currentEntries.addAll(newEntries)
                listener.notifyDataSetChanged()
            }
            return
        }
        val orgEntries = currentEntries.clone() as ArrayList<WidgetListRowEntry>
        val orgIter: Iterator<WidgetListRowEntry> = orgEntries.iterator()
        val newIter: Iterator<WidgetListRowEntry> = newEntries.iterator()
        var orgRowEntry: WidgetListRowEntry? = orgIter.next()
        var newRowEntry: WidgetListRowEntry? = newIter.next()
        do {
            val diff = comparePackageName(orgRowEntry, newRowEntry, comparator)
            if (DEBUG) {
                Log.d(TAG, String.format("diff=%d orgRowEntry (%s) newRowEntry (%s)",
                        diff, orgRowEntry?.toString(), newRowEntry?.toString()))
            }
            var index: Int
            when {
                diff < 0 -> {
                    index = currentEntries.indexOf(orgRowEntry)
                    listener.notifyItemRemoved(index)
                    if (DEBUG) {
                        Log.d(TAG, String.format("notifyItemRemoved called (%d)%s", index,
                                orgRowEntry!!.titleSectionName))
                    }
                    currentEntries.removeAt(index)
                    orgRowEntry = if (orgIter.hasNext()) orgIter.next() else null
                }
                diff > 0 -> {
                    index = if (orgRowEntry != null) currentEntries.indexOf(orgRowEntry) else currentEntries.size
                    newRowEntry?.apply { currentEntries.add(index, this) }
                    if (DEBUG) {
                        Log.d(TAG, String.format("notifyItemInserted called (%d)%s", index,
                                newRowEntry!!.titleSectionName))
                    }
                    newRowEntry = if (newIter.hasNext()) newIter.next() else null
                    listener.notifyItemInserted(index)
                }
                else -> {
                    // same package name but,
                    // did the icon, title, etc, change?
                    // or did the widget size and desc, span, etc change?
                    if (!isSamePackageItemInfo(orgRowEntry!!.pkgItem, newRowEntry!!.pkgItem) ||
                            orgRowEntry.widgets != newRowEntry.widgets) {
                        index = currentEntries.indexOf(orgRowEntry)
                        currentEntries[index] = newRowEntry
                        listener.notifyItemChanged(index)
                        if (DEBUG) {
                            Log.d(TAG, String.format("notifyItemChanged called (%d)%s", index,
                                    newRowEntry.titleSectionName))
                        }
                    }
                    orgRowEntry = if (orgIter.hasNext()) orgIter.next() else null
                    newRowEntry = if (newIter.hasNext()) newIter.next() else null
                }
            }
        } while (orgRowEntry != null || newRowEntry != null)
    }

    /**
     * Compare package name using the same comparator as in [WidgetsListAdapter].
     * Also handle null row pointers.
     */
    private fun comparePackageName(curRow: WidgetListRowEntry?, newRow: WidgetListRowEntry?,
                                   comparator: WidgetListRowEntryComparator): Int {
        check(!(curRow == null && newRow == null)) { "Cannot compare PackageItemInfo if both rows are null." }
        if (curRow == null && newRow != null) {
            return 1 // new row needs to be inserted
        } else if (curRow != null && newRow == null) {
            return -1 // old row needs to be deleted
        }
        return comparator.compare(curRow, newRow)
    }

    private fun isSamePackageItemInfo(curInfo: PackageItemInfo, newInfo: PackageItemInfo): Boolean {
        return curInfo.iconBitmap == newInfo.iconBitmap &&
                !iconCache.isDefaultIcon(curInfo.iconBitmap, curInfo.user)
    }

    companion object {
        private const val DEBUG = false
        private const val TAG = "WidgetsDiffReporter"
    }

}