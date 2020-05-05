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
package com.android.launcher3.popup

import android.animation.*
import android.content.Context
import android.graphics.CornerPathEffect
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.drawable.ShapeDrawable
import android.util.AttributeSet
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import com.android.launcher3.*
import com.android.launcher3.anim.RevealOutlineAnimation
import com.android.launcher3.anim.RoundedRectRevealOutlineProvider
import com.android.launcher3.graphics.TriangleShape
import com.android.launcher3.util.getAttrColor

/**
 * A container for shortcuts to deep links and notifications associated with an app.
 */
abstract class ArrowPopup
@JvmOverloads
constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : AbstractFloatingView(context, attrs, defStyleAttr) {

    private val tempRect = Rect()
    protected val inflater = LayoutInflater.from(context)!!
    private val outlineRadius = resources.getDimension(R.dimen.bg_round_rect_radius)

    @JvmField
    protected val launcher = Launcher.getLauncher(context)
    protected val isRtl = Utilities.isRtl(resources)
    private val arrayOffset: Int = resources.getDimensionPixelSize(R.dimen.popup_arrow_vertical_offset)
    // Initialize arrow view
    private val arrow = View(context).apply {
        val arrowWidth = resources.getDimensionPixelSize(R.dimen.popup_arrow_width)
        val arrowHeight = resources.getDimensionPixelSize(R.dimen.popup_arrow_height)
        layoutParams = InsettableFrameLayout.LayoutParams(arrowWidth, arrowHeight)
    }
    protected var isLeftAligned = false
    @JvmField
    protected var isAboveIcon = false
    private var mGravity = 0
    @JvmField
    protected var openCloseAnimator: Animator? = null
    @JvmField
    protected var deferContainerRemoval = false
    private val startRect = Rect()
    private val endRect = Rect()

    init {
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, outlineRadius)
            }
        }
    }

    override fun handleClose(animate: Boolean) {
        if (animate) {
            animateClose()
        } else {
            closeComplete()
        }
    }

    fun <T : View?> inflateAndAdd(resId: Int, container: ViewGroup): T {
        val view = inflater.inflate(resId, container, false)
        container.addView(view)
        return view as T
    }

    /**
     * Called when all view inflation and reordering in complete.
     */
    protected open fun onInflationComplete(isReversed: Boolean) {}

    /**
     * Shows the popup at the desired location, optionally reversing the children.
     * @param viewsToFlip number of views from the top to to flip in case of reverse order
     */
    protected fun reorderAndShow(viewsToFlip: Int) {
        visibility = View.INVISIBLE
        isOpen = true
        launcher.dragLayer.addView(this)
        orientAboutObject()
        val reverseOrder = isAboveIcon
        if (reverseOrder) {
            val count = childCount
            val allViews = mutableListOf<View>()
            for (i in 0 until count) {
                if (i == viewsToFlip) {
                    allViews.reverse()
                }
                allViews.add(getChildAt(i))
            }
            allViews.reverse()
            removeAllViews()
            for (i in 0 until count) {
                addView(allViews[i])
            }
            orientAboutObject()
        }
        onInflationComplete(reverseOrder)

        // Add the arrow.
        val res = resources
        val arrowCenterOffset = res.getDimensionPixelSize(if (isAlignedWithStart) R.dimen.popup_arrow_horizontal_center_start else R.dimen.popup_arrow_horizontal_center_end)
        val halfArrowWidth = res.getDimensionPixelSize(R.dimen.popup_arrow_width) / 2
        launcher.dragLayer.addView(arrow)
        val arrowLp = arrow.layoutParams as InsettableFrameLayout.LayoutParams
        if (isLeftAligned) {
            arrow.x = x + arrowCenterOffset - halfArrowWidth
        } else {
            arrow.x = x + measuredWidth - arrowCenterOffset - halfArrowWidth
        }
        if (Gravity.isVertical(mGravity)) {
            // This is only true if there wasn't room for the container next to the icon,
            // so we centered it instead. In that case we don't want to showDefaultOptions the arrow.
            arrow.visibility = View.INVISIBLE
        } else {
            val arrowDrawable = ShapeDrawable(TriangleShape.create(
                    arrowLp.width.toFloat(), arrowLp.height.toFloat(), !isAboveIcon))
            val arrowPaint = arrowDrawable.paint
            arrowPaint.color = getAttrColor(launcher, R.attr.popupColorPrimary)
            // The corner path effect won't be reflected in the shadow, but shouldn't be noticeable.
            val radius = resources.getDimensionPixelSize(R.dimen.popup_arrow_corner_radius)
            arrowPaint.pathEffect = CornerPathEffect(radius.toFloat())
            arrow.background = arrowDrawable
            arrow.elevation = elevation
        }
        arrow.pivotX = arrowLp.width / 2f
        arrow.pivotY = if (isAboveIcon) 0f else arrowLp.height.toFloat()
        animateOpen()
    }

    protected val isAlignedWithStart: Boolean
        get() = isLeftAligned xor isRtl

    /**
     * Provide the location of the target object relative to the dragLayer.
     */
    protected abstract fun getTargetObjectLocation(outPos: Rect?)

    /**
     * Orients this container above or below the given icon, aligning with the left or right.
     *
     * These are the preferred orientations, in order (RTL prefers right-aligned over left):
     * - Above and left-aligned
     * - Above and right-aligned
     * - Below and left-aligned
     * - Below and right-aligned
     *
     * So we always align left if there is enough horizontal space
     * and align above if there is enough vertical space.
     */
    private fun orientAboutObject() {
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED)
        val width = measuredWidth
        val extraVerticalSpace = (arrow.layoutParams.height + arrayOffset
                + resources.getDimensionPixelSize(R.dimen.popup_vertical_padding))
        val height = measuredHeight + extraVerticalSpace
        getTargetObjectLocation(tempRect)
        val dragLayer = launcher.dragLayer
        val insets = dragLayer.insets

        // Align left (right in RTL) if there is room.
        val leftAlignedX = tempRect.left
        val rightAlignedX = tempRect.right - width
        var x = leftAlignedX
        val canBeLeftAligned = (leftAlignedX + width + insets.left
                < dragLayer.right - insets.right)
        val canBeRightAligned = rightAlignedX > dragLayer.left + insets.left
        if (!canBeLeftAligned || isRtl && canBeRightAligned) {
            x = rightAlignedX
        }
        isLeftAligned = x == leftAlignedX

        // Offset x so that the arrow and shortcut icons are center-aligned with the original icon.
        val iconWidth = tempRect.width()
        val xOffset: Int
        xOffset = if (isAlignedWithStart) {
            // Aligning with the shortcut icon.
            val shortcutIconWidth = resources.getDimensionPixelSize(R.dimen.deep_shortcut_icon_size)
            val shortcutPaddingStart = resources.getDimensionPixelSize(
                    R.dimen.popup_padding_start)
            iconWidth / 2 - shortcutIconWidth / 2 - shortcutPaddingStart
        } else {
            // Aligning with the drag handle.
            val shortcutDragHandleWidth = resources.getDimensionPixelSize(
                    R.dimen.deep_shortcut_drag_handle_size)
            val shortcutPaddingEnd = resources.getDimensionPixelSize(
                    R.dimen.popup_padding_end)
            iconWidth / 2 - shortcutDragHandleWidth / 2 - shortcutPaddingEnd
        }
        x += if (isLeftAligned) xOffset else -xOffset

        // Open above icon if there is room.
        val iconHeight = tempRect.height()
        var y = tempRect.top - height
        isAboveIcon = y > dragLayer.top + insets.top
        if (!isAboveIcon) {
            y = tempRect.top + iconHeight + extraVerticalSpace
        }

        // Insets are added later, so subtract them now.
        x = if (isRtl) { x + insets.right } else { x - insets.left }
        y -= insets.top
        mGravity = 0
        if (y + height > dragLayer.bottom - insets.bottom) {
            // The container is opening off the screen, so just center it in the drag layer instead.
            mGravity = Gravity.CENTER_VERTICAL
            // Put the container next to the icon, preferring the right side in ltr (left in rtl).
            val rightSide = leftAlignedX + iconWidth - insets.left
            val leftSide = rightAlignedX - iconWidth - insets.left
            if (!isRtl) {
                if (rightSide + width < dragLayer.right) {
                    x = rightSide
                    isLeftAligned = true
                } else {
                    x = leftSide
                    isLeftAligned = false
                }
            } else {
                if (leftSide > dragLayer.left) {
                    x = leftSide
                    isLeftAligned = false
                } else {
                    x = rightSide
                    isLeftAligned = true
                }
            }
            isAboveIcon = true
        }
        setX(x.toFloat())
        if (Gravity.isVertical(mGravity)) {
            return
        }
        val lp = layoutParams as InsettableFrameLayout.LayoutParams
        val arrowLp = arrow.layoutParams as InsettableFrameLayout.LayoutParams
        if (isAboveIcon) {
            lp.gravity = Gravity.BOTTOM
            arrowLp.gravity = lp.gravity
            lp.bottomMargin = launcher.dragLayer.height - y - measuredHeight - insets.top
            arrowLp.bottomMargin = lp.bottomMargin - arrowLp.height - arrayOffset - insets.bottom
        } else {
            lp.gravity = Gravity.TOP
            arrowLp.gravity = lp.gravity
            lp.topMargin = y + insets.top
            arrowLp.topMargin = lp.topMargin - insets.top - arrowLp.height - arrayOffset
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)

        // enforce contained is within screen
        val dragLayer = launcher.dragLayer
        if (translationX + l < 0 || translationX + r > dragLayer.width) {
            // If we are still off screen, center horizontally too.
            mGravity = mGravity or Gravity.CENTER_HORIZONTAL
        }
        if (Gravity.isHorizontal(mGravity)) {
            x = dragLayer.width / 2 - measuredWidth / 2.toFloat()
            arrow.visibility = View.INVISIBLE
        }
        if (Gravity.isVertical(mGravity)) {
            y = dragLayer.height / 2 - measuredHeight / 2.toFloat()
        }
    }

    private fun animateOpen() {
        LauncherAnimUtils.createAnimatorSet().apply {
            openCloseAnimator = this
            play(getFadeInAnimator())
            playSequentially(getRevealAnimator(false), getScaleAnimator())
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    visibility = View.VISIBLE
                    arrow.scaleX = 0f
                    arrow.scaleY = 0f
                }

                override fun onAnimationEnd(animation: Animator) {
                    announceAccessibilityChanges()
                    openCloseAnimator = null
                }
            })
            start()
        }
    }

    private fun getFadeInAnimator(): Animator {
        val revealDuration = resources.getInteger(R.integer.config_popupOpenCloseDuration).toLong()
        val revealInterpolator = AccelerateDecelerateInterpolator()

        return ObjectAnimator.ofFloat(this, View.ALPHA, 0f, 1f).apply {
            duration = revealDuration
            interpolator = revealInterpolator
        }
    }

    private fun getFadeOutAnimator(): Animator {
        val revealInterpolator = AccelerateDecelerateInterpolator()

        return ObjectAnimator.ofFloat(this, View.ALPHA, 0f).apply {
            interpolator = revealInterpolator
        }
    }

    private fun getRevealAnimator(isReversed: Boolean): Animator {
        val revealDuration = resources.getInteger(R.integer.config_popupOpenCloseDuration).toLong()
        val revealInterpolator = AccelerateDecelerateInterpolator()

        // Rectangular reveal.
        return createOpenCloseOutlineProvider()
                .createRevealAnimator(this, isReversed).apply {
                    duration = revealDuration
                    interpolator = revealInterpolator
                }
    }

    private fun getScaleAnimator(): Animator {
        // Animate the arrow.
        return ObjectAnimator.ofFloat(arrow, LauncherAnimUtils.SCALE_PROPERTY, 1f).apply {
            duration = resources.getInteger(R.integer.config_popupArrowOpenDuration).toLong()
        }
    }

    protected fun animateClose() {
        if (!isOpen) return

        endRect.setEmpty()
        (outlineProvider as? RevealOutlineAnimation)?.getOutline(endRect)
        openCloseAnimator?.cancel()

        isOpen = false
        LauncherAnimUtils.createAnimatorSet().apply {
            // Hide the arrow
            play(ObjectAnimator.ofFloat(arrow, LauncherAnimUtils.SCALE_PROPERTY, 0f))
            play(ObjectAnimator.ofFloat(arrow, View.ALPHA, 0f))

            // Rectangular reveal (reversed).
            play(getRevealAnimator(true))
            play(getFadeOutAnimator())
            onCreateCloseAnimation(this)

            duration = resources.getInteger(R.integer.config_popupOpenCloseDuration).toLong()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    openCloseAnimator = null
                    if (deferContainerRemoval) {
                        visibility = View.INVISIBLE
                    } else {
                        closeComplete()
                    }
                }
            })
            openCloseAnimator = this
            start()
        }
    }

    /**
     * Called when creating the close transition allowing subclass can add additional animations.
     */
    protected open fun onCreateCloseAnimation(anim: AnimatorSet?) {}

    private fun createOpenCloseOutlineProvider(): RoundedRectRevealOutlineProvider {
        var arrowCenterX = resources.getDimensionPixelSize(if (isAlignedWithStart) R.dimen.popup_arrow_horizontal_center_start else R.dimen.popup_arrow_horizontal_center_end)
        if (!isLeftAligned) {
            arrowCenterX = measuredWidth - arrowCenterX
        }
        val arrowCenterY = if (isAboveIcon) measuredHeight else 0
        startRect[arrowCenterX, arrowCenterY, arrowCenterX] = arrowCenterY
        if (endRect.isEmpty) {
            endRect[0, 0, measuredWidth] = measuredHeight
        }
        return RoundedRectRevealOutlineProvider(outlineRadius, outlineRadius, startRect, endRect)
    }

    /**
     * Closes the popup without animation.
     */
    protected open fun closeComplete() {
        if (openCloseAnimator != null) {
            openCloseAnimator!!.cancel()
            openCloseAnimator = null
        }
        isOpen = false
        deferContainerRemoval = false
        launcher.dragLayer.removeView(this)
        launcher.dragLayer.removeView(arrow)
    }
}