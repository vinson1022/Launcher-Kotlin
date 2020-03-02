/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.launcher3.touch

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.view.HapticFeedbackConstants.CONTEXT_CLICK
import android.view.MotionEvent
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAnimUtils
import com.android.launcher3.LauncherAnimUtils.MIN_PROGRESS_TO_ALL_APPS
import com.android.launcher3.LauncherState
import com.android.launcher3.LauncherStateManager.*
import com.android.launcher3.Utilities
import com.android.launcher3.Utilities.SINGLE_FRAME_MS
import com.android.launcher3.anim.AnimationSuccessListener
import com.android.launcher3.anim.AnimatorPlaybackController
import com.android.launcher3.anim.AnimatorSetBuilder
import com.android.launcher3.anim.Interpolators
import com.android.launcher3.touch.SwipeDetector.Companion.calculateDuration
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction.DOWN
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Direction.UP
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch.FLING
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch.SWIPE
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType.ALLAPPS
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType.TASKSWITCHER
import com.android.launcher3.util.FlingBlockCheck
import com.android.launcher3.util.PendingAnimation
import com.android.launcher3.util.TouchController
import kotlin.math.max
import kotlin.math.sign

/**
 * TouchController for handling state changes
 */
abstract class AbstractStateChangeTouchController(
        protected val launcher: Launcher,
        dir: SwipeDetector.Direction
) : TouchController, SwipeDetector.Listener {
    private val detector: SwipeDetector = SwipeDetector(launcher, this, dir)
    private var noIntercept = false
    private var startContainerType = 0
    private var startState: LauncherState? = null
    @JvmField
    protected var fromState: LauncherState? = null
    @JvmField
    protected var toState: LauncherState? = null
    @JvmField
    protected var currentAnimation: AnimatorPlaybackController? = null
    private var pendingAnimation: PendingAnimation? = null
    private var startProgress = 0f
    // Ratio of transition process [0, 1] to drag displacement (px)
    private var progressMultiplier = 0f
    private var displacementShift = 0f
    private var canBlockFling = false
    private val flingBlockCheck = FlingBlockCheck()
    private var atomicAnim: AnimatorSet? = null
    private var passedOverviewAtomicThreshold = false
    // AtomicAnim plays the atomic components of the state animations when we pass the threshold.
    // However, if we reinit to transition to a new state (e.g. OVERVIEW -> ALL_APPS) before the
    // atomic animation finishes, we only control the non-atomic components so that we don't
    // interfere with the atomic animation. When the atomic animation ends, we start controlling
    // the atomic components as well, using this controller.
    private var atomicComponentsController: AnimatorPlaybackController? = null
    private var atomicComponentsStartProgress = 0f

    protected abstract fun canInterceptTouch(ev: MotionEvent): Boolean

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            noIntercept = !canInterceptTouch(ev)
            if (noIntercept) return false
            // Now figure out which direction scroll events the controller will start
            // calling the callbacks.
            val directionsToDetectScroll: Int
            var ignoreSlopWhenSettling = false
            if (currentAnimation != null) {
                directionsToDetectScroll = SwipeDetector.DIRECTION_BOTH
                ignoreSlopWhenSettling = true
            } else {
                directionsToDetectScroll = swipeDirection
                if (directionsToDetectScroll == 0) {
                    noIntercept = true
                    return false
                }
            }
            detector.setDetectableScrollConditions(
                    directionsToDetectScroll, ignoreSlopWhenSettling)
        }
        if (noIntercept) return false
        onControllerTouchEvent(ev)
        return detector.isDraggingOrSettling
    }

    private val swipeDirection: Int
        get() {
            val fromState = launcher.stateManager.state
            var swipeDirection = 0
            if (getTargetState(fromState, true) !== fromState) {
                swipeDirection = swipeDirection or SwipeDetector.DIRECTION_POSITIVE
            }
            if (getTargetState(fromState, false) !== fromState) {
                swipeDirection = swipeDirection or SwipeDetector.DIRECTION_NEGATIVE
            }
            return swipeDirection
        }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        return detector.onTouchEvent(ev)
    }

    protected val shiftRange: Float
        get() = launcher.allAppsController.shiftRange

    /**
     * Returns the state to go to from fromState given the drag direction. If there is no state in
     * that direction, returns fromState.
     */
    protected abstract fun getTargetState(fromState: LauncherState?,
                                          isDragTowardPositive: Boolean): LauncherState

    protected abstract fun initCurrentAnimation(@AnimationComponents animComponents: Int): Float
    /**
     * Returns the container that the touch started from when leaving NORMAL state.
     */
    protected abstract val logContainerTypeForNormalState: Int

    private fun reinitCurrentAnimation(reachedToState: Boolean, isDragTowardPositive: Boolean): Boolean {
        val newFromState = if (fromState == null) launcher.stateManager.state else if (reachedToState) toState!! else fromState!!
        val newToState = getTargetState(newFromState, isDragTowardPositive)
        if (newFromState === fromState && newToState === toState || newFromState === newToState) return false
        fromState = newFromState
        toState = newToState
        startProgress = 0f
        passedOverviewAtomicThreshold = false
        if (currentAnimation != null) {
            currentAnimation!!.onCancelRunnable = null
        }
        var animComponents = if (goingBetweenNormalAndOverview(fromState, toState)) NON_ATOMIC_COMPONENT else ANIM_ALL
        if (atomicAnim != null) {
            // Control the non-atomic components until the atomic animation finishes, then control
            // the atomic components as well.
            animComponents = NON_ATOMIC_COMPONENT
            atomicAnim!!.addListener(object : AnimationSuccessListener() {
                override fun onAnimationSuccess(animation: Animator) {
                    cancelAtomicComponentsController()
                    if (currentAnimation != null) {
                        atomicComponentsStartProgress = currentAnimation!!.progressFraction
                        val duration = (shiftRange * 2).toLong()
                        atomicComponentsController = AnimatorPlaybackController.wrap(
                                createAtomicAnimForState(fromState, toState, duration), duration)
                        atomicComponentsController?.dispatchOnStart()
                    }
                }
            })
        }
        if (goingBetweenNormalAndOverview(fromState, toState)) {
            cancelAtomicComponentsController()
        }
        progressMultiplier = initCurrentAnimation(animComponents)
        currentAnimation!!.dispatchOnStart()
        return true
    }

    private fun goingBetweenNormalAndOverview(fromState: LauncherState?, toState: LauncherState?): Boolean {
        return ((fromState === LauncherState.NORMAL || fromState === LauncherState.OVERVIEW)
                && (toState === LauncherState.NORMAL || toState === LauncherState.OVERVIEW)
                && pendingAnimation == null)
    }

    override fun onDragStart(start: Boolean) {
        startState = launcher.stateManager.state
        when {
            startState === LauncherState.ALL_APPS -> startContainerType = ALLAPPS
            startState === LauncherState.NORMAL -> startContainerType = logContainerTypeForNormalState
            startState === LauncherState.OVERVIEW -> startContainerType = TASKSWITCHER
        }
        if (currentAnimation == null) {
            fromState = startState
            toState = null
            atomicComponentsController = null
            reinitCurrentAnimation(false, detector.wasInitialTouchPositive())
            displacementShift = 0f
        } else {
            currentAnimation!!.pause()
            startProgress = currentAnimation!!.progressFraction
        }
        canBlockFling = fromState === LauncherState.NORMAL
        flingBlockCheck.unblockFling()
    }

    override fun onDrag(displacement: Float, velocity: Float): Boolean {
        val deltaProgress = progressMultiplier * (displacement - displacementShift)
        val progress = deltaProgress + startProgress
        updateProgress(progress)
        val isDragTowardPositive = displacement - displacementShift < 0
        if (progress <= 0) {
            if (reinitCurrentAnimation(false, isDragTowardPositive)) {
                displacementShift = displacement
                if (canBlockFling) {
                    flingBlockCheck.blockFling()
                }
            }
        } else if (progress >= 1) {
            if (reinitCurrentAnimation(true, isDragTowardPositive)) {
                displacementShift = displacement
                if (canBlockFling) {
                    flingBlockCheck.blockFling()
                }
            }
        } else {
            flingBlockCheck.onEvent()
        }
        return true
    }

    private fun updateProgress(fraction: Float) {
        currentAnimation!!.setPlayFraction(fraction)
        atomicComponentsController?.setPlayFraction(fraction - atomicComponentsStartProgress)
        maybeUpdateAtomicAnim(fromState, toState, fraction)
    }

    /**
     * When going between normal and overview states, see if we passed the overview threshold and
     * play the appropriate atomic animation if so.
     */
    private fun maybeUpdateAtomicAnim(fromState: LauncherState?, toState: LauncherState?,
                                      progress: Float) {
        if (!goingBetweenNormalAndOverview(fromState, toState)) return

        val threshold = if (toState === LauncherState.OVERVIEW) ATOMIC_OVERVIEW_ANIM_THRESHOLD else 1f - ATOMIC_OVERVIEW_ANIM_THRESHOLD
        val passedThreshold = progress >= threshold
        if (passedThreshold != passedOverviewAtomicThreshold) {
            val atomicFromState = if (passedThreshold) fromState else toState
            val atomicToState = if (passedThreshold) toState else fromState
            passedOverviewAtomicThreshold = passedThreshold
            if (atomicAnim != null) {
                atomicAnim!!.cancel()
            }
            atomicAnim = createAtomicAnimForState(atomicFromState, atomicToState, ATOMIC_DURATION)
            atomicAnim!!.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    atomicAnim = null
                }
            })
            atomicAnim!!.start()
            launcher.dragLayer.performHapticFeedback(CONTEXT_CLICK)
        }
    }

    private fun createAtomicAnimForState(fromState: LauncherState?, targetState: LauncherState?,
                                         duration: Long): AnimatorSet {
        val builder = AnimatorSetBuilder()
        launcher.stateManager.prepareForAtomicAnimation(fromState, targetState, builder)
        val config = AnimationConfig()
        config.animComponents = ATOMIC_COMPONENT
        config.duration = duration
        for (handler in launcher.stateManager.stateHandlers) {
            handler.setStateWithAnimation(targetState, builder, config)
        }
        return builder.build()
    }

    override fun onDragEnd(velocity: Float, _fling: Boolean) {
        var fling = _fling
        val logAction = if (fling) FLING else SWIPE
        val blockedFling = fling && flingBlockCheck.isBlocked
        if (blockedFling) {
            fling = false
        }
        val progress = currentAnimation!!.progressFraction
        val targetState = if (fling) {
            if (sign(velocity).compareTo(sign(progressMultiplier)) == 0) toState else fromState
            // snap to top or bottom using the release velocity
        } else {
            val successProgress = if (toState === LauncherState.ALL_APPS) MIN_PROGRESS_TO_ALL_APPS else SUCCESS_TRANSITION_PROGRESS
            if (progress > successProgress) toState else fromState
        }
        val endProgress: Float
        val startProgress: Float
        val duration: Long
        // Increase the duration if we prevented the fling, as we are going against a high velocity.
        val durationMultiplier = if (blockedFling && targetState === fromState) LauncherAnimUtils.blockedFlingDurationFactor(velocity) else 1
        if (targetState === toState) {
            endProgress = 1f
            if (progress >= 1) {
                duration = 0
                startProgress = 1f
            } else {
                startProgress = Utilities.boundToRange(
                        progress + velocity * SINGLE_FRAME_MS * progressMultiplier, 0f, 1f)
                duration = calculateDuration(velocity,
                        endProgress - max(progress, 0f)) * durationMultiplier
            }
        } else {
            // Let the state manager know that the animation didn't go to the target state,
            // but don't cancel ourselves (we already clean up when the animation completes).
            val onCancel = currentAnimation!!.onCancelRunnable
            currentAnimation!!.onCancelRunnable = null
            currentAnimation!!.dispatchOnCancel()
            currentAnimation!!.onCancelRunnable = onCancel
            endProgress = 0f
            if (progress <= 0) {
                duration = 0
                startProgress = 0f
            } else {
                startProgress = Utilities.boundToRange(
                        progress + velocity * SINGLE_FRAME_MS * progressMultiplier, 0f, 1f)
                duration = calculateDuration(velocity,
                        Math.min(progress, 1f) - endProgress) * durationMultiplier
            }
        }
        currentAnimation!!.setEndAction { onSwipeInteractionCompleted(targetState, logAction) }
        val anim = currentAnimation!!.animationPlayer
        anim.setFloatValues(startProgress, endProgress)
        maybeUpdateAtomicAnim(fromState, targetState, if (targetState === toState) 1f else 0f)
        updateSwipeCompleteAnimation(anim, max(duration, remainingAtomicDuration),
                targetState, velocity, fling)
        currentAnimation!!.dispatchOnStart()
        if (fling && targetState === LauncherState.ALL_APPS) {
            launcher.appsView.addSpringFromFlingUpdateListener(anim, velocity)
        }
        anim.start()
        atomicAnim?.run {
            addListener(object : AnimationSuccessListener() {
                override fun onAnimationSuccess(animator: Animator) {
                    startAtomicComponentsAnim(endProgress, anim.duration)
                }
            })
        } ?: run { startAtomicComponentsAnim(endProgress, anim.duration) }
    }

    /**
     * Animates the atomic components from the current progress to the final progress.
     *
     * Note that this only applies when we are controlling the atomic components separately from
     * the non-atomic components, which only happens if we reinit before the atomic animation
     * finishes.
     */
    private fun startAtomicComponentsAnim(toProgress: Float, duration: Long) {
        atomicComponentsController?.apply {
            val atomicAnim = animationPlayer
            atomicAnim.setFloatValues(progressFraction, toProgress)
            atomicAnim.duration = duration
            atomicAnim.start()
            atomicAnim.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    atomicComponentsController = null
                }
            })
        }
    }

    private val remainingAtomicDuration: Long
        get() = atomicAnim?.run {
            if (Utilities.ATLEAST_OREO) {
                totalDuration - currentPlayTime
            } else {
                var remainingDuration = 0L
                for (anim in childAnimations) {
                    remainingDuration = max(remainingDuration, anim.duration)
                }
                remainingDuration
            }
        } ?: 0

    private fun updateSwipeCompleteAnimation(animator: ValueAnimator, expectedDuration: Long,
                                             targetState: LauncherState?, velocity: Float, isFling: Boolean) {
        animator.setDuration(expectedDuration).interpolator = Interpolators.scrollInterpolatorForVelocity(velocity)
    }

    private val directionForLog: Int
        get() = if (toState!!.ordinal > fromState!!.ordinal) UP else DOWN

    private fun onSwipeInteractionCompleted(targetState: LauncherState?, logAction: Int) {
        clearState()
        var shouldGoToTargetState = true
        if (pendingAnimation != null) {
            val reachedTarget = toState === targetState
            pendingAnimation!!.finish(reachedTarget, logAction)
            pendingAnimation = null
            shouldGoToTargetState = !reachedTarget
        }
        if (shouldGoToTargetState) {
            if (targetState !== startState) {
                logReachedState(logAction, targetState)
            }
            launcher.stateManager.goToState(targetState, false)
        }
    }

    private fun logReachedState(logAction: Int, targetState: LauncherState?) {
        // Transition complete. log the action
        launcher.userEventDispatcher.logStateChangeAction(logAction,
                directionForLog,
                startContainerType,
                startState!!.containerType,
                targetState!!.containerType,
                launcher.workspace.currentPage)
    }

    private fun clearState() {
        currentAnimation = null
        cancelAtomicComponentsController()
        detector.finishedScrolling()
        detector.setDetectableScrollConditions(0, false)
    }

    private fun cancelAtomicComponentsController() {
        if (atomicComponentsController != null) {
            atomicComponentsController!!.animationPlayer.cancel()
            atomicComponentsController = null
        }
    }

    companion object {
        private const val TAG = "ASCTouchController"
        // Progress after which the transition is assumed to be a success in case user does not fling
        const val SUCCESS_TRANSITION_PROGRESS = 0.5f
        /**
         * Play an atomic recents animation when the progress from NORMAL to OVERVIEW reaches this.
         */
        const val ATOMIC_OVERVIEW_ANIM_THRESHOLD = 0.5f
        protected const val ATOMIC_DURATION: Long = 200
    }
}