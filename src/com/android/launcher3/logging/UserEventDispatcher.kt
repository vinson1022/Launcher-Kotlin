/*
 * Copyright (C) 2012 The Android Open Source Project
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
package com.android.launcher3.logging

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.View
import com.android.launcher3.DeviceProfile
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.ItemInfo
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.userevent.nano.LauncherLogProto
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Command.STOP
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch.*
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Type.TIP
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Type.TOUCH
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType.OVERVIEW
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType.WORKSPACE
import com.android.launcher3.userevent.nano.LauncherLogProto.ItemType.TASK
import com.android.launcher3.userevent.nano.LauncherLogProto.LauncherEvent
import com.android.launcher3.userevent.nano.LauncherLogProto.Target.Type.*
import com.android.launcher3.userevent.nano.LauncherLogProto.TipType.BOUNCE
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.InstantAppResolver
import com.android.launcher3.util.InstantAppResolver.Companion.newInstance
import com.android.launcher3.util.LogConfig
import java.util.*

/**
 * Manages the creation of [LauncherEvent].
 * To debug this class, execute following command before side loading a new apk.
 *
 * $ adb shell setprop log.tag.UserEvent VERBOSE
 */
class UserEventDispatcher {
    interface UserEventDelegate {
        fun modifyUserEvent(event: LauncherEvent?)
    }

    /**
     * Implemented by containers to provide a container source for a given child.
     */
    interface LogContainerProvider {
        /**
         * Copies data from the source to the destination proto.
         *
         * @param v            source of the data
         * @param info         source of the data
         * @param target       dest of the data
         * @param targetParent dest of the data
         */
        fun fillInLogContainerData(v: View?, info: ItemInfo?, target: LauncherLogProto.Target?, targetParent: LauncherLogProto.Target?)
    }

    private var sessionStarted = false
    private var elapsedContainerMillis = 0L
    private var elapsedSessionMillis = 0L
    private var actionDurationMillis = 0L
    private var isInMultiWindowMode = false
    private var isInLandscapeMode = false
    private var uuidStr1: String? = null
    protected var instantAppResolver: InstantAppResolver? = null
    private var appOrTaskLaunch = false
    private var delegate: UserEventDelegate? = null
    //                      APP_ICON    SHORTCUT    WIDGET
    // --------------------------------------------------------------
    // packageNameHash      required    optional    required
    // componentNameHash    required                required
    // intentHash                       required
    // --------------------------------------------------------------
    /**
     * Fills in the container data on the given event if the given view is not null.
     * @return whether container data was added.
     */
    protected fun fillInLogContainerData(event: LauncherEvent, v: View?): Boolean {
        // Fill in grid(x,y), pageIndex of the child and container type of the parent
        val provider = getLaunchProviderRecursive(v)
        if (v == null || v.tag !is ItemInfo || provider == null) {
            return false
        }
        val itemInfo = v.tag as ItemInfo
        provider.fillInLogContainerData(v, itemInfo, event.srcTarget[0], event.srcTarget[1])
        return true
    }

    fun logAppLaunch(v: View?, intent: Intent) {
        val event = LoggerUtils.newLauncherEvent(LoggerUtils.newTouchAction(TAP),
                LoggerUtils.newItemTarget(v, instantAppResolver), LoggerUtils.newTarget(CONTAINER))
        if (fillInLogContainerData(event, v)) {
            delegate?.modifyUserEvent(event)
            fillIntentInfo(event.srcTarget[0], intent)
        }
        dispatchUserEvent(event, intent)
        appOrTaskLaunch = true
    }

    fun logActionTip(actionType: Int, viewType: Int) {}
    fun logTaskLaunchOrDismiss(action: Int, direction: Int, taskIndex: Int,
                               componentKey: ComponentKey) {
        val event = LoggerUtils.newLauncherEvent(LoggerUtils.newTouchAction(action),  // TAP or SWIPE or FLING
                LoggerUtils.newTarget(ITEM))
        if (action == SWIPE || action == FLING) {
            // Direction DOWN means the task was launched, UP means it was dismissed.
            event.action.dir = direction
        }
        event.srcTarget[0].itemType = TASK
        event.srcTarget[0].pageIndex = taskIndex
        fillComponentInfo(event.srcTarget[0], componentKey.componentName)
        dispatchUserEvent(event, null)
        appOrTaskLaunch = true
    }

