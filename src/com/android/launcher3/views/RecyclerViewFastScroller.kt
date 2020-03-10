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
package com.android.launcher3.views

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.util.Property
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.TextView
import com.android.launcher3.BaseRecyclerView
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.graphics.FastScrollThumbDrawable
import com.android.launcher3.util.getAttrColor
import com.android.launcher3.util.getColorAccent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * The track and scrollbar that shows when you scroll the list.
 */
class RecyclerViewFastScroller
@JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val minWidth = resources.getDimensionPixelSize(R.dimen.fastscroll_track_min_width)
    private val maxWidth = resources.getDimensionPixelSize(R.dimen.fastscroll_track_max_width)
    private val thumbPadding = resources.getDimensionPixelSize(R.dimen.fastscroll_thumb_padding)

    /** Keeps the last known scrolling delta/velocity along y-axis.  */
    private var dy = 0
    private val deltaThreshold = resources.displayMetrics.density * SCROLL_DELTA_THRESHOLD_DP
    private val config = ViewConfiguration.get(context)

    // Current width of the track
    private var currentWidth = minWidth
    private var widthAnimator: ObjectAnimator? = null
    private val thumbPaint = Paint().apply {
        isAntiAlias = true
        color = getColorAccent(context)
        style = Paint.Style.FILL
    }
    val thumbHeight = resources.getDimensionPixelSize(R.dimen.fastscroll_thumb_height)
    private val trackPaint = Paint().apply {
        color = getAttrColor(context, android.R.attr.textColorPrimary)
        alpha = MAX_TRACK_ALPHA
    }
    private var lastTouchY = 0f
    var isDraggingThumb = false
        private set
    var isThumbDetached = false
        private set
    private val canThumbDetach: Boolean
    private var ignoreDragGesture = false

    // This is the offset from the top of the scrollbar when the user first starts touching.  To
    // prevent jumping, this offset is applied as the user scrolls.
    private var touchOffsetY = 0
    var thumbOffsetY: Int = 0
        set(y) {
            if (field == y) {
                return
            }
            field = y
            invalidate()
        }

    // Fast scroller popup
    private var popupView: TextView? = null
    private var popupVisible = false
    private var popupSectionName: String? = null
    private var baseRecyclerView: BaseRecyclerView? = null
    private var downX = 0
    private var downY = 0
    private var lastY = 0

    init {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.RecyclerViewFastScroller, defStyleAttr, 0)
        canThumbDetach = ta.getBoolean(R.styleable.RecyclerViewFastScroller_canThumbDetach, false)
        ta.recycle()
    }

    fun setRecyclerView(rv: BaseRecyclerView, popupView: TextView) {
        baseRecyclerView?.clearOnScrollListeners()
        baseRecyclerView = rv
        baseRecyclerView!!.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                this@RecyclerViewFastScroller.dy = dy

                // TODO(winsonc): If we want to animate the section heads while scrolling, we can
                //                initiate that here if the recycler view scroll state is not
                //                RecyclerView.SCROLL_STATE_IDLE.
                baseRecyclerView!!.onUpdateScrollbar(dy)
            }
        })
        this.popupView = popupView
        this.popupView!!.background = FastScrollThumbDrawable(thumbPaint, Utilities.isRtl(resources))
    }

    fun reattachThumbToScroll() {
        isThumbDetached = false
    }

    private fun setTrackWidth(width: Int) {
        if (this.currentWidth == width) return
        this.currentWidth = width
        invalidate()
    }

    /**
     * Handles the touch event and determines whether to show the fast scroller (or updates it if
     * it is already showing).
     */
    fun handleTouchEvent(ev: MotionEvent, offset: Point): Boolean {
        val x = ev.x.toInt() - offset.x
        val y = ev.y.toInt() - offset.y
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                // Keep track of the down positions
                downX = x
                run {
                    lastY = y
                    downY = lastY
                }
                if (abs(dy) < deltaThreshold &&
                        baseRecyclerView!!.scrollState != RecyclerView.SCROLL_STATE_IDLE) {
                    // now the touch events are being passed to the {@link WidgetCell} until the
                    // touch sequence goes over the touch slop.
                    baseRecyclerView!!.stopScroll()
                }
                if (isNearThumb(x, y)) {
                    touchOffsetY = downY - thumbOffsetY
                } else if (FeatureFlags.LAUNCHER3_DIRECT_SCROLL
                        && baseRecyclerView!!.supportsFastScrolling()
                        && isNearScrollBar(downX)) {
                    calcTouchOffsetAndPrepToFastScroll(downY, lastY)
                    updateFastScrollSectionNameAndThumbOffset(lastY, y)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                lastY = y

                // Check if we should start scrolling, but ignore this fastscroll gesture if we have
                // exceeded some fixed movement
                ignoreDragGesture = ignoreDragGesture or (abs(y - downY) > config.scaledPagingTouchSlop)
                if (!isDraggingThumb && !ignoreDragGesture && baseRecyclerView!!.supportsFastScrolling() &&
                        isNearThumb(downX, lastY) && abs(y - downY) > config.scaledTouchSlop) {
                    calcTouchOffsetAndPrepToFastScroll(downY, lastY)
                }
                if (isDraggingThumb) {
                    updateFastScrollSectionNameAndThumbOffset(lastY, y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                baseRecyclerView!!.onFastScrollCompleted()
                touchOffsetY = 0
                lastTouchY = 0f
                ignoreDragGesture = false
                if (isDraggingThumb) {
                    isDraggingThumb = false
                    animatePopupVisibility(false)
                    showActiveScrollbar(false)
                }
            }
        }
        return isDraggingThumb
    }

    private fun calcTouchOffsetAndPrepToFastScroll(downY: Int, lastY: Int) {
        isDraggingThumb = true
        if (canThumbDetach) {
            isThumbDetached = true
        }
        touchOffsetY += lastY - downY
        animatePopupVisibility(true)
        showActiveScrollbar(true)
    }

    private fun updateFastScrollSectionNameAndThumbOffset(lastY: Int, y: Int) {
        // Update the fastscroller section name at this touch position
        val bottom = baseRecyclerView!!.scrollbarTrackHeight - thumbHeight
        val boundedY = max(0, (min(bottom, y - touchOffsetY))).toFloat()
        val sectionName = baseRecyclerView!!.scrollToPositionAtProgress(boundedY / bottom)
        if (sectionName != popupSectionName) {
            popupSectionName = sectionName
            popupView!!.text = sectionName
        }
        animatePopupVisibility(sectionName.isNotEmpty())
        updatePopupY(lastY)
        lastTouchY = boundedY
        thumbOffsetY = lastTouchY.toInt()
    }

    public override fun onDraw(canvas: Canvas) {
        if (thumbOffsetY < 0) return

        val saveCount = canvas.save()
        canvas.translate(width / 2.toFloat(), baseRecyclerView!!.scrollBarTop.toFloat())
        // Draw the track
        var halfW = currentWidth / 2.toFloat()
        canvas.drawRoundRect(-halfW, 0f, halfW, baseRecyclerView!!.scrollbarTrackHeight.toFloat(),
                currentWidth.toFloat(), currentWidth.toFloat(), trackPaint)
        canvas.translate(0f, thumbOffsetY.toFloat())
        halfW += thumbPadding.toFloat()
        val r = currentWidth + thumbPadding + thumbPadding.toFloat()
        canvas.drawRoundRect(-halfW, 0f, halfW, thumbHeight.toFloat(), r, r, thumbPaint)
        canvas.restoreToCount(saveCount)
    }

    /**
     * Animates the width of the scrollbar.
     */
    private fun showActiveScrollbar(isScrolling: Boolean) {
        widthAnimator?.cancel()
        widthAnimator = ObjectAnimator.ofInt(this, TRACK_WIDTH,
                if (isScrolling) maxWidth else minWidth).apply {
            duration = SCROLL_BAR_VIS_DURATION.toLong()
            start()
        }
    }

    /**
     * Returns whether the specified point is inside the thumb bounds.
     */
    private fun isNearThumb(x: Int, y: Int): Boolean {
        val offset = y - thumbOffsetY
        return x in 0 until width && offset >= 0 && offset <= thumbHeight
    }

    /**
     * Returns true if AllAppsTransitionController can handle vertical motion
     * beginning at this point.
     */
    fun shouldBlockIntercept(x: Int, y: Int): Boolean {
        return isNearThumb(x, y)
    }

    /**
     * Returns whether the specified x position is near the scroll bar.
     */
    private fun isNearScrollBar(x: Int): Boolean {
        return x >= (width - maxWidth) / 2 && x <= (width + maxWidth) / 2
    }

    private fun animatePopupVisibility(visible: Boolean) {
        if (popupVisible != visible) {
            popupVisible = visible
            popupView!!.animate().cancel()
            popupView!!.animate().alpha(if (visible) 1f else 0f).setDuration(if (visible) 200 else 150.toLong()).start()
        }
    }

    private fun updatePopupY(lastTouchY: Int) {
        val height = popupView!!.height
        var top = (lastTouchY - FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR * height
                + baseRecyclerView!!.scrollBarTop)
        top = Utilities.boundToRange(top,
                maxWidth.toFloat(), baseRecyclerView!!.scrollbarTrackHeight - maxWidth - height.toFloat())
        popupView!!.translationY = top
    }

    fun isHitInParent(x: Float, y: Float, outOffset: Point?): Boolean {
        if (thumbOffsetY < 0) return false

        getHitRect(sTempRect)
        sTempRect.top += baseRecyclerView!!.scrollBarTop
        outOffset?.set(sTempRect.left, sTempRect.top)
        return sTempRect.contains(x.toInt(), y.toInt())
    }

    override fun hasOverlappingRendering(): Boolean {
        // There is actually some overlap between the track and the thumb. But since the track
        // alpha is so low, it does not matter.
        return false
    }

    companion object {
        private const val SCROLL_DELTA_THRESHOLD_DP = 4
        private val sTempRect = Rect()
        private val TRACK_WIDTH =
                object : Property<RecyclerViewFastScroller, Int>(Int::class.java, "width") {
                    override fun get(scrollBar: RecyclerViewFastScroller): Int {
                        return scrollBar.currentWidth
                    }

                    override fun set(scrollBar: RecyclerViewFastScroller, value: Int) {
                        scrollBar.setTrackWidth(value)
                    }
                }
        private const val MAX_TRACK_ALPHA = 30
        private const val SCROLL_BAR_VIS_DURATION = 150
        private const val FAST_SCROLL_OVERLAY_Y_OFFSET_FACTOR = 0.75f
    }
}