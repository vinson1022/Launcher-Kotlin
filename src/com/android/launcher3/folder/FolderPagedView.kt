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
package com.android.launcher3.folder

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.util.ArrayMap
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewDebug.ExportedProperty
import com.android.launcher3.*
import com.android.launcher3.LauncherAppState.Companion.getIDP
import com.android.launcher3.Workspace.ItemOperator
import com.android.launcher3.anim.Interpolators
import com.android.launcher3.keyboard.ViewGroupFocusHelper
import com.android.launcher3.pageindicators.PageIndicatorDots
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.util.Thunk
import kotlinx.android.synthetic.main.user_folder_icon_normalized.view.*
import java.util.*
import kotlin.math.max
import kotlin.math.min

class FolderPagedView(context: Context, attrs: AttributeSet?) : PagedView<PageIndicatorDots>(context, attrs) {
    @JvmField
    val isRtl = Utilities.isRtl(resources)
    private val inflater = LayoutInflater.from(context)
    private val focusIndicatorHelper = ViewGroupFocusHelper(this)

    @Thunk
    val pendingAnimations = ArrayMap<View, Runnable>()

    @ExportedProperty(category = "launcher")
    private val maxCountX: Int

    @ExportedProperty(category = "launcher")
    private val maxCountY: Int

    @ExportedProperty(category = "launcher")
    private val maxItemsPerPage: Int
    var allocatedContentSize = 0
        private set

    @ExportedProperty(category = "launcher")
    private var gridCountX = 0

    @ExportedProperty(category = "launcher")
    private var gridCountY = 0
    private var folder: Folder? = null

