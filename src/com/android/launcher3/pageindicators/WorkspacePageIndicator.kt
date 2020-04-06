package com.android.launcher3.pageindicators

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Property
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import com.android.launcher3.Insettable
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.uioverrides.WallpaperColorInfo

/**
 * A PageIndicator that briefly shows a fraction of a line when moving between pages
 *
 * The fraction is 1 / number of pages and the position is based on the progress of the page scroll.
 */
class WorkspacePageIndicator
@JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : View(context, attrs, defStyle), Insettable, PageIndicator {

    private val animators = arrayOfNulls<ValueAnimator>(ANIMATOR_COUNT)
    private val delayedLineFadeHandler = Handler(Looper.getMainLooper())
    private val launcher = Launcher.getLauncher(context)
    private var shouldAutoHide = true

    // The alpha of the line when it is showing.
    private var activeAlpha = 0

    // The alpha that the line is being animated to or already at (either 0 or mActiveAlpha).
    private var toAlpha = 0

    // A float value representing the number of pages, to allow for an animation when it changes.
    private var numPagesFloat = 0f
    private var currentScroll = 0
    private var totalScroll = 0
    private val linePaint = Paint().also { alpha = 0f }
    private val lineHeight = context.resources.getDimensionPixelSize(R.dimen.dynamic_grid_page_indicator_line_height)
    private val hideLineRunnable = Runnable { animateLineToAlpha(0) }

    init {
        val darkText = WallpaperColorInfo.getInstance(context).supportsDarkText()
        activeAlpha = if (darkText) BLACK_ALPHA else WHITE_ALPHA
        linePaint.color = if (darkText) Color.BLACK else Color.WHITE
    }

    override fun onDraw(canvas: Canvas) {
        if (totalScroll == 0 || numPagesFloat == 0f) return

        // Compute and draw line rect.
        val progress = Utilities.boundToRange(currentScroll.toFloat() / totalScroll, 0f, 1f)
        val availableWidth = width
        val lineWidth = (availableWidth / numPagesFloat).toInt()
        val lineLeft = (progress * (availableWidth - lineWidth)).toInt()
        val lineRight = lineLeft + lineWidth
        canvas.drawRoundRect(lineLeft.toFloat(), height / 2 - lineHeight / 2.toFloat(), lineRight.toFloat(),
                height / 2 + lineHeight / 2.toFloat(), lineHeight.toFloat(), lineHeight.toFloat(), linePaint)
    }

    override fun setScroll(currentScroll: Int, totalScroll: Int) {
        if (alpha == 0f) return

        animateLineToAlpha(activeAlpha)
        this.currentScroll = currentScroll
        when {
            this.totalScroll == 0 -> this.totalScroll = totalScroll
            this.totalScroll != totalScroll -> animateToTotalScroll(totalScroll)
            else -> invalidate()
        }
        if (shouldAutoHide) {
            hideAfterDelay()
        }
    }

    private fun hideAfterDelay() {
        delayedLineFadeHandler.removeCallbacksAndMessages(null)
        delayedLineFadeHandler.postDelayed(hideLineRunnable, LINE_FADE_DELAY.toLong())
    }

    override fun setActiveMarker(activePage: Int) {}
    override fun setMarkersCount(numMarkers: Int) {
        if (numMarkers.toFloat().compareTo(numPagesFloat) != 0) {
            setupAndRunAnimation(ObjectAnimator.ofFloat(this, NUM_PAGES, numMarkers.toFloat()),
                    NUM_PAGES_ANIMATOR_INDEX)
        } else {
            if (animators[NUM_PAGES_ANIMATOR_INDEX] != null) {
                animators[NUM_PAGES_ANIMATOR_INDEX]!!.cancel()
                animators[NUM_PAGES_ANIMATOR_INDEX] = null
            }
        }
    }

    fun setShouldAutoHide(shouldAutoHide: Boolean) {
        this.shouldAutoHide = shouldAutoHide
        if (shouldAutoHide && linePaint.alpha > 0) {
            hideAfterDelay()
        } else if (!shouldAutoHide) {
            delayedLineFadeHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun animateLineToAlpha(alpha: Int) {
        if (alpha == toAlpha) {
            // Ignore the new animation if it is going to the same alpha as the current animation.
            return
        }
        toAlpha = alpha
        setupAndRunAnimation(ObjectAnimator.ofInt(this, PAINT_ALPHA, alpha),
                LINE_ALPHA_ANIMATOR_INDEX)
    }

    private fun animateToTotalScroll(totalScroll: Int) {
        setupAndRunAnimation(ObjectAnimator.ofInt(this, TOTAL_SCROLL, totalScroll),
                TOTAL_SCROLL_ANIMATOR_INDEX)
    }

    /**
     * Starts the given animator and stores it in the provided index in [.mAnimators] until
     * the animation ends.
     *
     * If an animator is already at the index (i.e. it is already playing), it is canceled and
     * replaced with the new animator.
     */
    private fun setupAndRunAnimation(animator: ValueAnimator, animatorIndex: Int) {
        if (animators[animatorIndex] != null) {
            animators[animatorIndex]!!.cancel()
        }
        animators[animatorIndex] = animator.apply {
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animators[animatorIndex] = null
                }
            })
            duration = LINE_ANIMATE_DURATION
            start()
        }
    }

    /**
     * Pauses all currently running animations.
     */
    fun pauseAnimations() {
        animators.forEach {
            it?.pause()
        }
    }

    /**
     * Force-ends all currently running or paused animations.
     */
    fun skipAnimationsToEnd() {
        animators.forEach {
            it?.end()
        }
    }

    override fun setInsets(insets: Rect) {
        val grid = launcher.deviceProfile
        val lp = layoutParams as FrameLayout.LayoutParams
        if (grid.isVerticalBarLayout) {
            val padding = grid.workspacePadding
            lp.leftMargin = padding.left + grid.workspaceCellPaddingXPx
            lp.rightMargin = padding.right + grid.workspaceCellPaddingXPx
            lp.bottomMargin = padding.bottom
        } else {
            lp.rightMargin = 0
            lp.leftMargin = lp.rightMargin
            lp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
            lp.bottomMargin = grid.hotseatBarSizePx + insets.bottom
        }
        layoutParams = lp
    }

    companion object {
        private val LINE_ANIMATE_DURATION = ViewConfiguration.getScrollBarFadeDuration().toLong()
        private val LINE_FADE_DELAY = ViewConfiguration.getScrollDefaultDelay()
        const val WHITE_ALPHA = (0.70f * 255).toInt()
        const val BLACK_ALPHA = (0.65f * 255).toInt()
        private const val LINE_ALPHA_ANIMATOR_INDEX = 0
        private const val NUM_PAGES_ANIMATOR_INDEX = 1
        private const val TOTAL_SCROLL_ANIMATOR_INDEX = 2
        private const val ANIMATOR_COUNT = 3
        private val PAINT_ALPHA = object : Property<WorkspacePageIndicator, Int>(Int::class.java, "paint_alpha") {
            override fun get(obj: WorkspacePageIndicator): Int {
                return obj.linePaint.alpha
            }

            override fun set(obj: WorkspacePageIndicator, alpha: Int) {
                obj.linePaint.alpha = alpha
                obj.invalidate()
            }
        }
        private val NUM_PAGES = object : Property<WorkspacePageIndicator, Float>(Float::class.java, "num_pages") {
            override fun get(obj: WorkspacePageIndicator): Float {
                return obj.numPagesFloat
            }

            override fun set(obj: WorkspacePageIndicator, numPages: Float) {
                obj.numPagesFloat = numPages
                obj.invalidate()
            }
        }
        private val TOTAL_SCROLL = object : Property<WorkspacePageIndicator, Int>(Int::class.java, "total_scroll") {
            override fun get(obj: WorkspacePageIndicator): Int {
                return obj.totalScroll
            }

            override fun set(obj: WorkspacePageIndicator, totalScroll: Int) {
                obj.totalScroll = totalScroll
                obj.invalidate()
            }
        }
    }
}