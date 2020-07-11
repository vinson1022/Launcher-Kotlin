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
package com.android.launcher3.graphics

import android.animation.ObjectAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.*
import android.graphics.drawable.Drawable
import android.support.v4.graphics.ColorUtils
import android.util.Property
import android.view.View
import android.view.View.OnAttachStateChangeListener
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.Workspace
import com.android.launcher3.uioverrides.WallpaperColorInfo
import com.android.launcher3.uioverrides.WallpaperColorInfo.OnChangeListener
import com.android.launcher3.util.getAttrDrawable
import kotlin.math.roundToInt

/**
 * View scrim which draws behind hotseat and workspace
 */
class WorkspaceAndHotseatScrim(private val root: View) : OnAttachStateChangeListener, OnChangeListener {

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> animateScrimOnNextDraw = true
                Intent.ACTION_USER_PRESENT -> {
                    // ACTION_USER_PRESENT is sent after onStart/onResume. This covers the case where
                    // the user unlocked and the Launcher is not in the foreground.
                    animateScrimOnNextDraw = false
                }
            }
        }
    }
    private val highlightRect = Rect()
    private val launcher: Launcher = Launcher.getLauncher(root.context)
    private val wallpaperColorInfo = WallpaperColorInfo.getInstance(launcher).apply {
        onExtractedColorsChanged(this)
    }
    private var workspace: Workspace? = null
    private val hasSysUiScrim: Boolean = !wallpaperColorInfo.supportsDarkText()
    private var drawTopScrim = false
    private var drawBottomScrim = false
    private val finalMaskRect = RectF()
    private val bottomMaskPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var bottomMask: Bitmap? = if (hasSysUiScrim) {
        createDitheredAlphaMask()
    } else null
    private val maskHeight = Utilities.pxFromDp(ALPHA_MASK_BITMAP_DP.toFloat(),
            root.resources.displayMetrics)
    private var topScrim: Drawable? = if (hasSysUiScrim) {
        getAttrDrawable(root.context, R.attr.workspaceStatusBarScrim)
    } else null
    private var fullScrimColor = 0
    private var scrimProgress = 0f
    private var scrimAlpha = 0
    private var sysUiProgress = 1f
    private var hideSysUiScrim1 = false
    private var animateScrimOnNextDraw = false
    private var sysUiAnimMultiplier = 1f

    init {
        root.addOnAttachStateChangeListener(this)
    }

    fun setWorkspace(workspace: Workspace) {
        this.workspace = workspace
    }

    fun draw(canvas: Canvas) {
        // Draw the background below children.
        if (scrimAlpha > 0) {
            // Update the scroll position first to ensure scrim cutout is in the right place.
            workspace!!.computeScrollWithoutInvalidation()
            val currCellLayout = workspace!!.currentDragOverlappingLayout
            canvas.save()
            if (currCellLayout != null && currCellLayout !== launcher.hotseat.layout) {
                // Cut a hole in the darkening scrim on the page that should be highlighted, if any.
                launcher.dragLayer
                        .getDescendantRectRelativeToSelf(currCellLayout, highlightRect)
                canvas.clipRect(highlightRect, Region.Op.DIFFERENCE)
            }
            canvas.drawColor(ColorUtils.setAlphaComponent(fullScrimColor, scrimAlpha))
            canvas.restore()
        }
        if (!hideSysUiScrim1 && hasSysUiScrim) {
            if (sysUiProgress <= 0) {
                animateScrimOnNextDraw = false
                return
            }
            if (animateScrimOnNextDraw) {
                sysUiAnimMultiplier = 0f
                reapplySysUiAlphaNoInvalidate()
                val anim = ObjectAnimator.ofFloat(this, SYSUI_ANIM_MULTIPLIER, 1f)
                anim.setAutoCancel(true)
                anim.duration = 600
                anim.startDelay = launcher.window.transitionBackgroundFadeDuration
                anim.start()
                animateScrimOnNextDraw = false
            }
            if (drawTopScrim) {
                topScrim!!.draw(canvas)
            }
            if (drawBottomScrim) {
                canvas.drawBitmap(bottomMask!!, null, finalMaskRect, bottomMaskPaint)
            }
        }
    }

    fun onInsetsChanged(insets: Rect) {
        drawTopScrim = insets.top > 0
        drawBottomScrim = !launcher.deviceProfile.isVerticalBarLayout
    }

    private fun setScrimProgress(progress: Float) {
        if (scrimProgress != progress) {
            scrimProgress = progress
            scrimAlpha = (255 * scrimProgress).roundToInt()
            invalidate()
        }
    }

    override fun onViewAttachedToWindow(view: View) {
        wallpaperColorInfo.addOnChangeListener(this)
        onExtractedColorsChanged(wallpaperColorInfo)
        if (hasSysUiScrim) {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            filter.addAction(Intent.ACTION_USER_PRESENT) // When the device wakes up + keyguard is gone
            root.context.registerReceiver(receiver, filter)
        }
    }

    override fun onViewDetachedFromWindow(view: View) {
        wallpaperColorInfo.removeOnChangeListener(this)
        if (hasSysUiScrim) {
            root.context.unregisterReceiver(receiver)
        }
    }

    override fun onExtractedColorsChanged(wallpaperColorInfo: WallpaperColorInfo) {
        // for super light wallpaper it needs to be darken for contrast to workspace
        // for dark wallpapers the text is white so darkening works as well
        bottomMaskPaint.color = ColorUtils.compositeColors(DARK_SCRIM_COLOR,
                wallpaperColorInfo.mainColor)
        reapplySysUiAlpha()
        fullScrimColor = wallpaperColorInfo.mainColor
        if (scrimAlpha > 0) {
            invalidate()
        }
    }

    fun setSize(w: Int, h: Int) {
        if (hasSysUiScrim) {
            topScrim!!.setBounds(0, 0, w, h)
            finalMaskRect[0f, h - maskHeight.toFloat(), w.toFloat()] = h.toFloat()
        }
    }

    fun hideSysUiScrim(hideSysUiScrim: Boolean) {
        hideSysUiScrim1 = hideSysUiScrim
        if (!hideSysUiScrim) {
            animateScrimOnNextDraw = true
        }
        invalidate()
    }

    private fun setSysUiProgress(progress: Float) {
        if (progress != sysUiProgress) {
            sysUiProgress = progress
            reapplySysUiAlpha()
        }
    }

    private fun reapplySysUiAlpha() {
        if (hasSysUiScrim) {
            reapplySysUiAlphaNoInvalidate()
            if (!hideSysUiScrim1) {
                invalidate()
            }
        }
    }

    private fun reapplySysUiAlphaNoInvalidate() {
        val factor = sysUiProgress * sysUiAnimMultiplier
        bottomMaskPaint.alpha = (MAX_HOTSEAT_SCRIM_ALPHA * factor).roundToInt()
        topScrim?.alpha = (255 * factor).roundToInt()
    }

    fun invalidate() {
        root.invalidate()
    }

    private fun createDitheredAlphaMask(): Bitmap {
        val dm = launcher.resources.displayMetrics
        val width = Utilities.pxFromDp(ALPHA_MASK_WIDTH_DP.toFloat(), dm)
        val gradientHeight = Utilities.pxFromDp(ALPHA_MASK_HEIGHT_DP.toFloat(), dm).toFloat()
        val dst = Bitmap.createBitmap(width, maskHeight, Bitmap.Config.ALPHA_8)
        val c = Canvas(dst)
        val paint = Paint(Paint.DITHER_FLAG)
        val lg = LinearGradient(0f, 0f, 0f, gradientHeight, intArrayOf(
                0x00FFFFFF,
                ColorUtils.setAlphaComponent(Color.WHITE, (0xFF * 0.95).toInt()),
                -0x1), floatArrayOf(0f, 0.8f, 1f),
                Shader.TileMode.CLAMP)
        paint.shader = lg
        c.drawRect(0f, 0f, width.toFloat(), gradientHeight, paint)
        return dst
    }

    companion object {
        @JvmField
        var SCRIM_PROGRESS = object : Property<WorkspaceAndHotseatScrim, Float>(java.lang.Float.TYPE, "scrimProgress") {
            override fun get(scrim: WorkspaceAndHotseatScrim): Float {
                return scrim.scrimProgress
            }

            override fun set(scrim: WorkspaceAndHotseatScrim, value: Float) {
                scrim.setScrimProgress(value)
            }
        }
        @JvmField
        var SYSUI_PROGRESS = object : Property<WorkspaceAndHotseatScrim, Float>(java.lang.Float.TYPE, "sysUiProgress") {
            override fun get(scrim: WorkspaceAndHotseatScrim): Float {
                return scrim.sysUiProgress
            }

            override fun set(scrim: WorkspaceAndHotseatScrim, value: Float) {
                scrim.setSysUiProgress(value)
            }
        }
        private val SYSUI_ANIM_MULTIPLIER = object : Property<WorkspaceAndHotseatScrim, Float>(java.lang.Float.TYPE, "sysUiAnimMultiplier") {
            override fun get(scrim: WorkspaceAndHotseatScrim): Float {
                return scrim.sysUiAnimMultiplier
            }

            override fun set(scrim: WorkspaceAndHotseatScrim, value: Float) {
                scrim.sysUiAnimMultiplier = value
                scrim.reapplySysUiAlpha()
            }
        }
        private const val DARK_SCRIM_COLOR = 0x55000000
        private const val MAX_HOTSEAT_SCRIM_ALPHA = 100
        private const val ALPHA_MASK_HEIGHT_DP = 500
        private const val ALPHA_MASK_BITMAP_DP = 200
        private const val ALPHA_MASK_WIDTH_DP = 2
    }
}