    init {
        val profile = getIDP(context)
        maxCountX = profile.numFolderColumns
        maxCountY = profile.numFolderRows
        maxItemsPerPage = maxCountX * maxCountY
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setFolder(folder: Folder) {
        this.folder = folder
        mPageIndicator = folder.findViewById(R.id.pageIndicator)
        initParentViews(folder)
    }

    /**
     * Sets up the grid size such that {@param count} items can fit in the grid.
     */
    private fun setupContentDimensions(count: Int) {
        allocatedContentSize = count
        calculateGridSize(count, gridCountX, gridCountY, maxCountX, maxCountY, maxItemsPerPage,
                sTmpArray)
        gridCountX = sTmpArray[0]
        gridCountY = sTmpArray[1]

        // Update grid size
        for (i in pageCount - 1 downTo 0) {
            getPageAt(i).setGridSize(gridCountX, gridCountY)
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        focusIndicatorHelper.draw(canvas)
        super.dispatchDraw(canvas)
    }

    /**
     * Binds items to the layout.
     */
    fun bindItems(items: ArrayList<ShortcutInfo>) {
        val icons = items.map { createNewView(it) }
        arrangeChildren(icons, icons.size, false)
    }

    private fun allocateSpaceForRank(rank: Int) {
        val views = ArrayList(folder!!.itemsInReadingOrder)
        views.add(rank, null)
        arrangeChildren(views, views.size, false)
    }

    /**
     * Create space for a new item at the end, and returns the rank for that item.
     * Also sets the current page to the last page.
     */
    fun allocateRankForNewItem(): Int {
        val rank = itemCount
        allocateSpaceForRank(rank)
        currentPage = rank / maxItemsPerPage
        return rank
    }

    fun createAndAddViewForRank(item: ShortcutInfo, rank: Int): View {
        val icon = createNewView(item)
        allocateSpaceForRank(rank)
        addViewForRank(icon, item, rank)
        return icon
    }

    /**
     * Adds the {@param view} to the layout based on {@param rank} and updated the position
     * related attributes. It assumes that {@param item} is already attached to the view.
     */
    fun addViewForRank(view: View, item: ShortcutInfo, rank: Int) {
        val pagePos = rank % maxItemsPerPage
        val pageNo = rank / maxItemsPerPage
        item.rank = rank
        item.cellX = pagePos % gridCountX
        item.cellY = pagePos / gridCountX
        val lp = view.layoutParams as CellLayout.LayoutParams
        lp.cellX = item.cellX
        lp.cellY = item.cellY
        getPageAt(pageNo).addViewToCellLayout(
                view, -1, folder!!.launcher.getViewIdForItem(item), lp, true)
    }

    @SuppressLint("InflateParams")
    fun createNewView(item: ShortcutInfo): View {
        val textView = inflater.inflate(
                R.layout.folder_application, null, false) as BubbleTextView
        textView.applyFromShortcutInfo(item)
        textView.isHapticFeedbackEnabled = false
        textView.setOnClickListener(ItemClickHandler.clickListener)
        textView.setOnLongClickListener(folder)
        textView.onFocusChangeListener = focusIndicatorHelper
        textView.layoutParams = CellLayout.LayoutParams(
                item.cellX, item.cellY, item.spanX, item.spanY)
        return textView
    }

    override fun getPageAt(index: Int): CellLayout {
        return getChildAt(index) as CellLayout
    }

    val currentCellLayout: CellLayout
        get() = getPageAt(nextPage)

    private fun createAndAddNewPage(): CellLayout {
        val grid = Launcher.getLauncher(context).deviceProfile
        val page = inflater.inflate(R.layout.folder_page, this, false) as CellLayout
        page.setCellDimensions(grid.folderCellWidthPx, grid.folderCellHeightPx)
        page.shortcutsAndWidgets.isMotionEventSplittingEnabled = false
        page.setInvertIfRtl(true)
        page.setGridSize(gridCountX, gridCountY)
        addView(page, -1, generateDefaultLayoutParams())
        return page
    }

    override fun getChildGap(): Int {
        return paddingLeft + paddingRight
    }

    fun setFixedSize(width: Int, height: Int) {
        var w = width
        var h = height
        w -= paddingLeft + paddingRight
        h -= paddingTop + paddingBottom
        for (i in childCount - 1 downTo 0) {
            (getChildAt(i) as CellLayout).setFixedSize(w, h)
        }
    }

    fun removeItem(v: View?) {
        for (i in childCount - 1 downTo 0) {
            getPageAt(i).removeView(v)
        }
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        mPageIndicator!!.setScroll(l, mMaxScrollX)
    }

    /**
     * Updates position and rank of all the children in the view.
     * It essentially removes all views from all the pages and then adds them again in appropriate
     * page.
     *
     * @param list the ordered list of children.
     * @param itemCount if greater than the total children count, empty spaces are left
     * at the end, otherwise it is ignored.
     */
    fun arrangeChildren(list: List<View>, itemCount: Int) {
        arrangeChildren(list, itemCount, true)
    }

    @SuppressLint("RtlHardcoded")
    private fun arrangeChildren(list: List<View>, itemCount: Int, saveChanges: Boolean) {
        val pages = ArrayList<CellLayout>()
        for (i in 0 until childCount) {
            val page = getChildAt(i) as CellLayout
            page.removeAllViews()
            pages.add(page)
        }
        setupContentDimensions(itemCount)
        val pageItr: Iterator<CellLayout> = pages.iterator()
        var currentPage: CellLayout? = null
        var position = 0
        var newX: Int
        var newY: Int
        val verifier = FolderIconPreviewVerifier(
                Launcher.getLauncher(context).deviceProfile.inv)
        for ((rank, i) in (0 until itemCount).withIndex()) {
            val v = if (list.size > i) list[i] else null
            if (currentPage == null || position >= maxItemsPerPage) {
                // Next page
                currentPage = if (pageItr.hasNext()) {
                    pageItr.next()
                } else {
                    createAndAddNewPage()
                }
                position = 0
            }
            if (v != null) {
                val lp = v.layoutParams as CellLayout.LayoutParams
                newX = position % gridCountX
                newY = position / gridCountX
                val info = v.tag as ItemInfo
                if (info.cellX != newX || info.cellY != newY || info.rank != rank) {
                    info.cellX = newX
                    info.cellY = newY
                    info.rank = rank
                    if (saveChanges) {
                        folder!!.launcher.modelWriter.addOrMoveItemInDatabase(info,
                                folder!!.info.id, 0, info.cellX, info.cellY)
                    }
                }
                lp.cellX = info.cellX
                lp.cellY = info.cellY
                currentPage.addViewToCellLayout(
                        v, -1, folder!!.launcher.getViewIdForItem(info), lp, true)
                if (verifier.isItemInPreview(0, rank) && v is BubbleTextView) {
                    v.verifyHighRes()
                }
            }
            position++
        }

        // Remove extra views.
        var removed = false
        while (pageItr.hasNext()) {
            removeView(pageItr.next())
            removed = true
        }
        if (removed) {
            setCurrentPage(0)
        }
        setEnableOverscroll(pageCount > 1)

        // Update footer
        mPageIndicator!!.visibility = if (pageCount > 1) View.VISIBLE else View.GONE
        // Set the gravity as LEFT or RIGHT instead of START, as START depends on the actual text.
        folder!!.name.gravity = if (pageCount > 1) if (isRtl) Gravity.RIGHT else Gravity.LEFT else Gravity.CENTER_HORIZONTAL
    }

    val desiredWidth: Int
        get() = if (pageCount > 0) getPageAt(0).desiredWidth + paddingLeft + paddingRight else 0

    val desiredHeight: Int
        get() = if (pageCount > 0) getPageAt(0).desiredHeight + paddingTop + paddingBottom else 0

    // If there are no pages, nothing has yet been added to the folder.
    val itemCount: Int
        get() {
            val lastPageIndex = childCount - 1
            return if (lastPageIndex < 0) {
                // If there are no pages, nothing has yet been added to the folder.
                0
            } else {
                getPageAt(lastPageIndex).shortcutsAndWidgets.childCount + lastPageIndex * maxItemsPerPage
            }
        }

    /**
     * @return the rank of the cell nearest to the provided pixel position.
     */
    fun findNearestArea(pixelX: Int, pixelY: Int): Int {
        val pageIndex = nextPage
        val page = getPageAt(pageIndex)
        page.findNearestArea(pixelX, pixelY, 1, 1, sTmpArray)
        if (folder!!.isLayoutRtl) {
            sTmpArray[0] = page.countX - sTmpArray[0] - 1
        }
        return min(allocatedContentSize - 1,
                pageIndex * maxItemsPerPage + sTmpArray[1] * gridCountX + sTmpArray[0])
    }

    val firstItem: View?
        get() {
            if (childCount < 1) {
                return null
            }
            val currContainer = currentCellLayout.shortcutsAndWidgets
            return if (gridCountX > 0) {
                currContainer.getChildAt(0, 0)
            } else {
                currContainer.getChildAt(0)
            }
        }

    val lastItem: View?
        get() {
            if (childCount < 1) {
                return null
            }
            val currContainer = currentCellLayout.shortcutsAndWidgets
            val lastRank = currContainer.childCount - 1
            return if (gridCountX > 0) {
                currContainer.getChildAt(lastRank % gridCountX, lastRank / gridCountX)
            } else {
                currContainer.getChildAt(lastRank)
            }
        }

    /**
     * Iterates over all its items in a reading order.
     * @return the view for which the operator returned true.
     */
    fun iterateOverItems(op: ItemOperator): View? {
        for (k in 0 until childCount) {
            val page = getPageAt(k)
            for (j in 0 until page.countY) {
                for (i in 0 until page.countX) {
                    val v = page.getChildAt(i, j)
                    if (v != null && op.evaluate(v.tag as ItemInfo, v)) {
                        return v
                    }
                }
            }
        }
        return null
    }

    val accessibilityDescription: String
        get() = context.getString(R.string.folder_opened, gridCountX, gridCountY)

    /**
     * Sets the focus on the first visible child.
     */
    fun setFocusOnFirstChild() {
        val firstChild = currentCellLayout.getChildAt(0, 0)
        firstChild?.requestFocus()
    }

    override fun notifyPageSwitchListener(prevPage: Int) {
        super.notifyPageSwitchListener(prevPage)
        folder?.updateTextViewFocus()
    }

    /**
     * Scrolls the current view by a fraction
     */
    fun showScrollHint(direction: Int) {
        val fraction = if ((direction == Folder.SCROLL_LEFT) xor isRtl) -SCROLL_HINT_FRACTION else SCROLL_HINT_FRACTION
        val hint = (fraction * width).toInt()
        val scroll = getScrollForPage(nextPage) + hint
        val delta = scroll - scrollX
        if (delta != 0) {
            mScroller.setInterpolator(Interpolators.DEACCEL)
            mScroller.startScroll(scrollX, 0, delta, 0, Folder.SCROLL_HINT_DURATION.toInt())
            invalidate()
        }
    }

    fun clearScrollHint() {
        if (scrollX != getScrollForPage(nextPage)) {
            snapToPage(nextPage)
        }
    }

    /**
     * Finish animation all the views which are animating across pages
     */
    fun completePendingPageChanges() {
        if (!pendingAnimations.isEmpty()) {
            val pendingViews = ArrayMap(pendingAnimations)
            for ((key, value) in pendingViews) {
                key.animate().cancel()
                value.run()
            }
        }
    }

    fun rankOnCurrentPage(rank: Int): Boolean {
        val p = rank / maxItemsPerPage
        return p == nextPage
    }

    override fun onPageBeginTransition() {
        super.onPageBeginTransition()
        // Ensure that adjacent pages have high resolution icons
        verifyVisibleHighResIcons(currentPage - 1)
        verifyVisibleHighResIcons(currentPage + 1)
    }

    /**
     * Ensures that all the icons on the given page are of high-res
     */
    fun verifyVisibleHighResIcons(pageNo: Int) {
        val page = getPageAt(pageNo)
        val parent = page.shortcutsAndWidgets
        for (i in parent.childCount - 1 downTo 0) {
            val icon = parent.getChildAt(i) as BubbleTextView
            icon.verifyHighRes()
            // Set the callback back to the actual icon, in case
            // it was captured by the FolderIcon
            val d = icon.compoundDrawables[1]
            if (d != null) {
                d.callback = icon
            }
        }
    }

    /**
     * Reorders the items such that the {@param empty} spot moves to {@param target}
     */
    fun realTimeReorder(empty: Int, target: Int) {
        completePendingPageChanges()
        var delay = 0
        var delayAmount = START_VIEW_REORDER_DELAY.toFloat()

        // Animation only happens on the current page.
        val pageToAnimate = nextPage
        val pageT = target / maxItemsPerPage
        val pagePosT = target % maxItemsPerPage
        if (pageT != pageToAnimate) {
            Log.e(TAG, "Cannot animate when the target cell is invisible")
        }
        val pagePosE = empty % maxItemsPerPage
        val pageE = empty / maxItemsPerPage
        val startPos: Int
        val endPos: Int
        var moveStart: Int
        val moveEnd: Int
        val direction: Int
        when {
            target == empty -> {
                // No animation
                return
            }
            target > empty -> {
                // Items will move backwards to make room for the empty cell.
                direction = 1

                // If empty cell is in a different page, move them instantly.
                if (pageE < pageToAnimate) {
                    moveStart = empty
                    // Instantly move the first item in the current page.
                    moveEnd = pageToAnimate * maxItemsPerPage
                    // Animate the 2nd item in the current page, as the first item was already moved to
                    // the last page.
                    startPos = 0
                } else {
                    moveEnd = -1
                    moveStart = moveEnd
                    startPos = pagePosE
                }
                endPos = pagePosT
            }
            else -> {
                // The items will move forward.
                direction = -1
                if (pageE > pageToAnimate) {
                    // Move the items immediately.
                    moveStart = empty
                    // Instantly move the last item in the current page.
                    moveEnd = (pageToAnimate + 1) * maxItemsPerPage - 1

                    // Animations start with the second last item in the page
                    startPos = maxItemsPerPage - 1
                } else {
                    moveEnd = -1
                    moveStart = moveEnd
                    startPos = pagePosE
                }
                endPos = pagePosT
            }
        }

        // Instant moving views.
        while (moveStart != moveEnd) {
            val rankToMove = moveStart + direction
            val p = rankToMove / maxItemsPerPage
            val pagePos = rankToMove % maxItemsPerPage
            val x = pagePos % gridCountX
            val y = pagePos / gridCountX
            val page = getPageAt(p)
            val v = page.getChildAt(x, y)
            if (v != null) {
                if (pageToAnimate != p) {
                    page.removeView(v)
                    addViewForRank(v, v.tag as ShortcutInfo, moveStart)
                } else {
                    // Do a fake animation before removing it.
                    val newRank = moveStart
                    val oldTranslateX = v.translationX
                    val endAction = Runnable {
                        pendingAnimations.remove(v)
                        v.translationX = oldTranslateX
                        (v.parent.parent as CellLayout).removeView(v)
                        addViewForRank(v, v.tag as ShortcutInfo, newRank)
                    }
                    v.animate()
                            .translationXBy(if ((direction > 0) xor isRtl) -v.width.toFloat() else v.width.toFloat())
                            .setDuration(REORDER_ANIMATION_DURATION.toLong())
                            .setStartDelay(0)
                            .withEndAction(endAction)
                    pendingAnimations[v] = endAction
                }
            }
            moveStart = rankToMove
        }
        if ((endPos - startPos) * direction <= 0) {
            // No animation
            return
        }
        val page = getPageAt(pageToAnimate)
        var i = startPos
        while (i != endPos) {
            val nextPos = i + direction
            val v = page.getChildAt(nextPos % gridCountX, nextPos / gridCountX)
            if (v != null) {
                (v.tag as ItemInfo).rank -= direction
            }
            if (page.animateChildToPosition(v, i % gridCountX, i / gridCountX,
                            REORDER_ANIMATION_DURATION, delay, true, true)) {
                delay += delayAmount.toInt()
                delayAmount *= VIEW_REORDER_DELAY_FACTOR
            }
            i += direction
        }
    }

    fun itemsPerPage(): Int {
        return maxItemsPerPage
    }

    companion object {
        private const val TAG = "FolderPagedView"
        private const val REORDER_ANIMATION_DURATION = 230
        private const val START_VIEW_REORDER_DELAY = 30
        private const val VIEW_REORDER_DELAY_FACTOR = 0.9f

        /**
         * Fraction of the width to scroll when showing the next page hint.
         */
        private const val SCROLL_HINT_FRACTION = 0.07f
        private val sTmpArray = IntArray(2)

        /**
         * Calculates the grid size such that {@param count} items can fit in the grid.
         * The grid size is calculated such that countY <= countX and countX = ceil(sqrt(count)) while
         * maintaining the restrictions of [.mMaxCountX] &amp; [.mMaxCountY].
         */
        fun calculateGridSize(count: Int, countX: Int, countY: Int, maxCountX: Int,
                              maxCountY: Int, maxItemsPerPage: Int, out: IntArray) {
            var done: Boolean
            var gridCountX = countX
            var gridCountY = countY
            if (count >= maxItemsPerPage) {
                gridCountX = maxCountX
                gridCountY = maxCountY
                done = true
            } else {
                done = false
            }
            while (!done) {
                val oldCountX = gridCountX
                val oldCountY = gridCountY
                if (gridCountX * gridCountY < count) {
                    // Current grid is too small, expand it
                    if ((gridCountX <= gridCountY || gridCountY == maxCountY)
                            && gridCountX < maxCountX) {
                        gridCountX++
                    } else if (gridCountY < maxCountY) {
                        gridCountY++
                    }
                    if (gridCountY == 0) gridCountY++
                } else if ((gridCountY - 1) * gridCountX >= count && gridCountY >= gridCountX) {
                    gridCountY = max(0, gridCountY - 1)
                } else if ((gridCountX - 1) * gridCountY >= count) {
                    gridCountX = max(0, gridCountX - 1)
                }
                done = gridCountX == oldCountX && gridCountY == oldCountY
            }
            out[0] = gridCountX
            out[1] = gridCountY
        }
    }
}