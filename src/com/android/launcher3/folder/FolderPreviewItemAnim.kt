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
import com.android.launcher3.LauncherAnimUtils

/**
 * Animates a Folder preview item.
 */
internal class FolderPreviewItemAnim(previewItemManager: PreviewItemManager,
                                     params: PreviewItemDrawingParams, index0: Int, items0: Int, index1: Int, items1: Int,
                                     duration: Int, onCompleteRunnable: Runnable?) {
    private val animator: ValueAnimator
    @JvmField
    var finalScale: Float
    @JvmField
    var finalTransX: Float
    @JvmField
    var finalTransY: Float

    /**
     * @param params layout params to animate
     * @param index0 original index of the item to be animated
     * @param items0 original number of items in the preview
     * @param index1 new index of the item to be animated
     * @param items1 new number of items in the preview
     * @param duration duration in ms of the animation
     * @param onCompleteRunnable runnable to execute upon animation completion
     */
    init {
        previewItemManager.computePreviewItemDrawingParams(index1, items1, sTmpParams)
        finalScale = sTmpParams.scale
        finalTransX = sTmpParams.transX
        finalTransY = sTmpParams.transY
        previewItemManager.computePreviewItemDrawingParams(index0, items0, sTmpParams)
        val scale0 = sTmpParams.scale
        val transX0 = sTmpParams.transX
        val transY0 = sTmpParams.transY
        animator = LauncherAnimUtils.ofFloat(0f, 1.0f).apply {
            addUpdateListener { animation ->
                val progress = animation.animatedFraction
                params.transX = transX0 + progress * (finalTransX - transX0)
                params.transY = transY0 + progress * (finalTransY - transY0)
                params.scale = scale0 + progress * (finalScale - scale0)
                previewItemManager.onParamsChanged()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onCompleteRunnable?.run()
                    params.anim = null
                }
            })
            this.duration = duration.toLong()
        }
    }

    fun start() {
        animator.start()
    }

    fun cancel() {
        animator.cancel()
    }

    fun hasEqualFinalState(anim: FolderPreviewItemAnim): Boolean {
        return finalTransY == anim.finalTransY && finalTransX == anim.finalTransX && finalScale == anim.finalScale
    }

    companion object {
        private val sTmpParams = PreviewItemDrawingParams(0f, 0f, 0f, 0f)
    }
}