    protected fun fillIntentInfo(target: LauncherLogProto.Target, intent: Intent) {
        target.intentHash = intent.hashCode()
        fillComponentInfo(target, intent.component)
    }

    private fun fillComponentInfo(target: LauncherLogProto.Target, cn: ComponentName?) {
        if (cn != null) {
            target.packageNameHash = (uuidStr1 + cn.packageName).hashCode()
            target.componentHash = (uuidStr1 + cn.flattenToString()).hashCode()
        }
    }

    fun logNotificationLaunch(v: View?, intent: PendingIntent) {
        val event = LoggerUtils.newLauncherEvent(LoggerUtils.newTouchAction(TAP),
                LoggerUtils.newItemTarget(v, instantAppResolver), LoggerUtils.newTarget(CONTAINER))
        if (fillInLogContainerData(event, v)) {
            event.srcTarget[0].packageNameHash = (uuidStr1 + intent.creatorPackage).hashCode()
        }
        dispatchUserEvent(event, null)
    }

    fun logActionCommand(command: Int, srcContainerType: Int, dstContainerType: Int) {
        logActionCommand(command, LoggerUtils.newContainerTarget(srcContainerType),
                if (dstContainerType >= 0) LoggerUtils.newContainerTarget(dstContainerType) else null)
    }

    @JvmOverloads
    fun logActionCommand(command: Int, srcTarget: LauncherLogProto.Target?, dstTarget: LauncherLogProto.Target? = null) {
        val event = LoggerUtils.newLauncherEvent(LoggerUtils.newCommandAction(command), srcTarget)
        if (command == STOP) {
            if (appOrTaskLaunch || !sessionStarted) {
                sessionStarted = false
                return
            }
        }
        if (dstTarget != null) {
            event.destTarget = arrayOfNulls(1)
            event.destTarget[0] = dstTarget
            event.action.isStateChange = true
        }
        dispatchUserEvent(event, null)
    }

    /**
     * TODO: Make this function work when a container view is passed as the 2nd param.
     */
    fun logActionCommand(command: Int, itemView: View?, srcContainerType: Int) {
        val event = LoggerUtils.newLauncherEvent(LoggerUtils.newCommandAction(command),
                LoggerUtils.newItemTarget(itemView, instantAppResolver), LoggerUtils.newTarget(CONTAINER))
        if (fillInLogContainerData(event, itemView)) {
            // TODO: Remove the following two lines once fillInLogContainerData can take in a
            // container view.
            event.srcTarget[0].type = CONTAINER
            event.srcTarget[0].containerType = srcContainerType
        }
        dispatchUserEvent(event, null)
    }

    fun logActionOnControl(action: Int, controlType: Int, parentContainerType: Int) {
        logActionOnControl(action, controlType, null, parentContainerType)
    }

    fun logActionOnControl(action: Int, controlType: Int, parentContainer: Int,
                           grandParentContainer: Int) {
        val event = LoggerUtils.newLauncherEvent(LoggerUtils.newTouchAction(action),
                LoggerUtils.newControlTarget(controlType),
                LoggerUtils.newContainerTarget(parentContainer),
                LoggerUtils.newContainerTarget(grandParentContainer))
        dispatchUserEvent(event, null)
    }

    @JvmOverloads
    fun logActionOnControl(action: Int, controlType: Int, controlInContainer: View? = null,
                           parentContainerType: Int = -1) {
        val event =
                if (controlInContainer == null && parentContainerType < 0)
                    LoggerUtils.newLauncherEvent(
                            LoggerUtils.newTouchAction(action),
                            LoggerUtils.newTarget(CONTROL))
                else
                    LoggerUtils.newLauncherEvent(
                            LoggerUtils.newTouchAction(action),
                            LoggerUtils.newTarget(CONTROL),
                            LoggerUtils.newTarget(CONTAINER))
        event.srcTarget[0].controlType = controlType
        controlInContainer?.let { fillInLogContainerData(event, it) }
        if (parentContainerType >= 0) {
            event.srcTarget[1].containerType = parentContainerType
        }
        if (action == DRAGDROP) {
            event.actionDurationMillis = SystemClock.uptimeMillis() - actionDurationMillis
        }
        dispatchUserEvent(event, null)
    }

    fun logActionTapOutside(target: LauncherLogProto.Target?) {
        val event = LoggerUtils.newLauncherEvent(LoggerUtils.newTouchAction(TOUCH),
                target)
        event.action.isOutside = true
        dispatchUserEvent(event, null)
    }

