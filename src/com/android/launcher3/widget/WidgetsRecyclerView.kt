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
import android.graphics.Point
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.RecyclerView.OnItemTouchListener
import android.util.AttributeSet
import android.view.MotionEvent
import com.android.launcher3.BaseRecyclerView
import com.android.launcher3.R

/**
 * The widgets recycler view.
 */
class WidgetsRecyclerView
@JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : BaseRecyclerView(context, attrs, defStyleAttr), OnItemTouchListener {

    private var adapter: WidgetsListAdapter? = null
    private val scrollbarTop = resources.getDimensionPixelSize(R.dimen.dynamic_grid_edge_margin)
    private val fastScrollerOffset = Point()
    private var touchDownOnScroller = false

    init {
        // API 21 and below only support 3 parameter ctor.
        addOnItemTouchListener(this)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        // create a layout manager with Launcher's context so that scroll position
        // can be preserved during screen rotation.
        layoutManager = LinearLayoutManager(context)
    }

    override fun setAdapter(adapter: Adapter<*>?) {
        super.setAdapter(adapter)
        this.adapter = adapter as WidgetsListAdapter?
    }

    /**
     * Maps the touch (from 0..1) to the adapter position that should be visible.
     */
    override fun scrollToPositionAtProgress(touchFraction: Float): String {
        // Skip early if widgets are not bound.
        if (isModelNotReady) return ""

        // Stop the scroller if it is scrolling
        stopScroll()
        val rowCount = adapter!!.itemCount
        val pos = rowCount * touchFraction
        val availableScrollHeight = availableScrollHeight
        val layoutManager = layoutManager as LinearLayoutManager?
        layoutManager!!.scrollToPositionWithOffset(0, (-(availableScrollHeight * touchFraction)).toInt())
        val posInt = (if (touchFraction == 1f) pos - 1 else pos).toInt()
        return adapter!!.getSectionName(posInt)!!
    }

    /**
     * Updates the bounds for the scrollbar.
     */
    override fun onUpdateScrollbar(dy: Int) {
        // Skip early if widgets are not bound.
        if (isModelNotReady) return

        // Skip early if, there no child laid out in the container.
        val scrollY = currentScrollY
        if (scrollY < 0) {
            scroller.thumbOffsetY = -1
            return
        }
        synchronizeScrollBarThumbOffsetToViewScroll(scrollY, availableScrollHeight)
    }

    override fun getCurrentScrollY(): Int {
        // Skip early if widgets are not bound.
        if (isModelNotReady || childCount == 0) return -1
        val child = getChildAt(0)
        val rowIndex = getChildPosition(child)
        val y = child.measuredHeight * rowIndex
        val offset = layoutManager!!.getDecoratedTop(child)
        return paddingTop + y - offset
    }

    /**
     * Returns the available scroll height:
     * AvailableScrollHeight = Total height of the all items - last page height
     */
    override fun getAvailableScrollHeight(): Int {
        val child = getChildAt(0)
        return (child.measuredHeight * adapter!!.itemCount - scrollbarTrackHeight
                - scrollbarTop)
    }

    private val isModelNotReady: Boolean
        get() = adapter!!.itemCount == 0

    override fun getScrollBarTop() = scrollbarTop

    override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN) {
            touchDownOnScroller = scroller.isHitInParent(e.x, e.y, fastScrollerOffset)
        }
        return if (touchDownOnScroller) {
            scroller.handleTouchEvent(e, fastScrollerOffset)
        } else false
    }

    override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
        if (touchDownOnScroller) {
            scroller.handleTouchEvent(e, fastScrollerOffset)
        }
    }

    override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
}