/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.util.ArrayMap
import android.util.SparseArray
import android.view.View
import com.android.launcher3.AppInfo
import com.android.launcher3.ButtonDropTarget
import com.android.launcher3.ItemInfo
import com.android.launcher3.LauncherSettings
import com.android.launcher3.userevent.nano.LauncherLogExtensions.TargetExtension
import com.android.launcher3.userevent.nano.LauncherLogProto
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch.FLING
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Touch.SWIPE
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Type.COMMAND
import com.android.launcher3.userevent.nano.LauncherLogProto.Action.Type.TOUCH
import com.android.launcher3.userevent.nano.LauncherLogProto.ItemType.APP_ICON
import com.android.launcher3.userevent.nano.LauncherLogProto.ItemType.WEB_APP
import com.android.launcher3.userevent.nano.LauncherLogProto.LauncherEvent
import com.android.launcher3.userevent.nano.LauncherLogProto.Target.Type.*
import com.android.launcher3.userevent.nano.LauncherLogProto.TipType
import com.android.launcher3.util.InstantAppResolver
import java.lang.reflect.Modifier

/**
 * Helper methods for logging.
 */
object LoggerUtils {
    private val sNameCache = ArrayMap<Class<*>, SparseArray<String>>()
    private const val UNKNOWN = "UNKNOWN"
    fun getFieldName(value: Int, c: Class<*>): String {
        var cache: SparseArray<String>?
        synchronized(sNameCache) {
            cache = sNameCache[c]
            if (cache == null) {
                cache = SparseArray()
                for (f in c.declaredFields) {
                    if (f.type == Int::class.javaPrimitiveType && Modifier.isStatic(f.modifiers)) {
                        try {
                            f.isAccessible = true
                            cache!!.put(f.getInt(null), f.name)
                        } catch (e: IllegalAccessException) {
                            // Ignore
                        }
                    }
                }
                sNameCache[c] = cache
            }
        }
        val result = cache!![value]
        return result ?: UNKNOWN
    }

    fun getActionStr(action: LauncherLogProto.Action): String {
        var str = ""
        return when (action.type) {
            TOUCH -> {
                str += getFieldName(action.touch, LauncherLogProto.Action.Touch::class.java)
                if (action.touch == SWIPE || action.touch == FLING) {
                    str += " direction=" + getFieldName(action.dir, LauncherLogProto.Action.Direction::class.java)
                }
                str
            }
            COMMAND -> getFieldName(action.command, LauncherLogProto.Action.Command::class.java)
            else -> getFieldName(action.type, LauncherLogProto.Action.Type::class.java)
        }
    }

    fun getTargetStr(t: LauncherLogProto.Target?): String {
        if (t == null) {
            return ""
        }
        var str = ""
        when (t.type) {
            ITEM -> str = getItemStr(t)
            CONTROL -> str = getFieldName(t.controlType, LauncherLogProto.ControlType::class.java)
            CONTAINER -> {
                str = getFieldName(t.containerType, LauncherLogProto.ContainerType::class.java)
                if (t.containerType == LauncherLogProto.ContainerType.WORKSPACE ||
                        t.containerType == LauncherLogProto.ContainerType.HOTSEAT) {
                    str += " id=" + t.pageIndex
                } else if (t.containerType == LauncherLogProto.ContainerType.FOLDER) {
                    str += " grid(" + t.gridX + "," + t.gridY + ")"
                }
            }
            else -> str += "UNKNOWN TARGET TYPE"
        }
        if (t.tipType != TipType.DEFAULT_NONE) {
            str += " " + getFieldName(t.tipType, TipType::class.java)
        }
        return str
    }

