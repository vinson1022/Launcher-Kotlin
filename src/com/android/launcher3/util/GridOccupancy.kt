package com.android.launcher3.util

import android.graphics.Rect
import com.android.launcher3.ItemInfo

/**
 * Utility object to manage the occupancy in a grid.
 */
class GridOccupancy(private val countX: Int, private val countY: Int) {
    @JvmField
    val cells = Array(countX) { BooleanArray(countY) }

    /**
     * Find the first vacant cell, if there is one.
     *
     * @param vacantOut Holds the x and y coordinate of the vacant cell
     * @param spanX Horizontal cell span.
     * @param spanY Vertical cell span.
     *
     * @return true if a vacant cell was found
     */
    fun findVacantCell(vacantOut: IntArray, spanX: Int, spanY: Int): Boolean {
        var y = 0
        while (y + spanY <= countY) {
            var x = 0
            while (x + spanX <= countX) {
                var available = !cells[x][y]
                out@ for (i in x until x + spanX) {
                    for (j in y until y + spanY) {
                        available = available && !cells[i][j]
                        if (!available) break@out
                    }
                }
                if (available) {
                    vacantOut[0] = x
                    vacantOut[1] = y
                    return true
                }
                x++
            }
            y++
        }
        return false
    }

    fun copyTo(dest: GridOccupancy) {
        for (i in 0 until countX) {
            for (j in 0 until countY) {
                dest.cells[i][j] = cells[i][j]
            }
        }
    }

    fun isRegionVacant(x: Int, y: Int, spanX: Int, spanY: Int): Boolean {
        val xEnd = x + spanX - 1
        val yEnd = y + spanY - 1
        if (x < 0 || y < 0 || xEnd >= countX || yEnd >= countY) return false

        for (i in x..xEnd) {
            for (j in y..yEnd) {
                if (cells[i][j]) {
                    return false
                }
            }
        }
        return true
    }

    fun markCells(cellX: Int, cellY: Int, spanX: Int, spanY: Int, value: Boolean) {
        if (cellX < 0 || cellY < 0) return
        var x = cellX
        while (x < cellX + spanX && x < countX) {
            var y = cellY
            while (y < cellY + spanY && y < countY) {
                cells[x][y] = value
                y++
            }
            x++
        }
    }

    fun Rect.markCells(value: Boolean) {
        markCells(left, top, width(), height(), value)
    }

    fun CellAndSpan.markCells(value: Boolean) {
        markCells(cellX, cellY, spanX, spanY, value)
    }

    fun ItemInfo.markCells(value: Boolean) {
        markCells(cellX, cellY, spanX, spanY, value)
    }

    fun clear() {
        markCells(0, 0, countX, countY, false)
    }

}