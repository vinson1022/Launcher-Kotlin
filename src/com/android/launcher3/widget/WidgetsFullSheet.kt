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
package com.android.launcher3.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.PropertyValuesHolder
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Pair
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import com.android.launcher3.Insettable
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState.Companion.getInstance
import com.android.launcher3.LauncherAppWidgetHost.ProviderChangedListener
import com.android.launcher3.R
import com.android.launcher3.views.TopRoundedCornerView
import kotlinx.android.synthetic.main.widgets_full_sheet.view.*
import kotlin.math.max

/**
 * Popup for showing the full list of available widgets
 */
class WidgetsFullSheet
@JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int = 0
) : BaseWidgetSheet(context, attrs, defStyleAttr), Insettable, ProviderChangedListener {

    private val insets = Rect()
    private val adapter: WidgetsListAdapter = getInstance(context).let { apps ->
        WidgetsListAdapter(context, LayoutInflater.from(context), apps.widgetCache, apps.iconCache,
                this, this)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        content = findViewById(R.id.container)
        widgetsListView.adapter = adapter
        adapter.setApplyBitmapDeferred(true, widgetsListView)
        val springLayout = content as TopRoundedCornerView
        springLayout.addSpringView(R.id.widgetsListView)
        widgetsListView.edgeEffectFactory = springLayout.createEdgeEffectFactory()
        onWidgetsBound()
    }

    override fun getAccessibilityTarget(): Pair<View, String> {
        return Pair.create(widgetsListView, context.getString(
                if (isOpen) R.string.widgets_list else R.string.widgets_list_closed))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        launcher.appWidgetHost.addProviderChangeListener(this)
        notifyWidgetProvidersChanged()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        launcher.appWidgetHost.removeProviderChangeListener(this)
    }

    override fun setInsets(insets: Rect) {
        this.insets.set(insets)
        with(widgetsListView) {
            setPadding(paddingLeft, paddingTop, paddingRight, insets.bottom)
        }
        if (insets.bottom > 0) {
            setupNavBarColor()
        } else {
            clearNavBarColor()
        }
        (content as TopRoundedCornerView).setNavBarScrimHeight(this.insets.bottom)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthUsed = if (insets.bottom > 0) {
            0
        } else {
            val padding = launcher.deviceProfile.workspacePadding
            max(padding.left + padding.right,
                    2 * (insets.left + insets.right))
        }
        val heightUsed = insets.top + launcher.deviceProfile.edgeMarginPx
        measureChildWithMargins(content, widthMeasureSpec,
                widthUsed, heightMeasureSpec, heightUsed)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
                MeasureSpec.getSize(heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val height = b - t

        // Content is laid out as center bottom aligned
        val contentWidth = content.measuredWidth
        val contentLeft = (width - contentWidth) / 2
        content.layout(contentLeft, height - content.measuredHeight,
                contentLeft + contentWidth, height)
        setTranslationShift(_translationShift)
    }

    override fun notifyWidgetProvidersChanged() {
        launcher.refreshAndBindWidgetsForPackageUser(null)
    }

    override fun onWidgetsBound() {
        adapter.setWidgets(launcher.popupDataProvider.allWidgets)
    }

    private fun open(animate: Boolean) {
        if (animate) {
            if (launcher.dragLayer.insets.bottom > 0) {
                content.alpha = 0f
                setTranslationShift(VERTICAL_START_POSITION)
            }
            openCloseAnimator.setValues(
                    PropertyValuesHolder.ofFloat(TRANSLATION_SHIFT, TRANSLATION_SHIFT_OPENED))
            openCloseAnimator
                    .setDuration(DEFAULT_OPEN_DURATION).interpolator = AnimationUtils.loadInterpolator(
                    context, android.R.interpolator.linear_out_slow_in)
            openCloseAnimator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    widgetsListView.isLayoutFrozen = false
                    adapter.setApplyBitmapDeferred(false, widgetsListView)
                    openCloseAnimator.removeListener(this)
                }
            })
            post {
                widgetsListView.isLayoutFrozen = true
                openCloseAnimator.start()
                content.animate().alpha(1f).duration = FADE_IN_DURATION
            }
        } else {
            setTranslationShift(TRANSLATION_SHIFT_OPENED)
            adapter.setApplyBitmapDeferred(false, widgetsListView)
            post { announceAccessibilityChanges() }
        }
    }

    override fun handleClose(animate: Boolean) {
        handleClose(animate, DEFAULT_OPEN_DURATION)
    }

    override fun isOfType(type: Int): Boolean {
        return type and TYPE_WIDGETS_FULL_SHEET != 0
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        // Disable swipe down when recycler view is scrolling
        if (ev.action == MotionEvent.ACTION_DOWN) {
            noIntercept = false
            val scroller = widgetsListView.scrollbar
            if (scroller.thumbOffsetY >= 0 &&
                    launcher.dragLayer.isEventOverView(scroller, ev)) {
                noIntercept = true
            } else if (launcher.dragLayer.isEventOverView(content, ev)) {
                noIntercept = !widgetsListView.shouldContainerScroll(ev, launcher.dragLayer)
            }
        }
        return super.onControllerInterceptTouchEvent(ev)
    }

    override val elementsRowCount: Int
        get() = adapter.itemCount

    companion object {
        private const val DEFAULT_OPEN_DURATION = 267L
        private const val FADE_IN_DURATION = 150L
        private const val VERTICAL_START_POSITION = 0.3f

        @JvmStatic
        fun show(launcher: Launcher, animate: Boolean): WidgetsFullSheet {
            val sheet = launcher.layoutInflater
                    .inflate(R.layout.widgets_full_sheet, launcher.dragLayer, false) as WidgetsFullSheet
            sheet.isOpen = true
            launcher.dragLayer.addView(sheet)
            sheet.open(animate)
            return sheet
        }
    }
}