    private fun getItemStr(t: LauncherLogProto.Target): String {
        var typeStr = getFieldName(t.itemType, LauncherLogProto.ItemType::class.java)
        if (t.packageNameHash != 0) {
            typeStr += ", packageHash=" + t.packageNameHash
        }
        if (t.componentHash != 0) {
            typeStr += ", componentHash=" + t.componentHash
        }
        if (t.intentHash != 0) {
            typeStr += ", intentHash=" + t.intentHash
        }
        if ((t.packageNameHash != 0 || t.componentHash != 0 || t.intentHash != 0) &&
                t.itemType != LauncherLogProto.ItemType.TASK) {
            typeStr += (", predictiveRank=" + t.predictedRank + ", grid(" + t.gridX + "," + t.gridY
                    + "), span(" + t.spanX + "," + t.spanY
                    + "), pageIdx=" + t.pageIndex)
        }
        if (t.itemType == LauncherLogProto.ItemType.TASK) {
            typeStr += ", pageIdx=" + t.pageIndex
        }
        return typeStr
    }

    fun newItemTarget(itemType: Int): LauncherLogProto.Target {
        val t = newTarget(ITEM)
        t.itemType = itemType
        return t
    }

    @JvmStatic
    fun newItemTarget(v: View?, instantAppResolver: InstantAppResolver?): LauncherLogProto.Target {
        return if (v?.tag is ItemInfo) newItemTarget(v.tag as ItemInfo, instantAppResolver) else newTarget(ITEM)
    }

    @JvmStatic
    fun newItemTarget(info: ItemInfo, instantAppResolver: InstantAppResolver?): LauncherLogProto.Target {
        val t = newTarget(ITEM)
        when (info.itemType) {
            LauncherSettings.Favorites.ITEM_TYPE_APPLICATION -> {
                t.itemType = if (instantAppResolver != null && info is AppInfo
                        && instantAppResolver.isInstantApp(info)) WEB_APP else APP_ICON
                t.predictedRank = -100 // Never assigned
            }
            LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT -> t.itemType = LauncherLogProto.ItemType.SHORTCUT
            LauncherSettings.Favorites.ITEM_TYPE_FOLDER -> t.itemType = LauncherLogProto.ItemType.FOLDER_ICON
            LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET -> t.itemType = LauncherLogProto.ItemType.WIDGET
            LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT -> t.itemType = LauncherLogProto.ItemType.DEEPSHORTCUT
        }
        return t
    }

    fun newDropTarget(v: View?): LauncherLogProto.Target {
        if (v !is ButtonDropTarget) {
            return newTarget(CONTAINER)
        }
        return if (v is ButtonDropTarget) {
            v.dropTargetForLogging
        } else newTarget(CONTROL)
    }

    fun newTarget(targetType: Int, extension: TargetExtension?): LauncherLogProto.Target {
        val t = LauncherLogProto.Target()
        t.type = targetType
        t.extension = extension
        return t
    }

    @JvmStatic
    fun newTarget(targetType: Int): LauncherLogProto.Target {
        val t = LauncherLogProto.Target()
        t.type = targetType
        return t
    }

    fun newControlTarget(controlType: Int): LauncherLogProto.Target {
        val t = newTarget(CONTROL)
        t.controlType = controlType
        return t
    }

    @JvmStatic
    fun newContainerTarget(containerType: Int): LauncherLogProto.Target {
        val t = newTarget(CONTAINER)
        t.containerType = containerType
        return t
    }

    fun newAction(type: Int): LauncherLogProto.Action {
        val a = LauncherLogProto.Action()
        a.type = type
        return a
    }

    @JvmStatic
    fun newCommandAction(command: Int): LauncherLogProto.Action {
        val a = newAction(COMMAND)
        a.command = command
        return a
    }

    fun newTouchAction(touch: Int): LauncherLogProto.Action {
        val a = newAction(TOUCH)
        a.touch = touch
        return a
    }

    @JvmStatic
    fun newLauncherEvent(action: LauncherLogProto.Action?, vararg srcTargets: LauncherLogProto.Target?): LauncherEvent {
        val event = LauncherEvent()
        event.srcTarget = srcTargets
        event.action = action
        return event
    }
}