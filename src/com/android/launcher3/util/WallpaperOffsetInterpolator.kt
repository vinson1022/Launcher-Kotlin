package com.android.launcher3.util

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_WALLPAPER_CHANGED
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.os.SystemClock
import android.util.Log
import com.android.launcher3.Utilities
import com.android.launcher3.Workspace
import com.android.launcher3.anim.Interpolators.DEACCEL_1_5
import kotlin.math.max

/**
 * Utility class to handle wallpaper scrolling along with workspace.
 */
class WallpaperOffsetInterpolator(private val workspace: Workspace) : BroadcastReceiver() {

    private val isRtl = Utilities.isRtl(workspace.resources)
    private val handler: Handler = OffsetHandler(workspace.context)
    private var registered = false
    private var windowToken: IBinder? = null
    private var wallpaperIsLiveWallpaper = false
    var isLockedToDefaultPage = false
        private set
    private var numScreens = 0

    /**
     * Locks the wallpaper offset to the offset in the default state of Launcher.
     */
    fun setLockToDefaultPage(lockToDefaultPage: Boolean) {
        isLockedToDefaultPage = lockToDefaultPage
    }

    /**
     * Computes the wallpaper offset as an int ratio (out[0] / out[1])
     *
     * TODO: do different behavior if it's  a live wallpaper?
     */
    private fun wallpaperOffsetForScroll(scroll: Int, numScrollingPages: Int, out: IntArray) {
        out[1] = 1
        // To match the default wallpaper behavior in the system, we default to either the left
        // or right edge on initialization
        if (isLockedToDefaultPage || numScrollingPages <= 1) {
            out[0] = if (isRtl) 1 else 0
            return
        }
        // Distribute the wallpaper parallax over a minimum of MIN_PARALLAX_PAGE_SPAN workspace
        // screens, not including the custom screen, and empty screens (if > MIN_PARALLAX_PAGE_SPAN)
        val numPagesForWallpaperParallax = if (wallpaperIsLiveWallpaper) numScrollingPages else max(MIN_PARALLAX_PAGE_SPAN, numScrollingPages)
        // Offset by the custom screen
        val leftPageIndex: Int
        val rightPageIndex: Int
        if (isRtl) {
            rightPageIndex = 0
            leftPageIndex = rightPageIndex + numScrollingPages - 1
        } else {
            leftPageIndex = 0
            rightPageIndex = leftPageIndex + numScrollingPages - 1
        }
        // Calculate the scroll range
        val leftPageScrollX = workspace.getScrollForPage(leftPageIndex)
        val rightPageScrollX = workspace.getScrollForPage(rightPageIndex)
        val scrollRange = rightPageScrollX - leftPageScrollX
        if (scrollRange <= 0) {
            out[0] = 0
            return
        }
        // Sometimes the left parameter of the pages is animated during a layout transition;
        // this parameter offsets it to keep the wallpaper from animating as well
        var adjustedScroll = scroll - leftPageScrollX -
                workspace.getLayoutTransitionOffsetForPage(0)
        adjustedScroll = Utilities.boundToRange(adjustedScroll, 0, scrollRange)
        out[1] = (numPagesForWallpaperParallax - 1) * scrollRange
        // The offset is now distributed 0..1 between the left and right pages that we care about,
        // so we just map that between the pages that we are using for parallax
        var rtlOffset = 0
        if (isRtl) { // In RTL, the pages are right aligned, so adjust the offset from the end
            rtlOffset = out[1] - (numScrollingPages - 1) * scrollRange
        }
        out[0] = rtlOffset + adjustedScroll * (numScrollingPages - 1)
    }

    fun wallpaperOffsetForScroll(scroll: Int): Float {
        wallpaperOffsetForScroll(scroll, numScreensExcludingEmpty, tempInt)
        return tempInt[0].toFloat() / tempInt[1]
    }

    private val numScreensExcludingEmpty: Int
        get() {
            val numScrollingPages = workspace.childCount
            return if (numScrollingPages >= MIN_PARALLAX_PAGE_SPAN && workspace.hasExtraEmptyScreen()) {
                numScrollingPages - 1
            } else {
                numScrollingPages
            }
        }

    fun syncWithScroll() {
        val totalScreens = numScreensExcludingEmpty
        wallpaperOffsetForScroll(workspace.scrollX, totalScreens, tempInt)
        val msg = Message.obtain(handler, MSG_UPDATE_OFFSET, tempInt[0], tempInt[1],
                windowToken)
        if (totalScreens != numScreens) {
            if (numScreens > 0) {
                // Don't animate if we're going from 0 screens
                msg.what = MSG_START_ANIMATION
            }
            numScreens = totalScreens
            updateOffset()
        }
        msg.sendToTarget()
    }

    private fun updateOffset() {
        val numPagesForWallpaperParallax = if (wallpaperIsLiveWallpaper) {
            numScreens
        } else {
            max(MIN_PARALLAX_PAGE_SPAN, numScreens)
        }
        Message.obtain(handler, MSG_SET_NUM_PARALLAX, numPagesForWallpaperParallax, 0,
                windowToken).sendToTarget()
    }

    fun jumpToFinal() {
        Message.obtain(handler, MSG_JUMP_TO_FINAL, windowToken).sendToTarget()
    }

