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

import android.animation.*
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.support.v4.graphics.ColorUtils
import android.util.Property
import android.view.View
import android.view.animation.AnimationUtils
import com.android.launcher3.*
import com.android.launcher3.anim.PropertyResetListener
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider
import com.android.launcher3.util.getAttrColor
import com.android.launcher3.views.BaseDragLayerLayoutParams
import kotlin.math.roundToInt

/**
 * Manages the opening and closing animations for a [Folder].
 *
 * All of the animations are done in the Folder.
 * ie. When the user taps on the FolderIcon, we immediately hide the FolderIcon and show the Folder
 * in its place before starting the animation.
 */
class FolderAnimationManager(
        private val folder: Folder,
        private val isOpening: Boolean
) {
    private val content = folder.getContent()
    private val folderBackground = folder.background as GradientDrawable
    private val folderIcon = folder.folderIcon
    private val previewBackground = folderIcon.background
    private val context = folder.context
    private val launcher = folder.launcher
    private val duration = content.resources.getInteger(R.integer.config_materialFolderExpandDuration)
    private val delay = content.resources.getInteger(R.integer.config_folderDelay)
    private val folderInterpolator = AnimationUtils.loadInterpolator(context, R.interpolator.folder_interpolator)
    private val largeFolderPreviewItemOpenInterpolator = AnimationUtils.loadInterpolator(context,
            R.interpolator.large_folder_preview_item_open_interpolator)
    private val largeFolderPreviewItemCloseInterpolator = AnimationUtils.loadInterpolator(context,
            R.interpolator.large_folder_preview_item_close_interpolator)
    private val tmpParams = PreviewItemDrawingParams(0f, 0f, 0f, 0f)
    // Background can have a scaled radius in drag and drop mode, so we need to add the

    /**
     * Prepares the Folder for animating between open / closed states.
     */
    val animator: AnimatorSet
        get() {
            val lp = folder.layoutParams as BaseDragLayerLayoutParams
            val rule = folderIcon.layoutRule
            val itemsInPreview = folderIcon.previewItems

            // Match position of the FolderIcon
            val folderIconPos = Rect()
            val scaleRelativeToDragLayer = launcher.dragLayer
                    .getDescendantRectRelativeToSelf(folderIcon, folderIconPos)
            val scaledRadius = previewBackground.scaledRadius
            val initialSize = scaledRadius * 2 * scaleRelativeToDragLayer

            // Match size/scale of icons in the preview
            val previewScale = rule.scaleForItem(itemsInPreview.size)
            val previewSize = rule.iconSize * previewScale
            val initialScale = (previewSize / itemsInPreview[0].iconSize
                    * scaleRelativeToDragLayer)
            val finalScale = 1f
            val scale = if (isOpening) initialScale else finalScale
            folder.scaleX = scale
            folder.scaleY = scale
            folder.pivotX = 0f
            folder.pivotY = 0f

            // We want to create a small X offset for the preview items, so that they follow their
            // expected path to their final locations. ie. an icon should not move right, if it's final
            // location is to its left. This value is arbitrarily defined.
            var previewItemOffsetX = (previewSize / 2).toInt()
            if (Utilities.isRtl(context.resources)) {
                previewItemOffsetX = (lp.width * initialScale - initialSize - previewItemOffsetX).toInt()
            }
            val paddingOffsetX = ((folder.paddingLeft + content.paddingLeft)
                    * initialScale).toInt()
            val paddingOffsetY = ((folder.paddingTop + content.paddingTop)
                    * initialScale).toInt()
            val initialX = (folderIconPos.left + previewBackground.offsetX - paddingOffsetX
                    - previewItemOffsetX)
            val initialY = folderIconPos.top + previewBackground.offsetY - paddingOffsetY
            val xDistance = initialX - lp.x.toFloat()
            val yDistance = initialY - lp.y.toFloat()

            // Set up the Folder background.
            val finalColor = getAttrColor(context, android.R.attr.colorPrimary)
            val initialColor = ColorUtils.setAlphaComponent(finalColor, previewBackground.backgroundAlpha)
            folderBackground.setColor(if (isOpening) initialColor else finalColor)

            // Set up the reveal animation that clips the Folder.
            val totalOffsetX = paddingOffsetX + previewItemOffsetX
            val startRect = Rect(
                    (totalOffsetX / initialScale).roundToInt(),
                    (paddingOffsetY / initialScale).roundToInt(),
                    ((totalOffsetX + initialSize) / initialScale).roundToInt(),
                    ((paddingOffsetY + initialSize) / initialScale).roundToInt())
            val endRect = Rect(0, 0, lp.width, lp.height)
            val initialRadius = initialSize / initialScale / 2f
            val finalRadius = Utilities.pxFromDp(2f, context.resources.displayMetrics).toFloat()

            // Create the animators.
            val a = LauncherAnimUtils.createAnimatorSet()

            // Initialize the Folder items' text.
            val colorResetListener: PropertyResetListener<*, *> = PropertyResetListener(BubbleTextView.TEXT_ALPHA_PROPERTY, 1f)
            for (icon in folder.getItemsOnPage(folder.getContent().currentPage)) {
                if (isOpening) {
                    icon.setTextVisibility(false)
                }
                val anim = icon.createTextAlphaAnimator(isOpening)
                anim.addListener(colorResetListener)
                play(a, anim)
            }
            play(a, getAnimator(folder, View.TRANSLATION_X, xDistance, 0f))
            play(a, getAnimator(folder, View.TRANSLATION_Y, yDistance, 0f))
            play(a, getAnimator(folder, LauncherAnimUtils.SCALE_PROPERTY, initialScale, finalScale))
            play(a, getAnimator(folderBackground, "color", initialColor, finalColor))
            play(a, folderIcon.getName().createTextAlphaAnimator(!isOpening))
            val outlineProvider: RoundedRectRevealOutlineProvider = object : RoundedRectRevealOutlineProvider(
                    initialRadius, finalRadius, startRect, endRect) {
                override fun shouldRemoveElevationDuringAnimation(): Boolean {
                    return true
                }
            }
            play(a, outlineProvider.createRevealAnimator(folder, !isOpening))

            // Animate the elevation midway so that the shadow is not noticeable in the background.
            val midDuration = duration / 2
            val z = getAnimator(folder, View.TRANSLATION_Z, -folder.elevation, 0f)
            play(a, z, if (isOpening) midDuration.toLong() else 0.toLong(), midDuration)
            a.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    folder.translationX = 0.0f
                    folder.translationY = 0.0f
                    folder.translationZ = 0.0f
                    folder.scaleX = 1f
                    folder.scaleY = 1f
                }
            })

            // We set the interpolator on all current child animators here, because the preview item
            // animators may use a different interpolator.
            for (animator in a.childAnimations) {
                animator.interpolator = folderInterpolator
            }
            val radiusDiff = scaledRadius - previewBackground.radius
            addPreviewItemAnimators(a, initialScale / scaleRelativeToDragLayer,  // Background can have a scaled radius in drag and drop mode, so we need to add the
                    // difference to keep the preview items centered.
                    previewItemOffsetX + radiusDiff, radiusDiff)
            return a
        }

    /**
     * Animate the items on the current page.
     */
    private fun addPreviewItemAnimators(animatorSet: AnimatorSet, folderScale: Float,
                                        previewItemOffsetX: Int, previewItemOffsetY: Int) {
        val rule = folderIcon.layoutRule
        val isOnFirstPage = folder.getContent().currentPage == 0
        val itemsInPreview = if (isOnFirstPage) folderIcon.previewItems else folderIcon.getPreviewItemsOnPage(folder.getContent().currentPage)
        val numItemsInPreview = itemsInPreview.size
        val numItemsInFirstPagePreview = if (isOnFirstPage) numItemsInPreview else ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW
        val previewItemInterpolator = previewItemInterpolator
        val cwc = content.getPageAt(0).shortcutsAndWidgets
        for (i in 0 until numItemsInPreview) {
            val btv = itemsInPreview[i]
            val btvLp = btv.layoutParams as CellLayout.LayoutParams

            // Calculate the final values in the LayoutParams.
            btvLp.isLockedToGrid = true
            cwc.setupLp(btv)

            // Match scale of icons in the preview of the items on the first page.
            val previewScale = rule.scaleForItem(numItemsInFirstPagePreview)
            val previewSize = rule.iconSize * previewScale
            val iconScale = previewSize / itemsInPreview[i].iconSize
            val initialScale = iconScale / folderScale
            val finalScale = 1f
            val scale = if (isOpening) initialScale else finalScale
            btv.scaleX = scale
            btv.scaleY = scale

            // Match positions of the icons in the folder with their positions in the preview
            rule.computePreviewItemDrawingParams(i, numItemsInFirstPagePreview, tmpParams)
            // The PreviewLayoutRule assumes that the icon size takes up the entire width so we
            // offset by the actual size.
            val iconOffsetX = ((btvLp.width - btv.iconSize) * iconScale).toInt() / 2
            val previewPosX = ((tmpParams.transX - iconOffsetX + previewItemOffsetX) / folderScale).toInt()
            val previewPosY = ((tmpParams.transY + previewItemOffsetY) / folderScale).toInt()
            val xDistance = previewPosX - btvLp.x.toFloat()
            val yDistance = previewPosY - btvLp.y.toFloat()
            val translationX = getAnimator(btv, View.TRANSLATION_X, xDistance, 0f)
            translationX.interpolator = previewItemInterpolator
            play(animatorSet, translationX)
            val translationY = getAnimator(btv, View.TRANSLATION_Y, yDistance, 0f)
            translationY.interpolator = previewItemInterpolator
            play(animatorSet, translationY)
            val scaleAnimator = getAnimator(btv, LauncherAnimUtils.SCALE_PROPERTY, initialScale, finalScale)
            scaleAnimator.interpolator = previewItemInterpolator
            play(animatorSet, scaleAnimator)
            if (folder.itemCount > ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW) {
                // These delays allows the preview items to move as part of the Folder's motion,
                // and its only necessary for large folders because of differing interpolators.
                val delay = if (isOpening) delay else delay * 2
                if (isOpening) {
                    translationX.startDelay = delay.toLong()
                    translationY.startDelay = delay.toLong()
                    scaleAnimator.startDelay = delay.toLong()
                }
                translationX.duration = translationX.duration - delay
                translationY.duration = translationY.duration - delay
                scaleAnimator.duration = scaleAnimator.duration - delay
            }
            animatorSet.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    super.onAnimationStart(animation)
                    // Necessary to initialize values here because of the start delay.
                    if (isOpening) {
                        btv.translationX = xDistance
                        btv.translationY = yDistance
                        btv.scaleX = initialScale
                        btv.scaleY = initialScale
                    }
                }

                override fun onAnimationEnd(animation: Animator) {
                    super.onAnimationEnd(animation)
                    btv.translationX = 0.0f
                    btv.translationY = 0.0f
                    btv.scaleX = 1f
                    btv.scaleY = 1f
                }
            })
        }
    }

    private fun play(`as`: AnimatorSet, a: Animator, startDelay: Long = a.startDelay, duration: Int = this.duration) {
        a.startDelay = startDelay
        a.duration = duration.toLong()
        `as`.play(a)
    }

    // With larger folders, we want the preview items to reach their final positions faster
    // (when opening) and later (when closing) so that they appear aligned with the rest of
    // the folder items when they are both visible.
    private val previewItemInterpolator: TimeInterpolator
        get() = if (folder.itemCount > ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW) {
            // With larger folders, we want the preview items to reach their final positions faster
            // (when opening) and later (when closing) so that they appear aligned with the rest of
            // the folder items when they are both visible.
            if (isOpening) largeFolderPreviewItemOpenInterpolator else largeFolderPreviewItemCloseInterpolator
        } else folderInterpolator

    private fun getAnimator(view: View, property: Property<View, Float>, v1: Float, v2: Float): Animator {
        return if (isOpening) ObjectAnimator.ofFloat<View>(view, property, v1, v2) else ObjectAnimator.ofFloat<View>(view, property, v2, v1)
    }

    private fun getAnimator(drawable: GradientDrawable, property: String, v1: Int, v2: Int): Animator {
        return if (isOpening) ObjectAnimator.ofArgb(drawable, property, v1, v2) else ObjectAnimator.ofArgb(drawable, property, v2, v1)
    }
}