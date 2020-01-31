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
package com.android.launcher3.util

import android.util.Log
import android.view.KeyEvent
import android.view.KeyEvent.*
import android.view.View
import android.view.ViewGroup
import com.android.launcher3.CellLayout
import com.android.launcher3.DeviceProfile
import com.android.launcher3.ShortcutAndWidgetContainer
import com.android.launcher3.config.FeatureFlags
import java.util.*

/**
 * Calculates the next item that a [KeyEvent] should change the focus to.
 *
 *
 * Note, this utility class calculates everything regards to icon index and its (x,y) coordinates.
 * Currently supports:
 *
 *  *  full matrix of cells that are 1x1
 *  *  sparse matrix of cells that are 1x1
 * [ 1][  ][ 2][  ]
 * [  ][  ][ 3][  ]
 * [  ][ 4][  ][  ]
 * [  ][ 5][ 6][ 7]
 *
 * *
 *
 *
 * For testing, one can use a BT keyboard, or use following adb command.
 * ex. $ adb shell input keyevent 20 // KEYCODE_DPAD_LEFT
 */
object FocusLogic {

    private const val TAG = "FocusLogic"
    private const val DEBUG = false
    /** Item and page index related constant used by [.handleKeyEvent].  */
    const val NOOP = -1
    const val PREVIOUS_PAGE_RIGHT_COLUMN = -2
    const val PREVIOUS_PAGE_FIRST_ITEM = -3
    const val PREVIOUS_PAGE_LAST_ITEM = -4
    const val PREVIOUS_PAGE_LEFT_COLUMN = -5
    const val CURRENT_PAGE_FIRST_ITEM = -6
    const val CURRENT_PAGE_LAST_ITEM = -7
    const val NEXT_PAGE_FIRST_ITEM = -8
    const val NEXT_PAGE_LEFT_COLUMN = -9
    const val NEXT_PAGE_RIGHT_COLUMN = -10
    private const val ALL_APPS_COLUMN = -11
    // Matrix related constant.
    private const val EMPTY = -1
    const val PIVOT = 100
    /**
     * Returns true only if this utility class handles the key code.
     */
    @JvmStatic
    fun shouldConsume(keyCode: Int): Boolean {
        return keyCode == KEYCODE_DPAD_LEFT || keyCode == KEYCODE_DPAD_RIGHT
                || keyCode == KEYCODE_DPAD_UP || keyCode == KEYCODE_DPAD_DOWN
                || keyCode == KEYCODE_MOVE_HOME || keyCode == KEYCODE_MOVE_END
                || keyCode == KEYCODE_PAGE_UP || keyCode == KEYCODE_PAGE_DOWN
    }

    @JvmStatic
    fun handleKeyEvent(keyCode: Int, map: Array<IntArray>?, iconIdx: Int, pageIndex: Int,
                       pageCount: Int, isRtl: Boolean): Int {
        val cntX = map?.size ?: -1
        val cntY = if (map == null) -1 else map[0].size
        if (DEBUG) {
            Log.v(TAG, "handleKeyEvent START: cntX=$cntX, cntY=$cntY, iconIdx=$iconIdx, pageIdx=$pageIndex, pageCnt=$pageCount")
        }
        var newIndex = NOOP
        when (keyCode) {
            KEYCODE_DPAD_LEFT -> {
                newIndex = handleDpadHorizontal(iconIdx, cntX, cntY, map, -1, isRtl)
                if (!isRtl && newIndex == NOOP && pageIndex > 0) {
                    newIndex = PREVIOUS_PAGE_RIGHT_COLUMN
                } else if (isRtl && newIndex == NOOP && pageIndex < pageCount - 1) {
                    newIndex = NEXT_PAGE_RIGHT_COLUMN
                }
            }
            KEYCODE_DPAD_RIGHT -> {
                newIndex = handleDpadHorizontal(iconIdx, cntX, cntY, map, 1, isRtl)
                if (!isRtl && newIndex == NOOP && pageIndex < pageCount - 1) {
                    newIndex = NEXT_PAGE_LEFT_COLUMN
                } else if (isRtl && newIndex == NOOP && pageIndex > 0) {
                    newIndex = PREVIOUS_PAGE_LEFT_COLUMN
                }
            }
            KEYCODE_DPAD_DOWN -> newIndex = handleDpadVertical(iconIdx, cntX, cntY, map, 1)
            KEYCODE_DPAD_UP -> newIndex = handleDpadVertical(iconIdx, cntX, cntY, map, -1)
            KEYCODE_MOVE_HOME -> newIndex = handleMoveHome()
            KEYCODE_MOVE_END -> newIndex = handleMoveEnd()
            KEYCODE_PAGE_DOWN -> newIndex = handlePageDown(pageIndex, pageCount)
            KEYCODE_PAGE_UP -> newIndex = handlePageUp(pageIndex)
            else -> {
            }
        }
        if (DEBUG) {
            Log.v(TAG, "handleKeyEvent FINISH: index [$iconIdx -> ${getStringIndex(newIndex)}]")
        }
        return newIndex
    }