    fun logActionBounceTip(containerType: Int) {
        val event = LoggerUtils.newLauncherEvent(LoggerUtils.newAction(TIP),
                LoggerUtils.newContainerTarget(containerType))
        event.srcTarget[0].tipType = BOUNCE
        dispatchUserEvent(event, null)
    }

    @JvmOverloads
    fun logActionOnContainer(action: Int, dir: Int, containerType: Int, pageIndex: Int = 0) {
        val event = LoggerUtils.newLauncherEvent(LoggerUtils.newTouchAction(action),
                LoggerUtils.newContainerTarget(containerType))
        event.action.dir = dir
        event.srcTarget[0].pageIndex = pageIndex
        dispatchUserEvent(event, null)
    }

    /**
     * Used primarily for swipe up and down when state changes when swipe up happens from the
     * navbar bezel, the {@param srcChildContainerType} is NAVBAR and
     * {@param srcParentContainerType} is either one of the two
     * (1) WORKSPACE: if the launcher is the foreground activity
     * (2) APP: if another app was the foreground activity
     */
    fun logStateChangeAction(action: Int, dir: Int, srcChildTargetType: Int,
                             srcParentContainerType: Int, dstContainerType: Int,
                             pageIndex: Int) {
        val event = if (srcChildTargetType == TASK) {
            LoggerUtils.newLauncherEvent(LoggerUtils.newTouchAction(action),
                    LoggerUtils.newItemTarget(srcChildTargetType),
                    LoggerUtils.newContainerTarget(srcParentContainerType))
        } else {
            LoggerUtils.newLauncherEvent(LoggerUtils.newTouchAction(action),
                    LoggerUtils.newContainerTarget(srcChildTargetType),
                    LoggerUtils.newContainerTarget(srcParentContainerType))
        }.apply {
            destTarget = arrayOfNulls(1)
            destTarget[0] = LoggerUtils.newContainerTarget(dstContainerType)
            this.action.dir = dir
            this.action.isStateChange = true
            srcTarget[0].pageIndex = pageIndex
        }
        dispatchUserEvent(event, null)
        resetElapsedContainerMillis("state changed")
    }

    fun logActionOnItem(action: Int, dir: Int, itemType: Int) {
        val itemTarget = LoggerUtils.newTarget(ITEM)
        itemTarget.itemType = itemType
        val event = LoggerUtils.newLauncherEvent(
                LoggerUtils.newTouchAction(action), itemTarget)
        event.action.dir = dir
        dispatchUserEvent(event, null)
    }

    fun logDeepShortcutsOpen(icon: View?) {
        val provider = getLaunchProviderRecursive(icon)
        if (icon == null || icon.tag !is ItemInfo) {
            return
        }
        val info = icon.tag as ItemInfo
        val event = LoggerUtils.newLauncherEvent(
                LoggerUtils.newTouchAction(LONGPRESS),
                LoggerUtils.newItemTarget(info, instantAppResolver), LoggerUtils.newTarget(CONTAINER))
        provider!!.fillInLogContainerData(icon, info, event.srcTarget[0], event.srcTarget[1])
        dispatchUserEvent(event, null)
        resetElapsedContainerMillis("deep shortcut open")
    }

    /* Currently we are only interested in whether this event happens or not and don't
    * care about which screen moves to where. */
    fun logOverviewReorder() {
        val event = LoggerUtils.newLauncherEvent(LoggerUtils.newTouchAction(DRAGDROP),
                LoggerUtils.newContainerTarget(WORKSPACE),
                LoggerUtils.newContainerTarget(OVERVIEW))
        dispatchUserEvent(event, null)
    }

    fun logDragNDrop(dragObj: DragObject, dropTargetAsView: View?) {
        val event = LoggerUtils.newLauncherEvent(LoggerUtils.newTouchAction(DRAGDROP),
                LoggerUtils.newItemTarget(dragObj.originalDragInfo, instantAppResolver),
                LoggerUtils.newTarget(CONTAINER))
        event.destTarget = arrayOf(
                LoggerUtils.newItemTarget(dragObj.originalDragInfo, instantAppResolver),
                LoggerUtils.newDropTarget(dropTargetAsView)
        )
        dragObj.dragSource.fillInLogContainerData(null, dragObj.originalDragInfo,
                event.srcTarget[0], event.srcTarget[1])
        if (dropTargetAsView is LogContainerProvider) {
            (dropTargetAsView as LogContainerProvider).fillInLogContainerData(null,
                    dragObj.dragInfo, event.destTarget[0], event.destTarget[1])
        }
        event.actionDurationMillis = SystemClock.uptimeMillis() - actionDurationMillis
        dispatchUserEvent(event, null)
    }

