/*
 * Copyright (C) 2009 The Android Open Source Project
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
package com.android.launcher3.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Configuration
import android.graphics.PointF
import android.graphics.Rect
import android.os.SystemClock
import android.util.SparseBooleanArray
import android.view.*
import android.view.View.OnLongClickListener
import android.view.ViewDebug.ExportedProperty
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.AdapterView
import android.widget.Advanceable
import android.widget.RemoteViews
import com.android.launcher3.*
import com.android.launcher3.util.aboveApi26
import com.android.launcher3.views.BaseDragLayer.TouchCompleteListener

/**
 * {@inheritDoc}
 */
open class LauncherAppWidgetHostView(context: Context?) : AppWidgetHostView(context), TouchCompleteListener, OnLongClickListener {
    @JvmField
    protected val inflater: LayoutInflater = LayoutInflater.from(context)
    private val longPressHelper = CheckLongPressHelper(this, this)
    private val stylusEventHelper = StylusEventHelper(SimpleOnStylusPressListener(this), this)

    @JvmField
    protected val launcher: Launcher = Launcher.getLauncher(context)

    @ExportedProperty(category = "launcher")
    private var reinflateOnConfigChange = false
    private var slop = 0f

    @ExportedProperty(category = "launcher")
    private var childrenFocused = false
    private var isScrollable = false
    private var _isAttachedToWindow = false
    private var isAutoAdvanceRegistered = false
    private var autoAdvanceRunnable = Runnable { runAutoAdvance() }

    /**
     * The scaleX and scaleY value such that the widget fits within its cellspans, scaleX = scaleY.
     */
    var scaleToFit = 1f
        set(scale) {
            field = scale
            scaleX = scale
            scaleY = scale
        }

    /**
     * The translation values to center the widget within its cellspans.
     */
    val translationForCentering = PointF(0f, 0f)

    override fun onFinishInflate() {
        super.onFinishInflate()
        accessibilityDelegate = launcher.accessibilityDelegate
        setBackgroundResource(R.drawable.widget_internal_focus_bg)
        aboveApi26 {
            setExecutor(Utilities.THREAD_POOL_EXECUTOR)
        }
    }

    override fun onLongClick(view: View): Boolean {
        if (isScrollable) {
            val dragLayer = Launcher.getLauncher(context).dragLayer
            dragLayer.requestDisallowInterceptTouchEvent(false)
        }
        view.performLongClick()
        return true
    }

    override fun getErrorView(): View {
        return inflater.inflate(R.layout.appwidget_error, this, false)
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)

        // The provider info or the views might have changed.
        checkIfAutoAdvance()