    /**
     * Returns a matrix of size (m x n) that has been initialized with [.EMPTY].
     *
     * @param m                 number of columns in the matrix
     * @param n                 number of rows in the matrix
     */
    // TODO: get rid of dynamic matrix creation.
    private fun createFullMatrix(m: Int, n: Int): Array<IntArray> {
        val matrix = Array(m) { IntArray(n) }
        for (i in 0 until m) {
            Arrays.fill(matrix[i], EMPTY)
        }
        return matrix
    }

    /**
     * Returns a matrix of size same as the [CellLayout] dimension that is initialized with the
     * index of the child view.
     */
    // TODO: get rid of the dynamic matrix creation
    @JvmStatic
    fun createSparseMatrix(layout: CellLayout): Array<IntArray> {
        val parent = layout.shortcutsAndWidgets
        val m = layout.countX
        val n = layout.countY
        val invert = parent.invertLayoutHorizontally()
        val matrix = createFullMatrix(m, n)
        // Iterate thru the children.
        for (i in 0 until parent.childCount) {
            val cell = parent.getChildAt(i)
            if (!cell.isFocusable) {
                continue
            }
            val cx = (cell.layoutParams as CellLayout.LayoutParams).cellX
            val cy = (cell.layoutParams as CellLayout.LayoutParams).cellY
            val x = if (invert) m - cx - 1 else cx
            if (x < m && cy < n) { // check if view fits into matrix, else skip
                matrix[x][cy] = i
            }
        }
        if (DEBUG) {
            printMatrix(matrix)
        }
        return matrix
    }

    /**
     * Creates a sparse matrix that merges the icon and hotseat view group using the cell layout.
     * The size of the returning matrix is [icon column count x (icon + hotseat row count)]
     * in portrait orientation. In landscape, [(icon + hotseat) column count x (icon row count)]
     */
    // TODO: get rid of the dynamic matrix creation
    @JvmStatic
    fun createSparseMatrixWithHotseat(
            iconLayout: CellLayout, hotseatLayout: CellLayout, dp: DeviceProfile): Array<IntArray> {
        val iconParent: ViewGroup = iconLayout.shortcutsAndWidgets
        val hotseatParent: ViewGroup = hotseatLayout.shortcutsAndWidgets
        val isHotseatHorizontal = !dp.isVerticalBarLayout
        val moreIconsInHotseatThanWorkspace = !FeatureFlags.NO_ALL_APPS_ICON &&
                if (isHotseatHorizontal) hotseatLayout.countX > iconLayout.countX else hotseatLayout.countY > iconLayout.countY
        val m: Int
        val n: Int
        if (isHotseatHorizontal) {
            m = hotseatLayout.countX
            n = iconLayout.countY + hotseatLayout.countY
        } else {
            m = iconLayout.countX + hotseatLayout.countX
            n = hotseatLayout.countY
        }
        val matrix = createFullMatrix(m, n)
        if (moreIconsInHotseatThanWorkspace) {
            val allappsiconRank = dp.inv.allAppsButtonRank
            if (isHotseatHorizontal) {
                for (j in 0 until n) {
                    matrix[allappsiconRank][j] = ALL_APPS_COLUMN
                }
            } else {
                for (j in 0 until m) {
                    matrix[j][allappsiconRank] = ALL_APPS_COLUMN
                }
            }
        }
        // Iterate thru the children of the workspace.
        for (i in 0 until iconParent.childCount) {
            val cell = iconParent.getChildAt(i)
            if (!cell.isFocusable) {
                continue
            }
            var cx = (cell.layoutParams as CellLayout.LayoutParams).cellX
            var cy = (cell.layoutParams as CellLayout.LayoutParams).cellY
            if (moreIconsInHotseatThanWorkspace) {
                val allappsiconRank = dp.inv.allAppsButtonRank
                if (isHotseatHorizontal && cx >= allappsiconRank) { // Add 1 to account for the All Apps button.
                    cx++
                }
                if (!isHotseatHorizontal && cy >= allappsiconRank) { // Add 1 to account for the All Apps button.
                    cy++
                }
            }
            matrix[cx][cy] = i
        }
        // Iterate thru the children of the hotseat.
        for (i in hotseatParent.childCount - 1 downTo 0) {
            if (isHotseatHorizontal) {
                val cx = (hotseatParent.getChildAt(i).layoutParams as CellLayout.LayoutParams).cellX
                matrix[cx][iconLayout.countY] = iconParent.childCount + i
            } else {
                val cy = (hotseatParent.getChildAt(i).layoutParams as CellLayout.LayoutParams).cellY
                matrix[iconLayout.countX][cy] = iconParent.childCount + i
            }
        }
        if (DEBUG) {
            printMatrix(matrix)
        }
        return matrix
    }

