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
package com.android.launcher3.logging

import android.os.Process
import com.android.launcher3.ItemInfo
import com.android.launcher3.LauncherAppWidgetInfo
import com.android.launcher3.LauncherSettings.BaseLauncherColumns.ITEM_TYPE_APPLICATION
import com.android.launcher3.LauncherSettings.BaseLauncherColumns.ITEM_TYPE_SHORTCUT
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET
import com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT
import com.android.launcher3.model.nano.LauncherDumpProto
import com.android.launcher3.model.nano.LauncherDumpProto.ContainerType.FOLDER
import com.android.launcher3.model.nano.LauncherDumpProto.ContainerType.WORKSPACE
import com.android.launcher3.model.nano.LauncherDumpProto.DumpTarget
import com.android.launcher3.model.nano.LauncherDumpProto.DumpTarget.Type.CONTAINER
import com.android.launcher3.model.nano.LauncherDumpProto.DumpTarget.Type.ITEM
import com.android.launcher3.model.nano.LauncherDumpProto.ItemType.*
import com.android.launcher3.model.nano.LauncherDumpProto.UserType

/**
 * This class can be used when proto definition doesn't support nesting.
 */
class DumpTargetWrapper() {
    var dumpTarget: DumpTarget? = null
    var children = mutableListOf<DumpTargetWrapper>()

    constructor(containerType: Int, id: Int) : this() {
        dumpTarget = newContainerTarget(containerType, id)
    }

    constructor(info: ItemInfo) : this() {
        dumpTarget = newItemTarget(info)
    }

    fun add(child: DumpTargetWrapper) {
        children.add(child)
    }

    // add a delimiter empty object
    val flattenedList: List<DumpTarget?>
        get() {
            val list = mutableListOf<DumpTarget?>()
            list.add(dumpTarget)
            if (children.isNotEmpty()) {
                for (t in children) {
                    list.addAll(t.flattenedList)
                }
                list.add(dumpTarget) // add a delimiter empty object
            }
            return list
        }

    private fun newItemTarget(info: ItemInfo): DumpTarget {
        return DumpTarget().apply {
            type = ITEM
            when (info.itemType) {
                ITEM_TYPE_APPLICATION -> itemType = APP_ICON
                ITEM_TYPE_SHORTCUT -> itemType = UNKNOWN_ITEMTYPE
                ITEM_TYPE_APPWIDGET -> itemType = WIDGET
                ITEM_TYPE_DEEP_SHORTCUT -> itemType = SHORTCUT
            }
        }
    }

    private fun newContainerTarget(type: Int, id: Int) = DumpTarget().apply {
        this.type = CONTAINER
        containerType = type
        pageId = id
    }

    fun writeToDumpTarget(info: ItemInfo): DumpTarget? {
        return dumpTarget?.apply {
            if (info is LauncherAppWidgetInfo) {
                component = info.providerName.flattenToString()
                packageName = info.providerName.packageName
            } else {
                component = if (info.targetComponent == null) "" else info.targetComponent.flattenToString()
                packageName = if (info.targetComponent == null) "" else info.targetComponent.packageName
            }
            gridX = info.cellX
            gridY = info.cellY
            spanX = info.spanX
            spanY = info.spanY
            userType = if (info.user == Process.myUserHandle()) UserType.DEFAULT else UserType.WORK
        }
    }

    companion object {
        @JvmStatic
        fun getDumpTargetStr(t: DumpTarget?): String {
            return if (t == null) {
                ""
            } else when (t.type) {
                ITEM -> getItemStr(t)
                CONTAINER -> {
                    var str = LoggerUtils.getFieldName(t.containerType, LauncherDumpProto.ContainerType::class.java)
                    if (t.containerType == WORKSPACE) {
                        str += " id=" + t.pageId
                    } else if (t.containerType == FOLDER) {
                        str += " grid(" + t.gridX + "," + t.gridY + ")"
                    }
                    str
                }
                else -> "UNKNOWN TARGET TYPE"
            }
        }

        private fun getItemStr(t: DumpTarget): String {
            var typeStr = LoggerUtils.getFieldName(t.itemType, LauncherDumpProto.ItemType::class.java)
            if (t.packageName.isNotEmpty()) {
                typeStr += ", package=" + t.packageName
            }
            if (t.component.isNotEmpty()) {
                typeStr += ", component=" + t.component
            }
            return (typeStr + ", grid(" + t.gridX + "," + t.gridY + "), span(" + t.spanX + "," + t.spanY
                    + "), pageIdx=" + t.pageId + " user=" + t.userType)
        }
    }

}