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
package com.android.launcher3.touch

import android.graphics.PointF
import android.graphics.Rect
import android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
import android.view.HapticFeedbackConstants.LONG_PRESS
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import com.android.launcher3.*
import com.android.launcher3.LauncherState.NORMAL
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction.NONE
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch.LONGPRESS
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType.WORKSPACE
import com.android.launcher3.views.OptionsPopupView

/**
 * Helper class to handle touch on empty space in workspace and show options popup on long press
 */
class WorkspaceTouchListener(
        private val launcher: Launcher,
        private val workspace: Workspace
) : OnTouchListener, Runnable {

    private val tempRect = Rect()
    private val touchDownPoint = PointF()
    private var longPressState = STATE_CANCELLED

    override fun onTouch(view: View, ev: MotionEvent): Boolean {
        val action = ev.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            // Check if we can handle long press.
            var handleLongPress = canHandleLongPress()
            if (handleLongPress) {
                // Check if the event is not near the edges
                val dp = launcher.deviceProfile
                val dl = launcher.dragLayer
                val insets = dp.insets
                tempRect[insets.left, insets.top, dl.width - insets.right] = dl.height - insets.bottom
                tempRect.inset(dp.edgeMarginPx, dp.edgeMarginPx)
                handleLongPress = tempRect.contains(ev.x.toInt(), ev.y.toInt())
            }
            cancelLongPress()
            if (handleLongPress) {
                longPressState = STATE_REQUESTED
                touchDownPoint[ev.x] = ev.y
                workspace.postDelayed(this, ViewConfiguration.getLongPressTimeout().toLong())
            }
            workspace.onTouchEvent(ev)
            // Return true to keep receiving touch events
            return true
        }
        if (longPressState == STATE_PENDING_PARENT_INFORM) {
            // Inform the workspace to cancel touch handling
            ev.action = MotionEvent.ACTION_CANCEL
            workspace.onTouchEvent(ev)
            ev.action = action
            longPressState = STATE_COMPLETED
        }
        val result = when (longPressState) {
            STATE_COMPLETED -> {
                // We have handled the touch, so workspace does not need to know anything anymore.
                true
            }
            STATE_REQUESTED -> {
                workspace.onTouchEvent(ev)
                if (workspace.isHandlingTouch) {
                    cancelLongPress()
                }
                true
            }
            else -> {
                // We don't want to handle touch, let workspace handle it as usual.
                false
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            if (!workspace.isTouchActive) {
                val currentPage = workspace.getChildAt(workspace.currentPage) as? CellLayout
                if (currentPage != null) {
                    workspace.onWallpaperTap(ev)
                }
            }
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            cancelLongPress()
        }
        return result
    }

    private fun canHandleLongPress(): Boolean {
        return (AbstractFloatingView.getTopOpenView(launcher) == null
                && launcher.isInState(NORMAL))
    }

    private fun cancelLongPress() {
        workspace.removeCallbacks(this)
        longPressState = STATE_CANCELLED
    }

    override fun run() {
        if (longPressState == STATE_REQUESTED) {
            if (canHandleLongPress()) {
                longPressState = STATE_PENDING_PARENT_INFORM
                workspace.parent.requestDisallowInterceptTouchEvent(true)
                workspace.performHapticFeedback(LONG_PRESS, FLAG_IGNORE_VIEW_SETTING)
                launcher.userEventDispatcher.logActionOnContainer(LONGPRESS, NONE, WORKSPACE, workspace.currentPage)
                OptionsPopupView.showDefaultOptions(launcher, touchDownPoint.x, touchDownPoint.y)
            } else {
                cancelLongPress()
            }
        }
    }

    companion object {
        /**
         * STATE_PENDING_PARENT_INFORM is the state between longPress performed & the next motionEvent.
         * This next event is used to send an ACTION_CANCEL to Workspace, to that it clears any
         * temporary scroll state. After that, the state is set to COMPLETED, and we just eat up all
         * subsequent motion events.
         */
        private const val STATE_CANCELLED = 0
        private const val STATE_REQUESTED = 1
        private const val STATE_PENDING_PARENT_INFORM = 2
        private const val STATE_COMPLETED = 3
    }

}