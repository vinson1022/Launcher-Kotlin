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
package com.android.launcher3.folder

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.TextView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.ShortcutInfo
import com.android.launcher3.Utilities
import java.util.*
import kotlin.math.max

/**
 * Manages the drawing and animations of [PreviewItemDrawingParams] for a [FolderIcon].
 */
class PreviewItemManager(private val icon: FolderIcon) {

    // These variables are all associated with the drawing of the preview; they are stored
    // as member variables for shared usage and to avoid computation on each frame
    var intrinsicIconSize = -1f
        private set
    private var totalWidth = -1
    private var prevTopPadding = -1
    private var referenceDrawable: Drawable? = null

    // These hold the first page preview items
    private val firstPageParams = ArrayList<PreviewItemDrawingParams>()

    // These hold the current page preview items. It is empty if the current page is the first page.
    private val currentPageParams = ArrayList<PreviewItemDrawingParams>()
    private var currentPageItemsTransX = 0f
    private var shouldSlideInFirstPage = false

    /**
     * @param reverse If true, animates the final item in the preview to be full size. If false,
     * animates the first item to its position in the preview.
     */
    fun createFirstItemAnimation(reverse: Boolean,
                                 onCompleteRunnable: Runnable?): FolderPreviewItemAnim {
        return if (reverse) FolderPreviewItemAnim(this, firstPageParams[0], 0, 2, -1, -1,
                FINAL_ITEM_ANIMATION_DURATION, onCompleteRunnable) else FolderPreviewItemAnim(this, firstPageParams[0], -1, -1, 0, 2,
                INITIAL_ITEM_ANIMATION_DURATION, onCompleteRunnable)
    }

    fun prepareCreateAnimation(destView: View): Drawable {
        val animateDrawable = (destView as TextView).compoundDrawables[1]
        computePreviewDrawingParams(animateDrawable.intrinsicWidth,
                destView.getMeasuredWidth())
        referenceDrawable = animateDrawable
        return animateDrawable
    }

    fun recomputePreviewDrawingParams() {
        referenceDrawable?.apply {
            computePreviewDrawingParams(intrinsicWidth, icon.measuredWidth)
        }
    }

    private fun computePreviewDrawingParams(drawableSize: Int, totalSize: Int) {
        if (intrinsicIconSize != drawableSize.toFloat() || totalWidth != totalSize || prevTopPadding != icon.paddingTop) {
            intrinsicIconSize = drawableSize.toFloat()
            totalWidth = totalSize
            prevTopPadding = icon.paddingTop
            icon.background.setup(icon.launcher, icon, totalWidth, icon.paddingTop)
            icon.layoutRule.init(icon.background.previewSize, intrinsicIconSize,
                    Utilities.isRtl(icon.resources))
            updatePreviewItems(false)
        }
    }

    fun computePreviewItemDrawingParams(index: Int, curNumItems: Int,
                                        params: PreviewItemDrawingParams?): PreviewItemDrawingParams? {
        // We use an index of -1 to represent an icon on the workspace for the destroy and
        // create animations
        return if (index == -1) {
            getFinalIconParams(params)
        } else icon.layoutRule.computePreviewItemDrawingParams(index, curNumItems, params)
    }

    private fun getFinalIconParams(params: PreviewItemDrawingParams?): PreviewItemDrawingParams? {
        val iconSize = icon.launcher.deviceProfile.iconSizePx.toFloat()
        val scale = iconSize / referenceDrawable!!.intrinsicWidth
        val trans = (icon.background.previewSize - iconSize) / 2
        params?.update(trans, trans, scale)
        return params
    }

    private fun drawParams(canvas: Canvas, params: ArrayList<PreviewItemDrawingParams>,
                           transX: Float) {
        canvas.translate(transX, 0f)
        // The first item should be drawn last (ie. on top of later items)
        for (i in params.indices.reversed()) {
            val p = params[i]
            if (!p.hidden) {
                drawPreviewItem(canvas, p)
            }
        }
        canvas.translate(-transX, 0f)
    }