    /**
     * Currently logs following containers: workspace, allapps, widget tray.
     * @param reason
     */
    fun resetElapsedContainerMillis(reason: String) {
        elapsedContainerMillis = SystemClock.uptimeMillis()
        if (!IS_VERBOSE) {
            return
        }
        Log.d(TAG, "resetElapsedContainerMillis reason=$reason")
    }

    fun startSession() {
        sessionStarted = true
        elapsedSessionMillis = SystemClock.uptimeMillis()
        elapsedContainerMillis = SystemClock.uptimeMillis()
    }

    fun resetActionDurationMillis() {
        actionDurationMillis = SystemClock.uptimeMillis()
    }

    fun dispatchUserEvent(ev: LauncherEvent, intent: Intent?) {
        appOrTaskLaunch = false
        ev.isInLandscapeMode = isInLandscapeMode
        ev.isInMultiWindowMode = isInMultiWindowMode
        ev.elapsedContainerMillis = SystemClock.uptimeMillis() - elapsedContainerMillis
        ev.elapsedSessionMillis = SystemClock.uptimeMillis() - elapsedSessionMillis
        if (!IS_VERBOSE) {
            return
        }
        var log = "\n-----------------------------------------------------" +
                "\naction:${LoggerUtils.getActionStr(ev.action)}"
        if (ev.srcTarget != null && ev.srcTarget.isNotEmpty()) {
            log += "\n Source ${getTargetsStr(ev.srcTarget)}"
        }
        if (ev.destTarget != null && ev.destTarget.isNotEmpty()) {
            log += "\n Destination ${getTargetsStr(ev.destTarget)}"
        }
        log += String.format(Locale.US,
                "\n Elapsed container %d ms, session %d ms, action %d ms",
                ev.elapsedContainerMillis,
                ev.elapsedSessionMillis,
                ev.actionDurationMillis)
        log += "\n isInLandscapeMode ${ev.isInLandscapeMode}"
        log += "\n isInMultiWindowMode ${ev.isInMultiWindowMode}"
        log += "\n\n"
        Log.d(TAG, log)
    }

    companion object {
        private const val MAXIMUM_VIEW_HIERARCHY_LEVEL = 5
        private const val TAG = "UserEvent"
        private val IS_VERBOSE = FeatureFlags.IS_DOGFOOD_BUILD && Utilities.isPropertyEnabled(LogConfig.USEREVENT)
        private const val UUID_STORAGE = "uuid"

        @JvmStatic
        @JvmOverloads
        fun newInstance(context: Context, dp: DeviceProfile,
                        delegate: UserEventDelegate? = null): UserEventDispatcher {
            val sharedPrefs = Utilities.getDevicePrefs(context)
            var uuidStr = sharedPrefs.getString(UUID_STORAGE, null)
            if (uuidStr == null) {
                uuidStr = UUID.randomUUID().toString()
                sharedPrefs.edit().putString(UUID_STORAGE, uuidStr).apply()
            }
            val ued = Utilities.getOverrideObject(UserEventDispatcher::class.java,
                    context.applicationContext, R.string.user_event_dispatcher_class)
            ued.delegate = delegate
            ued.isInLandscapeMode = dp.isVerticalBarLayout
            ued.isInMultiWindowMode = dp.isMultiWindowMode
            ued.uuidStr1 = uuidStr
            ued.instantAppResolver = newInstance(context)
            return ued
        }

        /**
         * Recursively finds the parent of the given child which implements IconLogInfoProvider
         */
        fun getLaunchProviderRecursive(v: View?): LogContainerProvider? {
            var parent = v?.parent
            parent ?: return null

            // Optimization to only check up to 5 parents.
            var count = MAXIMUM_VIEW_HIERARCHY_LEVEL
            while (parent != null && count-- > 0) {
                parent = if (parent is LogContainerProvider) {
                    return parent
                } else {
                    parent.parent
                }
            }
            return null
        }

        private fun getTargetsStr(targets: Array<LauncherLogProto.Target>): String {
            val builder = StringBuilder()
            builder.append("child:${LoggerUtils.getTargetStr(targets[0])}")
            targets.forEach {
                builder.append("\tparent:${LoggerUtils.getTargetStr(it)}")
            }
            return builder.toString()
        }
    }
}