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
package com.android.launcher3.keyboard

import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import android.widget.PopupMenu
import com.android.launcher3.ItemInfo
import com.android.launcher3.Launcher
import com.android.launcher3.popup.PopupContainerWithArrow.Companion.getOpen
import java.util.*

/**
 * Handles showing a popup menu with available custom actions for a launcher icon.
 * This allows exposing various custom actions using keyboard shortcuts.
 */
class CustomActionsPopup(
        private val launcher: Launcher,
        private val icon: View?
) : PopupMenu.OnMenuItemClickListener {
    private var delegate = getOpen(launcher)?.accessibilityDelegate ?: launcher.accessibilityDelegate
    private val actionList: List<AccessibilityAction>
        get() {
            if (icon == null || icon.tag !is ItemInfo) {
                return emptyList()
            }
            val info = AccessibilityNodeInfo.obtain()
            delegate!!.addSupportedActions(icon, info, true)
            val result = ArrayList(info.actionList)
            info.recycle()
            return result
        }

    fun canShow() = actionList.isNotEmpty()

    fun show(): Boolean {
        val actions = actionList
        if (actions.isEmpty()) {
            return false
        }
        val popup = PopupMenu(launcher, icon)
        popup.setOnMenuItemClickListener(this)
        val menu = popup.menu
        for (action in actions) {
            menu.add(Menu.NONE, action.id, Menu.NONE, action.label)
        }
        popup.show()
        return true
    }

    override fun onMenuItemClick(menuItem: MenuItem): Boolean {
        return delegate!!.performAction(icon, icon!!.tag as ItemInfo, menuItem.itemId)
    }
}