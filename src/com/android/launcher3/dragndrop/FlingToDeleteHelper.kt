/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.launcher3.dragndrop

import android.graphics.PointF
import android.os.SystemClock
import android.view.*
import android.view.DragEvent.*
import android.view.MotionEvent.*
import com.android.launcher3.ButtonDropTarget
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.util.FlingAnimation
import kotlin.math.acos

/**
 * Utility class to manage fling to delete action during drag and drop.
 */
class FlingToDeleteHelper(private val launcher: Launcher) {
    private val flingToDeleteThresholdVelocity = launcher.resources.getDimensionPixelSize(R.dimen.drag_flingToDeleteMinVelocity)
    var dropTarget: ButtonDropTarget? = null
        private set
    private var velocityTracker: VelocityTracker? = null

    fun recordMotionEvent(ev: MotionEvent) {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker!!.addMovement(ev)
    }

    /**
     * Same as [.recordMotionEvent]. It creates a temporary [MotionEvent] object
     * using {@param event} for tracking velocity.
     */
    fun recordDragEvent(dragStartTime: Long, event: DragEvent) {
        val motionAction = when (event.action) {
            ACTION_DRAG_STARTED -> ACTION_DOWN
            ACTION_DRAG_LOCATION -> ACTION_MOVE
            ACTION_DRAG_ENDED -> ACTION_UP
            else -> return
        }
        val emulatedEvent = obtain(dragStartTime, SystemClock.uptimeMillis(),
                motionAction, event.x, event.y, 0)
        recordMotionEvent(emulatedEvent)
        emulatedEvent.recycle()
    }

    fun releaseVelocityTracker() {
        if (velocityTracker != null) {
            velocityTracker!!.recycle()
            velocityTracker = null
        }
    }

    fun getFlingAnimation(dragObject: DragObject): Runnable? {
        val vel = isFlingingToDelete ?: return null
        return FlingAnimation(dragObject, vel, dropTarget!!, launcher)
    }
    // Remove icon is on left side instead of top, so check if we are flinging to the left.
    // Do a quick dot product test to ensure that we are flinging upwards

    /**
     * Determines whether the user flung the current item to delete it.
     *
     * @return the vector at which the item was flung, or null if no fling was detected.
     */
    private val isFlingingToDelete: PointF?
        get() {
            if (dropTarget == null) {
                dropTarget = launcher.findViewById<View>(R.id.delete_target_text) as ButtonDropTarget
            }
            if (dropTarget == null || !dropTarget!!.isDropEnabled) return null
            val config = ViewConfiguration.get(launcher)
            velocityTracker!!.computeCurrentVelocity(1000, config.scaledMaximumFlingVelocity.toFloat())
            val vel = PointF(velocityTracker!!.xVelocity, velocityTracker!!.yVelocity)
            var theta = MAX_FLING_DEGREES + 1
            if (velocityTracker!!.yVelocity < flingToDeleteThresholdVelocity) {
                // Do a quick dot product test to ensure that we are flinging upwards
                val upVec = PointF(0f, -1f)
                theta = getAngleBetweenVectors(vel, upVec)
            } else if (launcher.deviceProfile.isVerticalBarLayout &&
                    velocityTracker!!.xVelocity < flingToDeleteThresholdVelocity) {
                // Remove icon is on left side instead of top, so check if we are flinging to the left.
                val leftVec = PointF(-1f, 0f)
                theta = getAngleBetweenVectors(vel, leftVec)
            }
            return if (theta <= Math.toRadians(MAX_FLING_DEGREES.toDouble())) {
                vel
            } else null
        }

    private fun getAngleBetweenVectors(vec1: PointF, vec2: PointF): Float {
        return acos((vec1.x * vec2.x + vec1.y * vec2.y) /
                (vec1.length() * vec2.length()).toDouble()).toFloat()
    }

    companion object {
        private const val MAX_FLING_DEGREES = 35f
    }

}