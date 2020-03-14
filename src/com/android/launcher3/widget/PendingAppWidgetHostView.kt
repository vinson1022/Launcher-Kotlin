/*
 * Copyright (C) 2014 The Android Open Source Project
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

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.View
import com.android.launcher3.IconCache
import com.android.launcher3.IconCache.ItemInfoUpdateReceiver
import com.android.launcher3.ItemInfoWithIcon
import com.android.launcher3.LauncherAppWidgetInfo
import com.android.launcher3.R
import com.android.launcher3.graphics.DrawableFactory
import com.android.launcher3.model.PackageItemInfo
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.util.getAttrColor
import kotlin.math.max
import kotlin.math.min

class PendingAppWidgetHostView(
        context: Context?,
        private val info: LauncherAppWidgetInfo,
        cache: IconCache,
        private val disabledForSafeMode: Boolean
) : LauncherAppWidgetHostView(ContextThemeWrapper(context, R.style.WidgetContainerTheme)),
        View.OnClickListener, ItemInfoUpdateReceiver {

    private val rect = Rect()
    private val _defaultView: View by lazy {
        inflater.inflate(R.layout.appwidget_not_ready, this, false).apply {
            setOnClickListener(this@PendingAppWidgetHostView)
            applyState()
        }
    }
    private var clickListener: OnClickListener? = null
    private val startState = info.restoreStatus
    private var centerDrawable: Drawable? = null
    private var settingIconDrawable: Drawable? = null
    private var drawableSizeChanged = false
    private val paint = TextPaint().apply {
        color = getAttrColor(getContext(), android.R.attr.textColorPrimary)
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX,
                launcher.deviceProfile.iconTextSizePx.toFloat(), resources.displayMetrics)
    }
    private var setupTextLayout: Layout? = null

    init {
        setBackgroundResource(R.drawable.pending_widget_bg)
        setWillNotDraw(false)
        elevation = resources.getDimension(R.dimen.pending_widget_elevation)
        updateAppWidget(null)
        setOnClickListener(ItemClickHandler.clickListener)
        if (info.pendingItemInfo == null) {
            info.pendingItemInfo = PackageItemInfo(info.providerName.packageName)
            info.pendingItemInfo.user = info.user
            cache.updateIconInBackground(this, info.pendingItemInfo)
        } else {
            reapplyItemInfo(info.pendingItemInfo)
        }
    }

    override fun updateAppWidgetSize(newOptions: Bundle, minWidth: Int, minHeight: Int, maxWidth: Int,
                                     maxHeight: Int) {
        // No-op
    }

    override fun getDefaultView(): View = _defaultView

    override fun setOnClickListener(l: OnClickListener?) {
        clickListener = l
    }

    val isReinflateIfNeeded: Boolean
        get() = startState != info.restoreStatus

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        drawableSizeChanged = true
    }

    override fun reapplyItemInfo(info: ItemInfoWithIcon) {
        if (centerDrawable != null) {
            centerDrawable!!.callback = null
            centerDrawable = null
        }
        if (info.iconBitmap != null) {
            // The view displays three modes,
            //   1) App icon in the center
            //   2) Preload icon in the center
            //   3) Setup icon in the center and app icon in the top right corner.
            val drawableFactory = DrawableFactory.get(context)
            when {
                disabledForSafeMode -> {
                    val disabledIcon = drawableFactory.newIcon(info)
                    disabledIcon.setIsDisabled(true)
                    centerDrawable = disabledIcon
                    settingIconDrawable = null
                }
                isReadyForClickSetup -> {
                    centerDrawable = drawableFactory.newIcon(info)
                    settingIconDrawable = resources.getDrawable(R.drawable.ic_setting).mutate()
                    updateSettingColor(info.iconColor)
                }
                else -> {
                    centerDrawable = DrawableFactory.get(context)
                            .newPendingIcon(info, context)
                    settingIconDrawable = null
                    applyState()
                }
            }
            centerDrawable?.callback = this
            drawableSizeChanged = true
        }
        invalidate()
    }

    private fun updateSettingColor(dominantColor: Int) {
        // Make the dominant color bright.
        val hsv = FloatArray(3)
        Color.colorToHSV(dominantColor, hsv)
        hsv[1] = min(hsv[1], MIN_SATUNATION)
        hsv[2] = 1f
        settingIconDrawable?.setColorFilter(Color.HSVToColor(hsv), PorterDuff.Mode.SRC_IN)
    }

    override fun verifyDrawable(who: Drawable): Boolean {
        return who === centerDrawable || super.verifyDrawable(who)
    }

    fun applyState() {
        centerDrawable?.level = max(info.installProgress, 0)
    }

    override fun onClick(v: View) {
        // AppWidgetHostView blocks all click events on the root view. Instead handle click events
        // on the content and pass it along.
        clickListener?.onClick(this)
    }

    /**
     * A pending widget is ready for setup after the provider is installed and
     * 1) Widget id is not valid: the widget id is not yet bound to the provider, probably
     * because the launcher doesn't have appropriate permissions.
     * Note that we would still have an allocated id as that does not
     * require any permissions and can be done during view inflation.
     * 2) UI is not ready: the id is valid and the bound. But the widget has a configure activity
     * which needs to be called once.
     */
    val isReadyForClickSetup: Boolean
        get() = (!info.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)
                && (info.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_UI_NOT_READY)
                || info.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)))

    private fun updateDrawableBounds() {
        val grid = launcher.deviceProfile
        val paddingTop = paddingTop
        val paddingBottom = paddingBottom
        val paddingLeft = paddingLeft
        val paddingRight = paddingRight
        val minPadding = resources
                .getDimensionPixelSize(R.dimen.pending_widget_min_padding)
        val availableWidth = width - paddingLeft - paddingRight - 2 * minPadding
        val availableHeight = height - paddingTop - paddingBottom - 2 * minPadding
        if (settingIconDrawable == null) {
            val maxSize = grid.iconSizePx
            val size = min(maxSize, min(availableWidth, availableHeight))
            rect[0, 0, size] = size
            rect.offsetTo((width - rect.width()) / 2, (height - rect.height()) / 2)
            centerDrawable!!.bounds = rect
        } else {
            var iconSize = max(0, min(availableWidth, availableHeight)).toFloat()

            // Use twice the setting size factor, as the setting is drawn at a corner and the
            // icon is drawn in the center.
            val settingIconScaleFactor = 1 + SETUP_ICON_SIZE_FACTOR * 2
            val maxSize = max(availableWidth, availableHeight)
            if (iconSize * settingIconScaleFactor > maxSize) {
                // There is an overlap
                iconSize = maxSize / settingIconScaleFactor
            }
            val actualIconSize = min(iconSize, grid.iconSizePx.toFloat()).toInt()

            // Icon top when we do not draw the text
            var iconTop = (height - actualIconSize) / 2
            setupTextLayout = null
            if (availableWidth > 0) {
                // Recreate the setup text.
                setupTextLayout = StaticLayout(
                        resources.getText(R.string.gadget_setup_text), paint, availableWidth,
                        Layout.Alignment.ALIGN_CENTER, 1f, 0f, true)
                val textHeight = setupTextLayout!!.height

                // Extra icon size due to the setting icon
                val minHeightWithText = textHeight + actualIconSize * settingIconScaleFactor + grid.iconDrawablePaddingPx
                if (minHeightWithText < availableHeight) {
                    // We can draw the text as well
                    iconTop = (height - textHeight -
                            grid.iconDrawablePaddingPx - actualIconSize) / 2
                } else {
                    // We can't draw the text. Let the iconTop be same as before.
                    setupTextLayout = null
                }
            }
            rect[0, 0, actualIconSize] = actualIconSize
            rect.offset((width - actualIconSize) / 2, iconTop)
            centerDrawable!!.bounds = rect
            rect.left = paddingLeft + minPadding
            rect.right = rect.left + (SETUP_ICON_SIZE_FACTOR * actualIconSize).toInt()
            rect.top = paddingTop + minPadding
            rect.bottom = rect.top + (SETUP_ICON_SIZE_FACTOR * actualIconSize).toInt()
            settingIconDrawable!!.bounds = rect
            if (setupTextLayout != null) {
                // Set up position for dragging the text
                rect.left = paddingLeft + minPadding
                rect.top = centerDrawable!!.bounds.bottom + grid.iconDrawablePaddingPx
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (centerDrawable == null) {
            // Nothing to draw
            return
        }
        if (drawableSizeChanged) {
            updateDrawableBounds()
            drawableSizeChanged = false
        }
        centerDrawable!!.draw(canvas)
        if (settingIconDrawable != null) {
            settingIconDrawable!!.draw(canvas)
        }
        if (setupTextLayout != null) {
            canvas.save()
            canvas.translate(rect.left.toFloat(), rect.top.toFloat())
            setupTextLayout!!.draw(canvas)
            canvas.restore()
        }
    }

    companion object {
        private const val SETUP_ICON_SIZE_FACTOR = 2f / 5
        private const val MIN_SATUNATION = 0.7f
    }
}