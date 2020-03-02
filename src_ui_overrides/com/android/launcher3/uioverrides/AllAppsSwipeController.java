package com.android.launcher3.uioverrides;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;

import android.view.MotionEvent;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.AnimationComponents;
import com.android.launcher3.touch.AbstractStateChangeTouchController;
import com.android.launcher3.touch.SwipeDetector;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;

/**
 * TouchController to switch between NORMAL and ALL_APPS state.
 */
public class AllAppsSwipeController extends AbstractStateChangeTouchController {

    private MotionEvent mTouchDownEvent;

    public AllAppsSwipeController(Launcher l) {
        super(l, SwipeDetector.VERTICAL);
    }

    @Override
    protected boolean canInterceptTouch(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            mTouchDownEvent = ev;
        }
        if (currentAnimation != null) {
            // If we are already animating from a previous state, we can intercept.
            return true;
        }
        if (AbstractFloatingView.getTopOpenView(getLauncher()) != null) {
            return false;
        }
        if (!getLauncher().isInState(NORMAL) && !getLauncher().isInState(ALL_APPS)) {
            // Don't listen for the swipe gesture if we are already in some other state.
            return false;
        }
        if (getLauncher().isInState(ALL_APPS) && !getLauncher().getAppsView().shouldContainerScroll(ev)) {
            return false;
        }
        return true;
    }

    @Override
    protected LauncherState getTargetState(LauncherState fromState, boolean isDragTowardPositive) {
        if (fromState == NORMAL && isDragTowardPositive) {
            return ALL_APPS;
        } else if (fromState == ALL_APPS && !isDragTowardPositive) {
            return NORMAL;
        }
        return fromState;
    }

    @Override
    protected int getLogContainerTypeForNormalState() {
        return getLauncher().getDragLayer().isEventOverView(getLauncher().getHotseat(), mTouchDownEvent) ?
                ContainerType.HOTSEAT : ContainerType.WORKSPACE;
    }

    @Override
    protected float initCurrentAnimation(@AnimationComponents int animComponents) {
        float range = getShiftRange();
        long maxAccuracy = (long) (2 * range);
        currentAnimation = getLauncher().getStateManager()
                .createAnimationToNewWorkspace(toState, maxAccuracy, animComponents);
        float startVerticalShift = fromState.getVerticalProgress(getLauncher()) * range;
        float endVerticalShift = toState.getVerticalProgress(getLauncher()) * range;
        float totalShift = endVerticalShift - startVerticalShift;
        return 1 / totalShift;
    }
}
