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
package com.android.launcher3.touch

import android.content.Context
import android.graphics.PointF
import android.support.annotation.VisibleForTesting
import android.util.Log
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.max

/**
 * One dimensional scroll/drag/swipe gesture detector.
 *
 * Definition of swipe is different from android system in that this detector handles
 * 'swipe to dismiss', 'swiping up/down a container' but also keeps scrolling state before
 * swipe action happens
 */
class SwipeDetector @VisibleForTesting
constructor(
        private val touchSlop: Float,
        /* Client of this gesture detector can register a callback. */
        private val listener: Listener,
        private var direction: Direction
) {
    var scrollDirections = 0
        private set
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    /* Scroll state, this is set to true during dragging and animation. */
    private var state = ScrollState.IDLE

    internal enum class ScrollState {
        IDLE, DRAGGING,  // onDragStart, onDrag
        SETTLING // onDragEnd
    }

    abstract class Direction {
        abstract fun getDisplacement(ev: MotionEvent, pointerIndex: Int, refPoint: PointF): Float
        /**
         * Distance in pixels a touch can wander before we think the user is scrolling.
         */
        abstract fun getActiveTouchSlop(ev: MotionEvent, pointerIndex: Int, downPos: PointF): Float
    }

    //------------------- ScrollState transition diagram -----------------------------------
    //
    // IDLE ->      (mDisplacement > mTouchSlop) -> DRAGGING
    // DRAGGING -> (MotionEvent#ACTION_UP, MotionEvent#ACTION_CANCEL) -> SETTLING
    // SETTLING -> (MotionEvent#ACTION_DOWN) -> DRAGGING
    // SETTLING -> (View settled) -> IDLE
    private fun setState(newState: ScrollState) {
        if (DBG) {
            Log.d(TAG, "setState:$state->$newState")
        }
        // onDragStart and onDragEnd is reported ONLY on state transition
        if (newState == ScrollState.DRAGGING) {
            initializeDragging()
            if (state == ScrollState.IDLE) {
                reportDragStart(false)
            } else if (state == ScrollState.SETTLING) {
                reportDragStart(true)
            }
        }
        if (newState == ScrollState.SETTLING) {
            reportDragEnd()
        }
        state = newState
    }

    val isDraggingOrSettling: Boolean
        get() = state == ScrollState.DRAGGING || state == ScrollState.SETTLING

    /**
     * There's no touch and there's no animation.
     */
    val isIdleState: Boolean
        get() = state == ScrollState.IDLE

    val isSettlingState: Boolean
        get() = state == ScrollState.SETTLING

    val isDraggingState: Boolean
        get() = state == ScrollState.DRAGGING

    private val downPos = PointF()
    private val lastPos = PointF()
    private var currentMillis: Long = 0
    private var velocity = 0f
    private var lastDisplacement = 0f
    private var displacement = 0f
    private var subtractDisplacement = 0f
    private var ignoreSlopWhenSettling = false

    interface Listener {
        fun onDragStart(start: Boolean)
        fun onDrag(displacement: Float, velocity: Float): Boolean
        fun onDragEnd(velocity: Float, fling: Boolean)
    }

    constructor(context: Context, l: Listener, dir: Direction) : this(ViewConfiguration.get(context).scaledTouchSlop.toFloat(), l, dir) {}

    fun updateDirection(dir: Direction) {
        this.direction = dir
    }

    fun setDetectableScrollConditions(scrollDirectionFlags: Int, ignoreSlop: Boolean) {
        scrollDirections = scrollDirectionFlags
        ignoreSlopWhenSettling = ignoreSlop
    }

    private fun shouldScrollStart(ev: MotionEvent, pointerIndex: Int): Boolean {
        // reject cases where the angle or slop condition is not met.
        if (max(direction.getActiveTouchSlop(ev, pointerIndex, downPos), touchSlop)
                > abs(displacement)) {
            return false
        }
        // Check if the client is interested in scroll in current direction.
        return (scrollDirections and DIRECTION_NEGATIVE) > 0 && displacement > 0 ||
                (scrollDirections and DIRECTION_POSITIVE) > 0 && displacement < 0
    }

    fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = ev.getPointerId(0)
                downPos[ev.x] = ev.y
                lastPos.set(downPos)
                lastDisplacement = 0f
                displacement = 0f
                velocity = 0f
                if (state == ScrollState.SETTLING && ignoreSlopWhenSettling) {
                    setState(ScrollState.DRAGGING)
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val ptrIdx = ev.actionIndex
                val ptrId = ev.getPointerId(ptrIdx)
                if (ptrId == activePointerId) {
                    val newPointerIdx = if (ptrIdx == 0) 1 else 0
                    downPos[ev.getX(newPointerIdx) - (lastPos.x - downPos.x)] = ev.getY(newPointerIdx) - (lastPos.y - downPos.y)
                    lastPos[ev.getX(newPointerIdx)] = ev.getY(newPointerIdx)
                    activePointerId = ev.getPointerId(newPointerIdx)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val pointerIndex = ev.findPointerIndex(activePointerId)
                if (pointerIndex == MotionEvent.INVALID_POINTER_ID) {
                    return true
                }
                displacement = direction.getDisplacement(ev, pointerIndex, downPos)
                computeVelocity(direction.getDisplacement(ev, pointerIndex, lastPos),
                        ev.eventTime)
                // handle state and listener calls.
                if (state != ScrollState.DRAGGING && shouldScrollStart(ev, pointerIndex)) {
                    setState(ScrollState.DRAGGING)
                }
                if (state == ScrollState.DRAGGING) {
                    reportDragging()
                }
                lastPos[ev.getX(pointerIndex)] = ev.getY(pointerIndex)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP ->
                // These are synthetic events and there is no need to update internal values.
                if (state == ScrollState.DRAGGING) {
                    setState(ScrollState.SETTLING)
                }
            else -> {
            }
        }
        return true
    }

    fun finishedScrolling() {
        setState(ScrollState.IDLE)
    }

    private fun reportDragStart(recatch: Boolean): Boolean {
        listener.onDragStart(!recatch)
        if (DBG) {
            Log.d(TAG, "onDragStart recatch:$recatch")
        }
        return true
    }

    private fun initializeDragging() {
        if (state == ScrollState.SETTLING && ignoreSlopWhenSettling) {
            subtractDisplacement = 0f
        }
        subtractDisplacement = if (displacement > 0) {
            touchSlop
        } else {
            -touchSlop
        }
    }

    /**
     * Returns if the start drag was towards the positive direction or negative.
     *
     * @see .setDetectableScrollConditions
     * @see .DIRECTION_BOTH
     */
    fun wasInitialTouchPositive(): Boolean {
        return subtractDisplacement < 0
    }

    private fun reportDragging(): Boolean {
        if (displacement != lastDisplacement) {
            if (DBG) {
                Log.d(TAG, String.format("onDrag disp=%.1f, velocity=%.1f",
                        displacement, velocity))
            }
            lastDisplacement = displacement
            return listener.onDrag(displacement - subtractDisplacement, velocity)
        }
        return true
    }

    private fun reportDragEnd() {
        if (DBG) {
            Log.d(TAG, String.format("onScrollEnd disp=%.1f, velocity=%.1f",
                    displacement, velocity))
        }
        listener.onDragEnd(velocity, abs(velocity) > RELEASE_VELOCITY_PX_MS)
    }

    /**
     * Computes the damped velocity.
     */
    private fun computeVelocity(delta: Float, currentMillis: Long): Float {
        val previousMillis = this.currentMillis
        this.currentMillis = currentMillis
        val deltaTimeMillis = this.currentMillis - previousMillis.toFloat()
        val velocity = if (deltaTimeMillis > 0) delta / deltaTimeMillis else 0f
        this.velocity = if (abs(this.velocity) < 0.001f) {
            velocity
        } else {
            val alpha = computeDampeningFactor(deltaTimeMillis)
            interpolate(this.velocity, velocity, alpha)
        }
        return this.velocity
    }

    companion object {
        private const val DBG = false
        private const val TAG = "SwipeDetector"
        const val DIRECTION_POSITIVE = 1 shl 0
        const val DIRECTION_NEGATIVE = 1 shl 1
        const val DIRECTION_BOTH = DIRECTION_NEGATIVE or DIRECTION_POSITIVE
        private const val ANIMATION_DURATION = 1200f
        /**
         * The minimum release velocity in pixels per millisecond that triggers fling..
         */
        const val RELEASE_VELOCITY_PX_MS = 1.0f
        /**
         * The time constant used to calculate dampening in the low-pass filter of scroll velocity.
         * Cutoff frequency is set at 10 Hz.
         */
        private const val SCROLL_VELOCITY_DAMPENING_RC = 1000f / (2f * Math.PI.toFloat() * 10)
        @JvmField
        val VERTICAL: Direction = object : Direction() {
            override fun getDisplacement(ev: MotionEvent, pointerIndex: Int, refPoint: PointF): Float {
                return ev.getY(pointerIndex) - refPoint.y
            }

            override fun getActiveTouchSlop(ev: MotionEvent, pointerIndex: Int, downPos: PointF): Float {
                return abs(ev.getX(pointerIndex) - downPos.x)
            }
        }
        @JvmField
        val HORIZONTAL: Direction = object : Direction() {
            override fun getDisplacement(ev: MotionEvent, pointerIndex: Int, refPoint: PointF): Float {
                return ev.getX(pointerIndex) - refPoint.x
            }

            override fun getActiveTouchSlop(ev: MotionEvent, pointerIndex: Int, downPos: PointF): Float {
                return abs(ev.getY(pointerIndex) - downPos.y)
            }
        }

        /**
         * Returns a time-dependent dampening factor using delta time.
         */
        private fun computeDampeningFactor(deltaTime: Float): Float {
            return deltaTime / (SCROLL_VELOCITY_DAMPENING_RC + deltaTime)
        }

        /**
         * Returns the linear interpolation between two values
         */
        fun interpolate(from: Float, to: Float, alpha: Float): Float {
            return (1.0f - alpha) * from + alpha * to
        }

        @JvmStatic
        fun calculateDuration(velocity: Float, progressNeeded: Float): Long {
            // TODO: make these values constants after tuning.
            val velocityDivisor = max(2f, abs(0.5f * velocity))
            val travelDistance = max(0.2f, progressNeeded)
            val duration = max(100f, ANIMATION_DURATION / velocityDivisor * travelDistance).toLong()
            if (DBG) {
                Log.d(TAG, String.format("calculateDuration=%d, v=%f, d=%f", duration, velocity, progressNeeded))
            }
            return duration
        }
    }

}