    fun setWindowToken(token: IBinder?) {
        windowToken = token
        if (windowToken == null && registered) {
            workspace.context.unregisterReceiver(this)
            registered = false
        } else if (windowToken != null && !registered) {
            workspace.context
                    .registerReceiver(this, IntentFilter(ACTION_WALLPAPER_CHANGED))
            onReceive(workspace.context, null)
            registered = true
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        wallpaperIsLiveWallpaper = WallpaperManager.getInstance(context).wallpaperInfo != null
        updateOffset()
    }

    private class OffsetHandler(context: Context) : Handler(backgroundLooper) {
        private val interpolator = DEACCEL_1_5
        private val wallpaperManager = WallpaperManager.getInstance(context)
        private var currentOffset = 0.5f // to force an initial update
        private var animating = false
        private var animationStartTime = 0L
        private var animationStartOffset = 0f
        private var finalOffset = 0f
        private var offsetX = 0f
        override fun handleMessage(msg: Message) {
            val token = msg.obj as? IBinder ?: return
            when (msg.what) {
                MSG_START_ANIMATION -> {
                    run {
                        animating = true
                        animationStartOffset = currentOffset
                        animationStartTime = msg.getWhen()
                    }
                    finalOffset = msg.arg1.toFloat() / msg.arg2
                    run {
                        val oldOffset = currentOffset
                        if (animating) {
                            val durationSinceAnimation = (SystemClock.uptimeMillis()
                                    - animationStartTime)
                            val t0 = durationSinceAnimation / ANIMATION_DURATION.toFloat()
                            val t1 = interpolator.getInterpolation(t0)
                            currentOffset = animationStartOffset +
                                    (finalOffset - animationStartOffset) * t1
                            animating = durationSinceAnimation < ANIMATION_DURATION
                        } else {
                            currentOffset = finalOffset
                        }
                        if (currentOffset.compareTo(oldOffset) != 0) {
                            setOffsetSafely(token)
                            // Force the wallpaper offset steps to be set again, because another app
                            // might have changed them
                            wallpaperManager.setWallpaperOffsetSteps(offsetX, 1.0f)
                        }
                        if (animating) {
                            // If we are animating, keep updating the offset
                            Message.obtain(this, MSG_APPLY_OFFSET, token).sendToTarget()
                        }
                        return
                    }
                }
                MSG_UPDATE_OFFSET -> {
                    finalOffset = msg.arg1.toFloat() / msg.arg2
                    run {
                        val oldOffset = currentOffset
                        if (animating) {
                            val durationSinceAnimation = (SystemClock.uptimeMillis()
                                    - animationStartTime)
                            val t0 = durationSinceAnimation / ANIMATION_DURATION.toFloat()
                            val t1 = interpolator.getInterpolation(t0)
                            currentOffset = animationStartOffset +
                                    (finalOffset - animationStartOffset) * t1
                            animating = durationSinceAnimation < ANIMATION_DURATION
                        } else {
                            currentOffset = finalOffset
                        }
                        if (currentOffset.compareTo(oldOffset) != 0) {
                            setOffsetSafely(token)
                            wallpaperManager.setWallpaperOffsetSteps(offsetX, 1.0f)
                        }
                        if (animating) {
                            Message.obtain(this, MSG_APPLY_OFFSET, token).sendToTarget()
                        }
                        return
                    }
                }
                MSG_APPLY_OFFSET -> {
                    val oldOffset = currentOffset
                    if (animating) {
                        val durationSinceAnimation = (SystemClock.uptimeMillis()
                                - animationStartTime)
                        val t0 = durationSinceAnimation / ANIMATION_DURATION.toFloat()
                        val t1 = interpolator.getInterpolation(t0)
                        currentOffset = animationStartOffset +
                                (finalOffset - animationStartOffset) * t1
                        animating = durationSinceAnimation < ANIMATION_DURATION
                    } else {
                        currentOffset = finalOffset
                    }
                    if (currentOffset.compareTo(oldOffset) != 0) {
                        setOffsetSafely(token)
                        wallpaperManager.setWallpaperOffsetSteps(offsetX, 1.0f)
                    }
                    if (animating) {
                        Message.obtain(this, MSG_APPLY_OFFSET, token).sendToTarget()
                    }
                    return
                }
                MSG_SET_NUM_PARALLAX -> {
                    // Set wallpaper offset steps (1 / (number of screens - 1))
                    offsetX = 1.0f / (msg.arg1 - 1)
                    wallpaperManager.setWallpaperOffsetSteps(offsetX, 1.0f)
                    return
                }
                MSG_JUMP_TO_FINAL -> {
                    if (currentOffset.compareTo(finalOffset) != 0) {
                        currentOffset = finalOffset
                        setOffsetSafely(token)
                    }
                    animating = false
                    return
                }
            }
        }

        private fun setOffsetSafely(token: IBinder) {
            try {
                wallpaperManager.setWallpaperOffsets(token, currentOffset, 0.5f)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Error updating wallpaper offset: $e")
            }
        }

    }

    companion object {
        private const val TAG = "WPOffsetInterpolator"

        private const val ANIMATION_DURATION = 250
        // Don't use all the wallpaper for parallax until you have at least this many pages
        private const val MIN_PARALLAX_PAGE_SPAN = 4

        private const val MSG_START_ANIMATION = 1
        private const val MSG_UPDATE_OFFSET = 2
        private const val MSG_APPLY_OFFSET = 3
        private const val MSG_SET_NUM_PARALLAX = 4
        private const val MSG_JUMP_TO_FINAL = 5

        private val tempInt = IntArray(2)
    }
}