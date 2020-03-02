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

import android.animation.*
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v4.graphics.ColorUtils
import android.support.v4.view.ViewCompat
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import android.support.v4.widget.ExploreByTouchHelper
import android.util.AttributeSet
import android.util.Property
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityManager
import com.android.launcher3.*
import com.android.launcher3.LauncherStateManager.StateListener
import com.android.launcher3.anim.Interpolators
import com.android.launcher3.uioverrides.WallpaperColorInfo
import com.android.launcher3.uioverrides.WallpaperColorInfo.OnChangeListener
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch.TAP
import com.android.launcher3.userevent.nano.LauncherLogProto.ControlType.ALL_APPS_BUTTON
import com.android.launcher3.util.getAttrColor
import kotlin.math.roundToInt

/**
 * Simple scrim which draws a flat color
 */
class ScrimView(context: Context, attrs: AttributeSet?) : View(context, attrs), Insettable, OnChangeListener, AccessibilityManager.AccessibilityStateChangeListener, StateListener {
    private val tempRect = Rect()
    private val tempPos = IntArray(2)
    private val launcher = Launcher.getLauncher(context)!!
    private val wallpaperColorInfo = WallpaperColorInfo.getInstance(context)
    private val accessibilityManager by lazy {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }
    private val endScrim: Int = getAttrColor(context, R.attr.allAppsScrimColor)
    private var maxScrimAlpha = 0.7f
    private var progress = 1f
    private var scrimColor = 0
    private var currentFlatColor = 0
    private var endFlatColor = 0
    private var endFlatColorAlpha = 0
    val dragHandleSize = context.resources.getDimensionPixelSize(R.dimen.vertical_drag_handle_size)
    private val dragHandleBounds = Rect(0, 0, dragHandleSize, dragHandleSize)
    private val hitRect = RectF()
    private val accessibilityHelper = AccessibilityHelper()
    private var dragHandle: Drawable? = null
    private var dragHandleAlpha = 255

    init {
        ViewCompat.setAccessibilityDelegate(this, accessibilityHelper)
        isFocusable = false
    }

    override fun setInsets(insets: Rect) {
        updateDragHandleBounds()
        updateDragHandleVisibility(null)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        updateDragHandleBounds()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        wallpaperColorInfo.addOnChangeListener(this)
        onExtractedColorsChanged(wallpaperColorInfo)
        accessibilityManager.addAccessibilityStateChangeListener(this)
        onAccessibilityStateChanged(accessibilityManager.isEnabled)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        wallpaperColorInfo.removeOnChangeListener(this)
        accessibilityManager.removeAccessibilityStateChangeListener(this)
    }

    override fun hasOverlappingRendering() = false

    override fun onExtractedColorsChanged(wallpaperColorInfo: WallpaperColorInfo) {
        scrimColor = wallpaperColorInfo.mainColor
        endFlatColor = ColorUtils.compositeColors(endScrim, ColorUtils.setAlphaComponent(
                scrimColor, (maxScrimAlpha * 255).roundToInt()))
        endFlatColorAlpha = Color.alpha(endFlatColor)
        updateColors()
        invalidate()
    }

    fun setProgress(progress: Float) {
        if (this.progress != progress) {
            this.progress = progress
            updateColors()
            updateDragHandleAlpha()
            invalidate()
        }
    }

    fun reInitUi() {}

    private fun updateColors() {
        currentFlatColor = if (progress >= 1) 0 else ColorUtils.setAlphaComponent(
                endFlatColor, ((1 - progress) * endFlatColorAlpha).roundToInt())
    }

    private fun updateDragHandleAlpha() {
        dragHandle?.alpha = dragHandleAlpha
    }