    fun draw(canvas: Canvas) {
        // The items are drawn in coordinates relative to the preview offset
        val bg = icon.folderBackground
        canvas.translate(bg.basePreviewOffsetX.toFloat(), bg.basePreviewOffsetY.toFloat())
        var firstPageItemsTransX = 0f
        if (shouldSlideInFirstPage) {
            drawParams(canvas, currentPageParams, currentPageItemsTransX)
            firstPageItemsTransX = -ITEM_SLIDE_IN_OUT_DISTANCE_PX + currentPageItemsTransX
        }
        drawParams(canvas, firstPageParams, firstPageItemsTransX)
        canvas.translate((-bg.basePreviewOffsetX).toFloat(), (-bg.basePreviewOffsetY).toFloat())
    }

    fun onParamsChanged() {
        icon.invalidate()
    }

    private fun drawPreviewItem(canvas: Canvas, params: PreviewItemDrawingParams) {
        canvas.save()
        canvas.translate(params.transX, params.transY)
        canvas.scale(params.scale, params.scale)
        params.drawable?.apply {
            val bounds = bounds
            canvas.save()
            canvas.translate(-bounds.left.toFloat(), -bounds.top.toFloat())
            canvas.scale(intrinsicIconSize / bounds.width(), intrinsicIconSize / bounds.height())
            draw(canvas)
            canvas.restore()
        }
        canvas.restore()
    }

    fun hidePreviewItem(index: Int, hidden: Boolean) {
        // If there are more params than visible in the preview, they are used for enter/exit
        // animation purposes and they were added to the front of the list.
        // To index the params properly, we need to skip these params.
        var idx = index
        idx += max(firstPageParams.size - ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW, 0)
        val params = if (idx < firstPageParams.size) firstPageParams[idx] else null
        if (params != null) {
            params.hidden = hidden
        }
    }

    private fun buildParamsForPage(page: Int, params: ArrayList<PreviewItemDrawingParams>, animate: Boolean) {
        val items = icon.getPreviewItemsOnPage(page)
        val prevNumItems = params.size

        // We adjust the size of the list to match the number of items in the preview.
        while (items.size < params.size) {
            params.removeAt(params.size - 1)
        }
        while (items.size > params.size) {
            params.add(PreviewItemDrawingParams(0f, 0f, 0f, 0f))
        }
        val numItemsInFirstPagePreview = if (page == 0) items.size else ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW
        for (i in params.indices) {
            val p = params[i]
            p.drawable = items[i].compoundDrawables[1]
            if (p.drawable != null && icon.folder?.isOpen == false) {
                // Set the callback to FolderIcon as it is responsible to drawing the icon. The
                // callback will be released when the folder is opened.
                p.drawable!!.callback = icon
            }
            if (!animate) {
                computePreviewItemDrawingParams(i, numItemsInFirstPagePreview, p)
                if (referenceDrawable == null) {
                    referenceDrawable = p.drawable
                }
            } else {
                val anim = FolderPreviewItemAnim(this, p, i, prevNumItems, i,
                        numItemsInFirstPagePreview, FolderIcon.DROP_IN_ANIMATION_DURATION, null)
                if (p.anim != null) {
                    if (p.anim!!.hasEqualFinalState(anim)) {
                        // do nothing, let the current animation finish
                        continue
                    }
                    p.anim!!.cancel()
                }
                p.anim = anim
                p.anim!!.start()
            }
        }
    }

