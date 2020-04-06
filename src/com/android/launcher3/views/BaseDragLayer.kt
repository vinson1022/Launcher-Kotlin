/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import com.android.launcher3.*
import com.android.launcher3.util.MultiValueAlpha
import com.android.launcher3.util.TouchController
import java.util.*

/**
 * A viewgroup with utility methods for drag-n-drop and touch interception
 */
abstract class BaseDragLayer<T : BaseDraggingActivity?>(
        context: Context?,
        attrs: AttributeSet?,
        alphaChannelCount: Int
) : InsettableFrameLayout(context, attrs) {
    private val tmpXY = IntArray(2)
    private val hitRect = Rect()
    @JvmField
    protected val activity = BaseActivity.fromContext(context) as T
    private val multiValueAlpha = MultiValueAlpha(this, alphaChannelCount)

    @JvmField
    protected var controllers: Array<TouchController> = arrayOf()
    @JvmField
    protected var activeController: TouchController? = null
    private var touchCompleteListener: TouchCompleteListener? = null

    fun isEventOverView(view: View, ev: MotionEvent): Boolean {
        getDescendantRectRelativeToSelf(view, hitRect)
        return hitRect.contains(ev.x.toInt(), ev.y.toInt())
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.action
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (touchCompleteListener != null) {
                touchCompleteListener!!.onTouchComplete()
            }
            touchCompleteListener = null
        } else if (action == MotionEvent.ACTION_DOWN) {
            activity!!.finishAutoCancelActionMode()
        }
        return findActiveController(ev)
    }

    protected open fun findActiveController(ev: MotionEvent?): Boolean {
        activeController = null
        val topView = AbstractFloatingView.getTopOpenView(activity)
        if (topView != null && topView.onControllerInterceptTouchEvent(ev!!)) {
            activeController = topView
            return true
        }
        for (controller in controllers) {
            if (controller.onControllerInterceptTouchEvent(ev!!)) {
                activeController = controller
                return true
            }
        }
        return false
    }

    override fun onRequestSendAccessibilityEvent(child: View, event: AccessibilityEvent): Boolean {
        // Shortcuts can appear above folder
        val topView: View? = AbstractFloatingView.getTopOpenViewWithType(activity,
                AbstractFloatingView.TYPE_ACCESSIBLE)
        return if (topView != null) {
            if (child === topView) {
                super.onRequestSendAccessibilityEvent(child, event)
            } else false
            // Skip propagating onRequestSendAccessibilityEvent for all other children
            // which are not topView
        } else super.onRequestSendAccessibilityEvent(child, event)
    }

    override fun addChildrenForAccessibility(childrenForAccessibility: ArrayList<View>) {
        val topView: View? = AbstractFloatingView.getTopOpenViewWithType(activity,
                AbstractFloatingView.TYPE_ACCESSIBLE)
        if (topView != null) {
            // Only add the top view as a child for accessibility when it is open
            addAccessibleChildToList(topView, childrenForAccessibility)
        } else {
            super.addChildrenForAccessibility(childrenForAccessibility)
        }
    }

    protected fun addAccessibleChildToList(child: View, outList: ArrayList<View>) {
        if (child.isImportantForAccessibility) {
            outList.add(child)
        } else {
            child.addChildrenForAccessibility(outList)
        }
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        if (child is AbstractFloatingView) {
            // Handles the case where the view is removed without being properly closed.
            // This can happen if something goes wrong during a state change/transition.
            postDelayed({
                if (child.isOpen) {
                    child.close(false)
                }
            }, Utilities.SINGLE_FRAME_MS.toLong())
        }
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.action
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (touchCompleteListener != null) {
                touchCompleteListener!!.onTouchComplete()
            }
            touchCompleteListener = null
        }
        return if (activeController != null) {
            activeController!!.onControllerTouchEvent(ev)
        } else {
            // In case no child view handled the touch event, we may not get onIntercept anymore
            findActiveController(ev)
        }
    }

    /**
     * Determine the rect of the descendant in this DragLayer's coordinates
     *
     * @param descendant The descendant whose coordinates we want to find.
     * @param r The rect into which to place the results.
     * @return The factor by which this descendant is scaled relative to this DragLayer.
     */
    fun getDescendantRectRelativeToSelf(descendant: View, r: Rect): Float {
        tmpXY[0] = 0
        tmpXY[1] = 0
        val scale = getDescendantCoordRelativeToSelf(descendant, tmpXY)
        r[tmpXY[0], tmpXY[1], (tmpXY[0] + scale * descendant.measuredWidth).toInt()] =
                (tmpXY[1] + scale * descendant.measuredHeight).toInt()
        return scale
    }

    fun getLocationInDragLayer(child: View?, loc: IntArray): Float {
        loc[0] = 0
        loc[1] = 0
        return getDescendantCoordRelativeToSelf(child, loc)
    }

    fun getDescendantCoordRelativeToSelf(descendant: View?, coord: IntArray?): Float {
        return getDescendantCoordRelativeToSelf(descendant, coord, false)
    }

    /**
     * Given a coordinate relative to the descendant, find the coordinate in this DragLayer's
     * coordinates.
     *
     * @param descendant The descendant to which the passed coordinate is relative.
     * @param coord The coordinate that we want mapped.
     * @param includeRootScroll Whether or not to account for the scroll of the root descendant:
     * sometimes this is relevant as in a child's coordinates within the root descendant.
     * @return The factor by which this descendant is scaled relative to this DragLayer. Caution
     * this scale factor is assumed to be equal in X and Y, and so if at any point this
     * assumption fails, we will need to return a pair of scale factors.
     */
    fun getDescendantCoordRelativeToSelf(descendant: View?, coord: IntArray?,
                                         includeRootScroll: Boolean): Float {
        return Utilities.getDescendantCoordRelativeToAncestor(descendant, this,
                coord, includeRootScroll)
    }

    /**
     * Inverse of [.getDescendantCoordRelativeToSelf].
     */
    fun mapCoordInSelfToDescendant(descendant: View?, coord: IntArray?) {
        Utilities.mapCoordInSelfToDescendant(descendant, this, coord)
    }

    fun getViewRectRelativeToSelf(v: View, r: Rect) {
        val loc = IntArray(2)
        getLocationInWindow(loc)
        val x = loc[0]
        val y = loc[1]
        v.getLocationInWindow(loc)
        val vX = loc[0]
        val vY = loc[1]
        val left = vX - x
        val top = vY - y
        r[left, top, left + v.measuredWidth] = top + v.measuredHeight
    }

    override fun dispatchUnhandledMove(focused: View, direction: Int): Boolean {
        // Consume the unhandled move if a container is open, to avoid switching pages underneath.
        return AbstractFloatingView.getTopOpenView(activity) != null
    }

    override fun onRequestFocusInDescendants(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        val topView: View? = AbstractFloatingView.getTopOpenView(activity)
        return topView?.requestFocus(direction, previouslyFocusedRect)
                ?: super.onRequestFocusInDescendants(direction, previouslyFocusedRect)
    }

    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
        val topView: View? = AbstractFloatingView.getTopOpenView(activity)
        topView?.addFocusables(views, direction) 
                ?: super.addFocusables(views, direction, focusableMode)
    }

    fun setTouchCompleteListener(listener: TouchCompleteListener?) {
        touchCompleteListener = listener
    }

    interface TouchCompleteListener {
        fun onTouchComplete()
    }

    override fun generateLayoutParams(attrs: AttributeSet) = BaseDragLayerLayoutParams(context, attrs)

    override fun generateDefaultLayoutParams()
            = BaseDragLayerLayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    // Override to allow type-checking of LayoutParams.
    override fun checkLayoutParams(p: ViewGroup.LayoutParams) = p is LayoutParams

    override fun generateLayoutParams(p: ViewGroup.LayoutParams) = BaseDragLayerLayoutParams(p)

    fun getAlphaProperty(index: Int) = multiValueAlpha.getProperty(index)

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val count = childCount
        for (i in 0 until count) {
            val child = getChildAt(i)
            val flp = child.layoutParams as FrameLayout.LayoutParams
            if (flp is BaseDragLayerLayoutParams) {
                if (flp.customPosition) {
                    child.layout(flp.x, flp.y, flp.x + flp.width, flp.y + flp.height)
                }
            }
        }
    }
}

class BaseDragLayerLayoutParams : InsettableFrameLayout.LayoutParams {
    @JvmField
    var x = 0
    @JvmField
    var y = 0
    @JvmField
    var customPosition = false

    constructor(c: Context?, attrs: AttributeSet?) : super(c, attrs)
    constructor(width: Int, height: Int) : super(width, height)
    constructor(lp: ViewGroup.LayoutParams) : super(lp)
}