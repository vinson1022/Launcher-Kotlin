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
package com.android.launcher3.states

import com.android.launcher3.InstallShortcutReceiver
import com.android.launcher3.InstallShortcutReceiver.FLAG_DRAG_AND_DROP
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAnimUtils
import com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_TRANSITION_MS
import com.android.launcher3.LauncherState
import com.android.launcher3.userevent.nano.LauncherLogProto

/**
 * Definition for spring loaded state used during drag and drop.
 */
class SpringLoadedState(id: Int) : LauncherState(id, LauncherLogProto.ContainerType.OVERVIEW, SPRING_LOADED_TRANSITION_MS, STATE_FLAGS) {
    override fun getWorkspaceScaleAndTranslation(launcher: Launcher): FloatArray {
        val grid = launcher.deviceProfile
        val ws = launcher.workspace
        if (ws.childCount == 0) {
            return super.getWorkspaceScaleAndTranslation(launcher)
        }
        if (grid.isVerticalBarLayout) {
            val scale = grid.workspaceSpringLoadShrinkFactor
            return floatArrayOf(scale, 0f, 0f)
        }
        val scale = grid.workspaceSpringLoadShrinkFactor
        val insets = launcher.dragLayer.insets
        val scaledHeight = scale * ws.normalChildHeight
        val shrunkTop = insets.top + grid.dropTargetBarSizePx.toFloat()
        val shrunkBottom = (ws.measuredHeight - insets.bottom
                - grid.workspacePadding.bottom
                - grid.workspaceSpringLoadedBottomSpace).toFloat()
        val totalShrunkSpace = shrunkBottom - shrunkTop
        val desiredCellTop = shrunkTop + (totalShrunkSpace - scaledHeight) / 2
        val halfHeight = ws.height / 2.toFloat()
        val myCenter = ws.top + halfHeight
        val cellTopFromCenter = halfHeight - ws.getChildAt(0).top
        val actualCellTop = myCenter - cellTopFromCenter * scale
        return floatArrayOf(scale, 0f, (desiredCellTop - actualCellTop) / scale)
    }

    override fun onStateEnabled(launcher: Launcher) {
        val ws = launcher.workspace
        ws.showPageIndicatorAtCurrentScroll()
        ws.pageIndicator.setShouldAutoHide(false)
        // Prevent any Un/InstallShortcutReceivers from updating the db while we are
        // in spring loaded mode
        InstallShortcutReceiver.enableInstallQueue(FLAG_DRAG_AND_DROP)
        launcher.rotationHelper.setCurrentStateRequest(RotationHelper.REQUEST_LOCK)
    }

    override fun getWorkspaceScrimAlpha(launcher: Launcher) = 0.3f

    override fun onStateDisabled(launcher: Launcher) {
        launcher.workspace.pageIndicator.setShouldAutoHide(true)
        // Re-enable any Un/InstallShortcutReceiver and now process any queued items
        InstallShortcutReceiver.disableAndFlushInstallQueue(FLAG_DRAG_AND_DROP, launcher)
    }

    companion object {
        private const val STATE_FLAGS = FLAG_MULTI_PAGE or
                FLAG_DISABLE_ACCESSIBILITY or FLAG_DISABLE_RESTORE or FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED or
                FLAG_DISABLE_PAGE_CLIPPING or FLAG_PAGE_BACKGROUNDS or FLAG_HIDE_BACK_BUTTON
    }
}