    fun onFolderClose(currentPage: Int) {
        // If we are not closing on the first page, we animate the current page preview items
        // out, and animate the first page preview items in.
        shouldSlideInFirstPage = currentPage != 0
        if (shouldSlideInFirstPage) {
            currentPageItemsTransX = 0f
            buildParamsForPage(currentPage, currentPageParams, false)
            onParamsChanged()
            ValueAnimator.ofFloat(0f, ITEM_SLIDE_IN_OUT_DISTANCE_PX).apply {
                addUpdateListener { valueAnimator ->
                    currentPageItemsTransX = valueAnimator.animatedValue as Float
                    onParamsChanged()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        currentPageParams.clear()
                    }
                })
                startDelay = SLIDE_IN_FIRST_PAGE_ANIMATION_DURATION_DELAY
                duration = SLIDE_IN_FIRST_PAGE_ANIMATION_DURATION
            }.start()
        }
    }

    fun updatePreviewItems(animate: Boolean) {
        buildParamsForPage(0, firstPageParams, animate)
    }

    fun verifyDrawable(who: Drawable): Boolean {
        return firstPageParams.any { it.drawable === who }
    }

    /**
     * Handles the case where items in the preview are either:
     * - Moving into the preview
     * - Moving into a new position
     * - Moving out of the preview
     *
     * @param oldParams The list of items in the old preview.
     * @param newParams The list of items in the new preview.
     * @param dropped The item that was dropped onto the FolderIcon.
     */
    fun onDrop(oldParams: List<BubbleTextView>, newParams: List<BubbleTextView>,
               dropped: ShortcutInfo) {
        val numItems = newParams.size
        val params = firstPageParams
        buildParamsForPage(0, params, false)

        // New preview items for items that are moving in (except for the dropped item).
        val moveIn = ArrayList(newParams.filter { !oldParams.contains(it) && it.tag != dropped })
        for (i in moveIn.indices) {
            val prevIndex = newParams.indexOf(moveIn[i])
            val p = params[prevIndex]
            computePreviewItemDrawingParams(prevIndex, numItems, p)
            updateTransitionParam(p, moveIn[i], ClippedFolderIconLayoutRule.ENTER_INDEX, newParams.indexOf(moveIn[i]),
                    numItems)
        }

        // Items that are moving into new positions within the preview.
        for (newIndex in newParams.indices) {
            val oldIndex = oldParams.indexOf(newParams[newIndex])
            if (oldIndex >= 0 && newIndex != oldIndex) {
                val p = params[newIndex]
                updateTransitionParam(p, newParams[newIndex], oldIndex, newIndex, numItems)
            }
        }

        // Old preview items that need to be moved out.
        val moveOut: MutableList<BubbleTextView> = ArrayList(oldParams)
        moveOut.removeAll(newParams)
        for (i in moveOut.indices) {
            val item = moveOut[i]
            val oldIndex = oldParams.indexOf(item)
            val p = computePreviewItemDrawingParams(oldIndex, numItems, null)
            updateTransitionParam(p, item, oldIndex, ClippedFolderIconLayoutRule.EXIT_INDEX, numItems)
            params.add(0, p!!) // We want these items first so that they are on drawn last.
        }
        params.forEach { it.anim?.start() }
    }

    private fun updateTransitionParam(p: PreviewItemDrawingParams?, btv: BubbleTextView,
                                      prevIndex: Int, newIndex: Int, numItems: Int) {
        p ?: return
        p.drawable = btv.compoundDrawables[1]
        if (icon.folder?.isOpen == false) {
            // Set the callback to FolderIcon as it is responsible to drawing the icon. The
            // callback will be released when the folder is opened.
            p.drawable?.callback = icon
        }
        val anim = FolderPreviewItemAnim(this, p, prevIndex, numItems,
                newIndex, numItems, FolderIcon.DROP_IN_ANIMATION_DURATION, null)
        p.anim.takeIf { it != null && it.hasEqualFinalState(anim) }?.cancel()
        p.anim = anim
    }

    companion object {
        const val INITIAL_ITEM_ANIMATION_DURATION = 350L
        private const val FINAL_ITEM_ANIMATION_DURATION = 200L
        private const val SLIDE_IN_FIRST_PAGE_ANIMATION_DURATION_DELAY = 100L
        private const val SLIDE_IN_FIRST_PAGE_ANIMATION_DURATION = 300L
        private const val ITEM_SLIDE_IN_OUT_DISTANCE_PX = 200f
    }

}