    private fun setDragHandleAlpha(alpha: Int) {
        if (alpha != dragHandleAlpha) {
            dragHandleAlpha = alpha
            dragHandle?.apply {
                this.alpha = dragHandleAlpha
                invalidate()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (currentFlatColor != 0) {
            canvas.drawColor(currentFlatColor)
        }
        dragHandle?.draw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val value = super.onTouchEvent(event)
        val dragHandle = this.dragHandle?: return value
        if (!value && event.action == MotionEvent.ACTION_DOWN && dragHandle.alpha == 255 && hitRect.contains(event.x, event.y)) {
            val drawable: Drawable = dragHandle
            this.dragHandle = null
            drawable.bounds = dragHandleBounds
            val topBounds = Rect(dragHandleBounds)
            topBounds.offset(0, -dragHandleBounds.height() / 2)
            val invalidateRegion = Rect(dragHandleBounds)
            invalidateRegion.top = topBounds.top
            val frameTop = Keyframe.ofObject(0.6f, topBounds)
            frameTop.interpolator = Interpolators.DEACCEL
            val frameBot = Keyframe.ofObject(1f, dragHandleBounds)
            frameBot.interpolator = Interpolators.ACCEL
            val holder = PropertyValuesHolder.ofKeyframe("bounds",
                    Keyframe.ofObject(0f, dragHandleBounds), frameTop, frameBot)
            holder.setEvaluator(RectEvaluator())
            val anim = ObjectAnimator.ofPropertyValuesHolder(drawable, holder)
            anim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    overlay.remove(drawable)
                    updateDragHandleVisibility(drawable)
                }
            })
            anim.addUpdateListener { invalidate(invalidateRegion) }
            overlay.add(drawable)
            anim.start()
        }
        return value
    }

    private fun updateDragHandleBounds() {
        val grid = launcher.deviceProfile
        val left: Int
        val width = measuredWidth
        val top = measuredHeight - dragHandleSize - grid.insets.bottom
        val topMargin: Int
        if (grid.isVerticalBarLayout) {
            topMargin = grid.workspacePadding.bottom
            left = if (grid.isSeascape) {
                width - grid.insets.right - dragHandleSize
            } else {
                dragHandleSize + grid.insets.left
            }
        } else {
            left = (width - dragHandleSize) / 2
            topMargin = grid.hotseatBarSizePx
        }
        dragHandleBounds.offsetTo(left, top - topMargin)
        hitRect.set(dragHandleBounds)
        val inset = -dragHandleSize / 2.toFloat()
        hitRect.inset(inset, inset)
        if (dragHandle != null) {
            dragHandle!!.bounds = dragHandleBounds
        }
    }

    override fun onAccessibilityStateChanged(enabled: Boolean) {
        val stateManager = launcher.stateManager
        stateManager.removeStateListener(this)
        if (enabled) {
            stateManager.addStateListener(this)
            onStateSetImmediately(launcher.stateManager.state)
        } else {
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        updateDragHandleVisibility(null)
    }

    private fun updateDragHandleVisibility(recycle: Drawable?) {
        val visible = launcher.deviceProfile.isVerticalBarLayout || accessibilityManager.isEnabled
        val wasVisible = dragHandle != null
        if (visible != wasVisible) {
            if (visible) {
                dragHandle = recycle ?: launcher.getDrawable(R.drawable.drag_handle_indicator)
                dragHandle!!.bounds = dragHandleBounds
                updateDragHandleAlpha()
            } else {
                dragHandle = null
            }
            invalidate()
        }
    }

    public override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        return accessibilityHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        return accessibilityHelper.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    public override fun onFocusChanged(gainFocus: Boolean, direction: Int,
                                       previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        accessibilityHelper.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    override fun onStateTransitionStart(toState: LauncherState) {}
    override fun onStateTransitionComplete(finalState: LauncherState) {
        onStateSetImmediately(finalState)
    }

    override fun onStateSetImmediately(state: LauncherState) {
        importantForAccessibility = if (state === LauncherState.ALL_APPS) IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else IMPORTANT_FOR_ACCESSIBILITY_AUTO
    }

    private inner class AccessibilityHelper : ExploreByTouchHelper(this@ScrimView) {
        override fun getVirtualViewAt(x: Float, y: Float): Int {
            return if (dragHandleBounds.contains(x.toInt(), y.toInt())) DRAG_HANDLE_ID else INVALID_ID
        }

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            virtualViewIds.add(DRAG_HANDLE_ID)
        }

        override fun onPopulateNodeForVirtualView(virtualViewId: Int,
                                                  node: AccessibilityNodeInfoCompat) {
            node.contentDescription = context.getString(R.string.all_apps_button_label)
            node.setBoundsInParent(dragHandleBounds)
            getLocationOnScreen(tempPos)
            tempRect.set(dragHandleBounds)
            tempRect.offset(tempPos[0], tempPos[1])
            node.setBoundsInScreen(tempRect)
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            node.isClickable = true
            node.isFocusable = true
            if (launcher.isInState(LauncherState.NORMAL)) {
                val context = context
                if (Utilities.isWallpaperAllowed(context)) {
                    node.addAction(
                            AccessibilityActionCompat(WALLPAPERS, context.getText(WALLPAPERS)))
                }
                node.addAction(AccessibilityActionCompat(WIDGETS, context.getText(WIDGETS)))
                node.addAction(AccessibilityActionCompat(SETTINGS, context.getText(SETTINGS)))
            }
        }

        override fun onPerformActionForVirtualView(
                virtualViewId: Int, action: Int, arguments: Bundle?): Boolean {
            return when (action) {
                AccessibilityNodeInfoCompat.ACTION_CLICK -> {
                    launcher.userEventDispatcher.logActionOnControl(TAP, ALL_APPS_BUTTON,
                            launcher.stateManager.state.containerType)
                    launcher.stateManager.goToState(LauncherState.ALL_APPS)
                    true
                }
                WALLPAPERS -> OptionsPopupView.startWallpaperPicker(this@ScrimView)
                WIDGETS -> OptionsPopupView.onWidgetsClicked(this@ScrimView)
                SETTINGS -> OptionsPopupView.startSettings(this@ScrimView)
                else -> false
            }
        }
    }

    companion object {
        @JvmField
        val DRAG_HANDLE_ALPHA = object : Property<ScrimView, Int>(Integer.TYPE, "dragHandleAlpha") {
            override fun get(scrimView: ScrimView): Int {
                return scrimView.dragHandleAlpha
            }

            override fun set(scrimView: ScrimView, value: Int) {
                scrimView.setDragHandleAlpha(value)
            }
        }
        private const val WALLPAPERS = R.string.wallpaper_button_text
        private const val WIDGETS = R.string.widget_button_text
        private const val SETTINGS = R.string.settings_button_text

        private const val DRAG_HANDLE_ID = 1
    }
}