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
package com.android.launcher3.util

import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.MotionEvent.*
import android.view.TouchDelegate
import android.view.View

/**
 * This class differs from the framework [TouchDelegate] in that it transforms the
 * coordinates of the motion event to the provided bounds.
 *
 * You can also modify the bounds post construction. Since the bounds are available during layout,
 * this avoids new object creation during every layout.
 */
class TransformingTouchDelegate(private var delegateView: View) : TouchDelegate(tempRect, delegateView) {
    private val bounds = RectF()
    private val touchCheckBounds = RectF()
    private var touchExtension = 0f
    private var wasTouchOutsideBounds = false
    private var delegateTargeted = false

    fun setBounds(left: Int, top: Int, right: Int, bottom: Int) {
        bounds[left.toFloat(), top.toFloat(), right.toFloat()] = bottom.toFloat()
        updateTouchBounds()
    }

    fun extendTouchBounds(extension: Float) {
        touchExtension = extension
        updateTouchBounds()
    }

    private fun updateTouchBounds() {
        touchCheckBounds.set(bounds)
        touchCheckBounds.inset(-touchExtension, -touchExtension)
    }

    fun setDelegateView(view: View) {
        delegateView = view
    }

    /**
     * Will forward touch events to the delegate view if the event is within the bounds
     * specified in the constructor.
     *
     * @param event The touch event to forward
     * @return True if the event was forwarded to the delegate, false otherwise.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        var sendToDelegate = false
        when (event.action) {
            ACTION_DOWN -> {
                delegateTargeted = touchCheckBounds.contains(event.x, event.y)
                if (delegateTargeted) {
                    wasTouchOutsideBounds = !bounds.contains(event.x, event.y)
                    sendToDelegate = true
                }
            }
            ACTION_MOVE -> sendToDelegate = delegateTargeted
            ACTION_UP, ACTION_CANCEL -> {
                sendToDelegate = delegateTargeted
                delegateTargeted = false
            }
        }
        var handled = false
        if (sendToDelegate) {
            val x = event.x
            val y = event.y
            if (wasTouchOutsideBounds) {
                event.setLocation(bounds.centerX(), bounds.centerY())
            } else {
                event.offsetLocation(-bounds.left, -bounds.top)
            }
            handled = delegateView.dispatchTouchEvent(event)
            event.setLocation(x, y)
        }
        return handled
    }

    companion object {
        private val tempRect = Rect()
    }
}