    /**
     * Creates a sparse matrix that merges the icon of previous/next page and last column of
     * current page. When left key is triggered on the leftmost column, sparse matrix is created
     * that combines previous page matrix and an extra column on the right. Likewise, when right
     * key is triggered on the rightmost column, sparse matrix is created that combines this column
     * on the 0th column and the next page matrix.
     *
     * @param pivotX    x coordinate of the focused item in the current page
     * @param pivotY    y coordinate of the focused item in the current page
     */
    // TODO: get rid of the dynamic matrix creation
    @JvmStatic
    fun createSparseMatrixWithPivotColumn(iconLayout: CellLayout,
                                          pivotX: Int, pivotY: Int): Array<IntArray> {
        val iconParent: ViewGroup = iconLayout.shortcutsAndWidgets
        val matrix = createFullMatrix(iconLayout.countX + 1, iconLayout.countY)
        // Iterate thru the children of the top parent.
        for (i in 0 until iconParent.childCount) {
            val cell = iconParent.getChildAt(i)
            if (!cell.isFocusable) {
                continue
            }
            val cx = (cell.layoutParams as CellLayout.LayoutParams).cellX
            val cy = (cell.layoutParams as CellLayout.LayoutParams).cellY
            if (pivotX < 0) {
                matrix[cx - pivotX][cy] = i
            } else {
                matrix[cx][cy] = i
            }
        }
        if (pivotX < 0) {
            matrix[0][pivotY] = PIVOT
        } else {
            matrix[pivotX][pivotY] = PIVOT
        }
        if (DEBUG) {
            printMatrix(matrix)
        }
        return matrix
    }

    //
    // key event handling methods.
    //
    /**
     * Calculates icon that has is closest to the horizontal axis in reference to the cur icon.
     *
     * Example of the check order for KEYCODE_DPAD_RIGHT:
     * [  ][  ][13][14][15]
     * [  ][ 6][ 8][10][12]
     * [ X][ 1][ 2][ 3][ 4]
     * [  ][ 5][ 7][ 9][11]
     */
    // TODO: add unit tests to verify all permutation.
    private fun handleDpadHorizontal(iconIdx: Int, cntX: Int, cntY: Int,
                                     matrix: Array<IntArray>?, increment: Int, isRtl: Boolean): Int {
        checkNotNull(matrix) { "Dpad navigation requires a matrix." }
        var newIconIndex = NOOP
        var xPos = -1
        var yPos = -1
        // Figure out the location of the icon.
        for (i in 0 until cntX) {
            for (j in 0 until cntY) {
                if (matrix[i][j] == iconIdx) {
                    xPos = i
                    yPos = j
                }
            }
        }
        if (DEBUG) {
            Log.v(TAG, "\thandleDpadHorizontal: \t[x, y]=[$xPos, $yPos] iconIndex=$iconIdx")
        }
        // Rule1: check first in the horizontal direction
        run {
            var x = xPos + increment
            while (x in 0 until cntX) {
                if (inspectMatrix(x, yPos, cntX, cntY, matrix).also { newIconIndex = it } != NOOP
                        && newIconIndex != ALL_APPS_COLUMN) {
                    return newIconIndex
                }
                x += increment
            }
        }
        // Rule2: check (x1-n, yPos + increment),   (x1-n, yPos - increment)
        //              (x2-n, yPos + 2*increment), (x2-n, yPos - 2*increment)
        var nextYPos1: Int
        var nextYPos2: Int
        var haveCrossedAllAppsColumn1 = false
        var haveCrossedAllAppsColumn2 = false
        var x: Int
        for (coeff in 1 until cntY) {
            nextYPos1 = yPos + coeff * increment
            nextYPos2 = yPos - coeff * increment
            x = xPos + increment * coeff
            if (inspectMatrix(x, nextYPos1, cntX, cntY, matrix) == ALL_APPS_COLUMN) {
                haveCrossedAllAppsColumn1 = true
            }
            if (inspectMatrix(x, nextYPos2, cntX, cntY, matrix) == ALL_APPS_COLUMN) {
                haveCrossedAllAppsColumn2 = true
            }
            while (x in 0 until cntX) {
                val offset1 = if (haveCrossedAllAppsColumn1 && x < cntX - 1) increment else 0
                newIconIndex = inspectMatrix(x, nextYPos1 + offset1, cntX, cntY, matrix)
                if (newIconIndex != NOOP) {
                    return newIconIndex
                }
                val offset2 = if (haveCrossedAllAppsColumn2 && x < cntX - 1) -increment else 0
                newIconIndex = inspectMatrix(x, nextYPos2 + offset2, cntX, cntY, matrix)
                if (newIconIndex != NOOP) {
                    return newIconIndex
                }
                x += increment
            }
        }
        // Rule3: if switching between pages, do a brute-force search to find an item that was
        //        missed by rules 1 and 2 (such as when going from a bottom right icon to top left)
        if (iconIdx == PIVOT) {
            if (isRtl) {
                return if (increment < 0) NEXT_PAGE_FIRST_ITEM else PREVIOUS_PAGE_LAST_ITEM
            }
            return if (increment < 0) PREVIOUS_PAGE_LAST_ITEM else NEXT_PAGE_FIRST_ITEM
        }
        return newIconIndex
    }

