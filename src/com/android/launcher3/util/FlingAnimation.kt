package com.android.launcher3.util

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.graphics.PointF
import android.graphics.Rect
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import com.android.launcher3.ButtonDropTarget
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.LauncherState.NORMAL
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.dragndrop.DragLayer.ANIMATION_END_DISAPPEAR
import com.android.launcher3.dragndrop.DragView
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

class FlingAnimation(
        private val dragObject: DragObject,
        vel: PointF,
        private val dropTarget: ButtonDropTarget,
        private val mLauncher: Launcher
) : AnimatorUpdateListener, Runnable {

    private val dragLayer = mLauncher.dragLayer
    private val alphaInterpolator = DecelerateInterpolator(0.75f)
    private val ux = vel.x / 1000f
    private val uy = vel.y / 1000f
    private var iconRect: Rect? = null
    private var from = Rect()
    private var duration = 0
    private var animationTimeFraction = 0f
    private var ax = 0f
    private var ay = 0f

    override fun run() {
        iconRect = dropTarget.getIconRect(dragObject)
        // Initiate from
        from.setEmpty()
        dragLayer.getViewRectRelativeToSelf(dragObject.dragView, from)
        val scale = dragObject.dragView.scaleX
        val xOffset = (scale - 1f) * dragObject.dragView.measuredWidth / 2f
        val yOffset = (scale - 1f) * dragObject.dragView.measuredHeight / 2f
        from.left += xOffset.toInt()
        from.right -= xOffset.toInt()
        from.top += yOffset.toInt()
        from.bottom -= yOffset.toInt()
        duration = if (abs(uy) > abs(ux)) initFlingUpDuration() else initFlingLeftDuration()
        animationTimeFraction = duration.toFloat() / (duration + DRAG_END_DELAY)
        // Don't highlight the icon as it's animating
        dragObject.dragView.setColor(0)
        val duration = duration + DRAG_END_DELAY
        val startTime = AnimationUtils.currentAnimationTimeMillis()
        // NOTE: Because it takes time for the first frame of animation to actually be
        // called and we expect the animation to be a continuation of the fling, we have
        // to account for the time that has elapsed since the fling finished.  And since
        // we don't have a startDelay, we will always get call to update when we call
        // start() (which we want to ignore).
        val tInterpolator: TimeInterpolator = object : TimeInterpolator {
            private var count = -1
            private var offset = 0f
            override fun getInterpolation(t: Float): Float {
                if (count < 0) {
                    count++
                } else if (count == 0) {
                    offset = 0.5f.coerceAtMost((AnimationUtils.currentAnimationTimeMillis() - startTime).toFloat() / duration)
                    count++
                }
                return 1f.coerceAtMost(offset + t)
            }
        }
        val onAnimationEndRunnable = Runnable {
            mLauncher.stateManager.goToState(NORMAL)
            dropTarget.completeDrop(dragObject)
        }
        dragLayer.animateView(dragObject.dragView, this, duration, tInterpolator,
                onAnimationEndRunnable, ANIMATION_END_DISAPPEAR, null)
    }

    /**
     * The fling animation is based on the following system
     * - Apply a constant force in the y direction to causing the fling to decelerate.
     * - The animation runs for the time taken by the object to go out of the screen.
     * - Calculate a constant acceleration in x direction such that the object reaches
     * [.mIconRect] in the given time.
     */
    private fun initFlingUpDuration(): Int {
        val sY = -from.bottom.toFloat()
        var d = uy * uy + 2 * sY * MAX_ACCELERATION
        if (d >= 0) { // sY can be reached under the MAX_ACCELERATION. Use MAX_ACCELERATION for y direction.
            ay = MAX_ACCELERATION
        } else { // sY is not reachable, decrease the acceleration so that sY is almost reached.
            d = 0f
            ay = uy * uy / (2 * -sY)
        }
        val t = (-uy - sqrt(d.toDouble())) / ay
        val sX = -from.exactCenterX() + iconRect!!.exactCenterX()
        // Find horizontal acceleration such that: u*t + a*t*t/2 = s
        ax = ((sX - t * ux) * 2 / (t * t)).toFloat()
        return t.roundToInt()
    }

    /**
     * The fling animation is based on the following system
     * - Apply a constant force in the x direction to causing the fling to decelerate.
     * - The animation runs for the time taken by the object to go out of the screen.
     * - Calculate a constant acceleration in y direction such that the object reaches
     * [.mIconRect] in the given time.
     */
    private fun initFlingLeftDuration(): Int {
        val sX = -from.right.toFloat()
        var d = ux * ux + 2 * sX * MAX_ACCELERATION
        if (d >= 0) { // sX can be reached under the MAX_ACCELERATION. Use MAX_ACCELERATION for x direction.
            ax = MAX_ACCELERATION
        } else { // sX is not reachable, decrease the acceleration so that sX is almost reached.
            d = 0f
            ax = ux * ux / (2 * -sX)
        }
        val t = (-ux - sqrt(d.toDouble())) / ax
        val sY = -from.exactCenterY() + iconRect!!.exactCenterY()
        // Find vertical acceleration such that: u*t + a*t*t/2 = s
        ay = ((sY - t * uy) * 2 / (t * t)).toFloat()
        return t.roundToInt()
    }

    override fun onAnimationUpdate(animation: ValueAnimator) {
        var t = animation.animatedFraction
        t = if (t > animationTimeFraction) {
            1f
        } else {
            t / animationTimeFraction
        }
        val dragView = dragLayer.animatedView as DragView
        val time = t * duration
        dragView.translationX = time * ux + from.left + ax * time * time / 2
        dragView.translationY = time * uy + from.top + ay * time * time / 2
        dragView.alpha = 1f - alphaInterpolator.getInterpolation(t)
    }

    companion object {
        /**
         * Maximum acceleration in one dimension (pixels per milliseconds)
         */
        private const val MAX_ACCELERATION = 0.5f
        private const val DRAG_END_DELAY = 300
    }
}