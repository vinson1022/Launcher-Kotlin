package com.android.launcher3.util

/**
 * Base class which represents an area on the grid.
 */
open class CellAndSpan(
        /**
         * Indicates the X position of the associated cell.
         */
        var cellX: Int = -1,
        /**
         * Indicates the Y position of the associated cell.
         */
        var cellY: Int = -1,
        /**
         * Indicates the X cell span.
         */
        var spanX: Int = 1,
        /**
         * Indicates the Y cell span.
         */
        var spanY: Int = 1
) {

    fun copyFrom(copy: CellAndSpan) {
        cellX = copy.cellX
        cellY = copy.cellY
        spanX = copy.spanX
        spanY = copy.spanY
    }

    override fun toString(): String {
        return "($cellX, $cellY: $spanX, $spanY)"
    }
}