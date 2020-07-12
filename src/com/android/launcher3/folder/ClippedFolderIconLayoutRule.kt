package com.android.launcher3.folder

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

internal class ClippedFolderIconLayoutRule {
    private val tmpPoint = FloatArray(2)
    private var availableSpace = 0f
    private var radius = 0f
    var iconSize = 0f
        private set
    private var isRtl = false
    private var baselineIconScale = 0f
    
    fun init(availableSpace: Int, intrinsicIconSize: Float, rtl: Boolean) {
        this.availableSpace = availableSpace.toFloat()
        radius = ITEM_RADIUS_SCALE_FACTOR * availableSpace / 2f
        iconSize = intrinsicIconSize
        isRtl = rtl
        baselineIconScale = availableSpace / (intrinsicIconSize * 1f)
    }

    fun computePreviewItemDrawingParams(index: Int, curNumItems: Int,
                                        params: PreviewItemDrawingParams?): PreviewItemDrawingParams {
        val totalScale = scaleForItem(curNumItems)
        val overlayAlpha = 0f
        when {
            index == EXIT_INDEX -> {
                // 0 1 * <-- Exit position (row 0, col 2)
                // 2 3
                getGridPosition(0, 2, tmpPoint)
            }
            index == ENTER_INDEX -> {
                // 0 1
                // 2 3 * <-- Enter position (row 1, col 2)
                getGridPosition(1, 2, tmpPoint)
            }
            index >= MAX_NUM_ITEMS_IN_PREVIEW -> {
                // Items beyond those displayed in the preview are animated to the center
                tmpPoint[1] = availableSpace / 2 - iconSize * totalScale / 2
                tmpPoint[0] = tmpPoint[1]
            }
            else -> {
                getPosition(index, curNumItems, tmpPoint)
            }
        }
        val transX = tmpPoint[0]
        val transY = tmpPoint[1]
        return params?.apply {
            update(transX, transY, totalScale)
            this.overlayAlpha = overlayAlpha
        } ?: PreviewItemDrawingParams(transX, transY, totalScale, overlayAlpha)
    }

    /**
     * Builds a grid based on the positioning of the items when there are
     * [.MAX_NUM_ITEMS_IN_PREVIEW] in the preview.
     *
     * Positions in the grid: 0 1  // 0 is row 0, col 1
     * 2 3  // 3 is row 1, col 1
     */
    private fun getGridPosition(row: Int, col: Int, result: FloatArray) {
        // We use position 0 and 3 to calculate the x and y distances between items.
        getPosition(0, 4, result)
        val left = result[0]
        val top = result[1]
        getPosition(3, 4, result)
        val dx = result[0] - left
        val dy = result[1] - top
        result[0] = left + col * dx
        result[1] = top + row * dy
    }

    private fun getPosition(index: Int, curNumItems: Int, result: FloatArray) {
        // The case of two items is homomorphic to the case of one.
        var index = index
        var curNumItems = curNumItems
        curNumItems = max(curNumItems, 2)

        // We model the preview as a circle of items starting in the appropriate piece of the
        // upper left quadrant (to achieve horizontal and vertical symmetry).
        var theta0: Double = if (isRtl) 0.toDouble() else Math.PI

        // In RTL we go counterclockwise
        val direction = if (isRtl) 1 else -1
        var thetaShift = 0.0
        if (curNumItems == 3) {
            thetaShift = Math.PI / 6
        } else if (curNumItems == 4) {
            thetaShift = Math.PI / 4
        }
        theta0 += direction * thetaShift

        // We want the items to appear in reading order. For the case of 1, 2 and 3 items, this
        // is natural for the circular model. With 4 items, however, we need to swap the 3rd and
        // 4th indices to achieve reading order.
        if (curNumItems == 4 && index == 3) {
            index = 2
        } else if (curNumItems == 4 && index == 2) {
            index = 3
        }

        // We bump the radius up between 0 and MAX_RADIUS_DILATION % as the number of items increase
        val radius = radius * (1 + MAX_RADIUS_DILATION * (curNumItems -
                MIN_NUM_ITEMS_IN_PREVIEW) / (MAX_NUM_ITEMS_IN_PREVIEW - MIN_NUM_ITEMS_IN_PREVIEW))
        val theta = theta0 + index * (2 * Math.PI / curNumItems) * direction
        val halfIconSize = iconSize * scaleForItem(curNumItems) / 2

        // Map the location along the circle, and offset the coordinates to represent the center
        // of the icon, and to be based from the top / left of the preview area. The y component
        // is inverted to match the coordinate system.
        result[0] = availableSpace / 2 + (radius * cos(theta) / 2).toFloat() - halfIconSize
        result[1] = availableSpace / 2 + (-radius * sin(theta) / 2).toFloat() - halfIconSize
    }

    fun scaleForItem(numItems: Int): Float {
        // Scale is determined by the number of items in the preview.
        val scale = when {
            numItems <= 2 -> {
                MAX_SCALE
            }
            numItems == 3 -> {
                (MAX_SCALE + MIN_SCALE) / 2
            }
            else -> {
                MIN_SCALE
            }
        }
        return scale * baselineIconScale
    }

    companion object {
        const val MAX_NUM_ITEMS_IN_PREVIEW = 4
        private const val MIN_NUM_ITEMS_IN_PREVIEW = 2
        private const val MIN_SCALE = 0.48f
        private const val MAX_SCALE = 0.58f
        private const val MAX_RADIUS_DILATION = 0.15f
        private const val ITEM_RADIUS_SCALE_FACTOR = 1.33f
        const val EXIT_INDEX = -2
        const val ENTER_INDEX = -3
    }
}