    /**
     * Calculates icon that is closest to the vertical axis in reference to the current icon.
     *
     * Example of the check order for KEYCODE_DPAD_DOWN:
     * [  ][  ][  ][ X][  ][  ][  ]
     * [  ][  ][ 5][ 1][ 4][  ][  ]
     * [  ][10][ 7][ 2][ 6][ 9][  ]
     * [14][12][ 9][ 3][ 8][11][13]
     */
    // TODO: add unit tests to verify all permutation.
    private fun handleDpadVertical(iconIndex: Int, cntX: Int, cntY: Int,
                                   matrix: Array<IntArray>?, increment: Int): Int {
        var newIconIndex = NOOP
        checkNotNull(matrix) { "Dpad navigation requires a matrix." }
        var xPos = -1
        var yPos = -1
        // Figure out the location of the icon.
        for (i in 0 until cntX) {
            for (j in 0 until cntY) {
                if (matrix[i][j] == iconIndex) {
                    xPos = i
                    yPos = j
                }
            }
        }
        if (DEBUG) {
            Log.v(TAG, "\thandleDpadVertical: \t[x, y]=[$xPos, $yPos] iconIndex=$iconIndex")
        }
        // Rule1: check first in the dpad direction
        run {
            var y = yPos + increment
            while (y in 0 until cntY && 0 <= y) {
                if (inspectMatrix(xPos, y, cntX, cntY, matrix).also { newIconIndex = it } != NOOP
                        && newIconIndex != ALL_APPS_COLUMN) {
                    return newIconIndex
                }
                y += increment
            }
        }
        // Rule2: check (xPos + increment, y_(1-n)),   (xPos - increment, y_(1-n))
        //              (xPos + 2*increment, y_(2-n))), (xPos - 2*increment, y_(2-n))
        var nextXPos1: Int
        var nextXPos2: Int
        var haveCrossedAllAppsColumn1 = false
        var haveCrossedAllAppsColumn2 = false
        var y: Int
        for (coeff in 1 until cntX) {
            nextXPos1 = xPos + coeff * increment
            nextXPos2 = xPos - coeff * increment
            y = yPos + increment * coeff
            if (inspectMatrix(nextXPos1, y, cntX, cntY, matrix) == ALL_APPS_COLUMN) {
                haveCrossedAllAppsColumn1 = true
            }
            if (inspectMatrix(nextXPos2, y, cntX, cntY, matrix) == ALL_APPS_COLUMN) {
                haveCrossedAllAppsColumn2 = true
            }
            while (y in 0 until cntY) {
                val offset1 = if (haveCrossedAllAppsColumn1 && y < cntY - 1) increment else 0
                newIconIndex = inspectMatrix(nextXPos1 + offset1, y, cntX, cntY, matrix)
                if (newIconIndex != NOOP) {
                    return newIconIndex
                }
                val offset2 = if (haveCrossedAllAppsColumn2 && y < cntY - 1) -increment else 0
                newIconIndex = inspectMatrix(nextXPos2 + offset2, y, cntX, cntY, matrix)
                if (newIconIndex != NOOP) {
                    return newIconIndex
                }
                y += increment
            }
        }
        return newIconIndex
    }