        // It is possible that widgets can receive updates while launcher is not in the foreground.
        // Consequently, the widgets will be inflated for the orientation of the foreground activity
        // (framework issue). On resuming, we ensure that any widgets are inflated for the current
        // orientation.
        reinflateOnConfigChange = !isSameOrientation
    }

    private val isSameOrientation: Boolean
        get() = launcher.resources.configuration.orientation == launcher.orientation

    private fun checkScrollableRecursively(viewGroup: ViewGroup): Boolean {
        if (viewGroup is AdapterView<*>) {
            return true
        } else {
            for (i in 0 until viewGroup.childCount) {
                val child = viewGroup.getChildAt(i)
                if (child is ViewGroup) {
                    if (checkScrollableRecursively(child)) {
                        return true
                    }
                }
            }
        }
        return false
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Just in case the previous long press hasn't been cleared, we make sure to start fresh
        // on touch down.
        if (ev.action == MotionEvent.ACTION_DOWN) {
            longPressHelper.cancelLongPress()
        }

        // Consume any touch events for ourselves after longpress is triggered
        if (longPressHelper.hasPerformedLongPress()) {
            longPressHelper.cancelLongPress()
            return true
        }

        // Watch for longpress or stylus button press events at this level to
        // make sure users can always pick up this widget
        if (stylusEventHelper.onMotionEvent(ev)) {
            longPressHelper.cancelLongPress()
            return true
        }
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                val dragLayer = Launcher.getLauncher(context).dragLayer
                if (isScrollable) {
                    dragLayer.requestDisallowInterceptTouchEvent(true)
                }
                if (!stylusEventHelper.inStylusButtonPressed()) {
                    longPressHelper.postCheckForLongPress()
                }
                dragLayer.setTouchCompleteListener(this)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> longPressHelper.cancelLongPress()
            MotionEvent.ACTION_MOVE -> if (!Utilities.pointInView(this, ev.x, ev.y, slop)) {
                longPressHelper.cancelLongPress()
            }
        }

        // Otherwise continue letting touch events fall through to children
        return false
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        // If the widget does not handle touch, then cancel
        // long press when we release the touch
        when (ev.action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> longPressHelper.cancelLongPress()
            MotionEvent.ACTION_MOVE -> if (!Utilities.pointInView(this, ev.x, ev.y, slop)) {
                longPressHelper.cancelLongPress()
            }
        }
        return false
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        _isAttachedToWindow = true
        checkIfAutoAdvance()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        // We can't directly use isAttachedToWindow() here, as this is called before the internal
        // state is updated. So isAttachedToWindow() will return true until next frame.
        _isAttachedToWindow = false
        checkIfAutoAdvance()
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        longPressHelper.cancelLongPress()
    }

    override fun getAppWidgetInfo(): AppWidgetProviderInfo {
        val info = super.getAppWidgetInfo()
        check(!(info != null && info !is LauncherAppWidgetProviderInfo)) {
            ("Launcher widget must have LauncherAppWidgetProviderInfo")
        }
        return info
    }

    override fun onTouchComplete() {
        if (!longPressHelper.hasPerformedLongPress()) {
            // If a long press has been performed, we don't want to clear the record of that since
            // we still may be receiving a touch up which we want to intercept
            longPressHelper.cancelLongPress()
        }
    }

    override fun getDescendantFocusability(): Int {
        return if (childrenFocused) ViewGroup.FOCUS_BEFORE_DESCENDANTS else ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (childrenFocused && event.keyCode == KeyEvent.KEYCODE_ESCAPE && event.action == KeyEvent.ACTION_UP) {
            childrenFocused = false
            requestFocus()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!childrenFocused && keyCode == KeyEvent.KEYCODE_ENTER) {
            event.startTracking()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isTracking) {
            if (!childrenFocused && keyCode == KeyEvent.KEYCODE_ENTER) {
                childrenFocused = true
                val focusableChildren = getFocusables(View.FOCUS_FORWARD)
                focusableChildren.remove(this)
                when (focusableChildren.size) {
                    0 -> childrenFocused = false
                    1 -> {
                        run {
                            if (tag is ItemInfo) {
                                val item = tag as ItemInfo
                                if (item.spanX == 1 && item.spanY == 1) {
                                    focusableChildren[0].performClick()
                                    childrenFocused = false
                                    return true
                                }
                            }
                        }
                        focusableChildren[0].requestFocus()
                        return true
                    }
                    else -> {
                        focusableChildren[0].requestFocus()
                        return true
                    }
                }
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        if (gainFocus) {
            childrenFocused = false
            dispatchChildFocus(false)
        }
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    override fun requestChildFocus(child: View, focused: View) {
        super.requestChildFocus(child, focused)
        dispatchChildFocus(childrenFocused)
        focused.isFocusableInTouchMode = false
    }

    override fun clearChildFocus(child: View) {
        super.clearChildFocus(child)
        dispatchChildFocus(false)
    }

    override fun dispatchUnhandledMove(focused: View, direction: Int) = childrenFocused

    private fun dispatchChildFocus(childIsFocused: Boolean) {
        // The host view's background changes when selected, to indicate the focus is inside.
        isSelected = childIsFocused
    }

    fun switchToErrorView() {
        // Update the widget with 0 Layout id, to reset the view to error view.
        updateAppWidget(RemoteViews(appWidgetInfo.provider.packageName, 0))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        try {
            super.onLayout(changed, left, top, right, bottom)
        } catch (e: RuntimeException) {
            post { switchToErrorView() }
        }
        isScrollable = checkScrollableRecursively(this)
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = javaClass.name
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        maybeRegisterAutoAdvance()
    }

    private fun checkIfAutoAdvance() {
        var isAutoAdvance = false
        val target = advanceable
        if (target != null) {
            isAutoAdvance = true
            target.fyiWillBeAdvancedByHostKThx()
        }
        val wasAutoAdvance = sAutoAdvanceWidgetIds.indexOfKey(appWidgetId) >= 0
        if (isAutoAdvance != wasAutoAdvance) {
            if (isAutoAdvance) {
                sAutoAdvanceWidgetIds.put(appWidgetId, true)
            } else {
                sAutoAdvanceWidgetIds.delete(appWidgetId)
            }
            maybeRegisterAutoAdvance()
        }
    }

    private val advanceable: Advanceable?
        get() {
            if (appWidgetInfo.autoAdvanceViewId == View.NO_ID || !_isAttachedToWindow) return null

            val v = findViewById<View>(appWidgetInfo.autoAdvanceViewId)
            return if (v is Advanceable) v else null
        }

    private fun maybeRegisterAutoAdvance() {
        val handler = handler?: return
        val shouldRegisterAutoAdvance = windowVisibility == View.VISIBLE && sAutoAdvanceWidgetIds.indexOfKey(appWidgetId) >= 0
        if (shouldRegisterAutoAdvance != isAutoAdvanceRegistered) {
            isAutoAdvanceRegistered = shouldRegisterAutoAdvance
            handler.removeCallbacks(autoAdvanceRunnable)
            scheduleNextAdvance()
        }
    }

    private fun scheduleNextAdvance() {
        if (!isAutoAdvanceRegistered) return
        val now = SystemClock.uptimeMillis()
        val advanceTime = now + (ADVANCE_INTERVAL - now % ADVANCE_INTERVAL) + ADVANCE_STAGGER * sAutoAdvanceWidgetIds.indexOfKey(appWidgetId)
        handler?.postAtTime(autoAdvanceRunnable, advanceTime)
    }

    private fun runAutoAdvance() {
        advanceable?.advance()
        scheduleNextAdvance()
    }

    fun setTranslationForCentering(x: Float, y: Float) {
        translationForCentering[x] = y
        translationX = x
        translationY = y
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Only reinflate when the final configuration is same as the required configuration
        if (reinflateOnConfigChange && isSameOrientation) {
            reinflateOnConfigChange = false
            reInflate()
        }
    }

    fun reInflate() {
        if (!_isAttachedToWindow) return

        val info = tag as LauncherAppWidgetInfo
        // Remove and rebind the current widget (which was inflated in the wrong
        // orientation), but don't delete it from the database
        launcher.removeItem(this, info, false /* deleteFromDb */)
        launcher.bindAppWidget(info)
    }

    companion object {
        // Related to the auto-advancing of widgets
        private const val ADVANCE_INTERVAL = 20000L
        private const val ADVANCE_STAGGER = 250L

        // Maintains a list of widget ids which are supposed to be auto advanced.
        private val sAutoAdvanceWidgetIds = SparseBooleanArray()
    }
}