    private fun handleMoveHome(): Int {
        return CURRENT_PAGE_FIRST_ITEM
    }

    private fun handleMoveEnd(): Int {
        return CURRENT_PAGE_LAST_ITEM
    }

    private fun handlePageDown(pageIndex: Int, pageCount: Int): Int {
        return if (pageIndex < pageCount - 1) {
            NEXT_PAGE_FIRST_ITEM
        } else CURRENT_PAGE_LAST_ITEM
    }

    private fun handlePageUp(pageIndex: Int): Int {
        return if (pageIndex > 0) {
            PREVIOUS_PAGE_FIRST_ITEM
        } else {
            CURRENT_PAGE_FIRST_ITEM
        }
    }

    //
    // Helper methods.
    //
    private fun isValid(xPos: Int, yPos: Int, countX: Int, countY: Int): Boolean {
        return xPos in 0 until countX && yPos in 0 until countY
    }

    private fun inspectMatrix(x: Int, y: Int, cntX: Int, cntY: Int, matrix: Array<IntArray>): Int {
        var newIconIndex = NOOP
        if (isValid(x, y, cntX, cntY)) {
            if (matrix[x][y] != -1) {
                newIconIndex = matrix[x][y]
                if (DEBUG) {
                    Log.v(TAG, "\t\tinspect: \t[x, y]=[$x, $y] ${matrix[x][y]}")
                }
                return newIconIndex
            }
        }
        return newIconIndex
    }

    /**
     * Only used for debugging.
     */
    private fun getStringIndex(index: Int): String {
        return when (index) {
            NOOP -> "NOOP"
            PREVIOUS_PAGE_FIRST_ITEM -> "PREVIOUS_PAGE_FIRST"
            PREVIOUS_PAGE_LAST_ITEM -> "PREVIOUS_PAGE_LAST"
            PREVIOUS_PAGE_RIGHT_COLUMN -> "PREVIOUS_PAGE_RIGHT_COLUMN"
            CURRENT_PAGE_FIRST_ITEM -> "CURRENT_PAGE_FIRST"
            CURRENT_PAGE_LAST_ITEM -> "CURRENT_PAGE_LAST"
            NEXT_PAGE_FIRST_ITEM -> "NEXT_PAGE_FIRST"
            NEXT_PAGE_LEFT_COLUMN -> "NEXT_PAGE_LEFT_COLUMN"
            ALL_APPS_COLUMN -> "ALL_APPS_COLUMN"
            else -> index.toString()
        }
    }

    /**
     * Only used for debugging.
     */
    private fun printMatrix(matrix: Array<IntArray>) {
        Log.v(TAG, "\tprintMap:")
        val m = matrix.size
        val n: Int = matrix[0].size
        for (j in 0 until n) {
            var colY = "\t\t"
            for (i in 0 until m) {
                colY += String.format("%3d", matrix[i][j])
            }
            Log.v(TAG, colY)
        }
    }

    /**
     * @param edgeColumn the column of the new icon. either [.NEXT_PAGE_LEFT_COLUMN] or
     * [.NEXT_PAGE_RIGHT_COLUMN]
     * @return the view adjacent to {@param oldView} in the {@param nextPage} of the folder.
     */
    @JvmStatic
    fun getAdjacentChildInNextFolderPage(
            nextPage: ShortcutAndWidgetContainer, oldView: View, edgeColumn: Int): View? {
        val newRow = (oldView.layoutParams as CellLayout.LayoutParams).cellY
        var column = if ((edgeColumn == NEXT_PAGE_LEFT_COLUMN) xor nextPage.invertLayoutHorizontally()) 0 else (nextPage.parent as CellLayout).countX - 1
        while (column >= 0) {
            for (row in newRow downTo 0) {
                val newView = nextPage.getChildAt(column, row)
                if (newView != null) {
                    return newView
                }
            }
            column--
        }
        return null
    }
}