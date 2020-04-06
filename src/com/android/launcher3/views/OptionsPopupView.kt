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
package com.android.launcher3.views

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.RectF
import android.util.ArrayMap
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.widget.Toast
import com.android.launcher3.*
import com.android.launcher3.popup.ArrowPopup
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.userevent.nano.LauncherLogProto
import com.android.launcher3.widget.WidgetsFullSheet
import kotlinx.android.synthetic.main.system_shortcut.view.*
import java.util.*

/**
 * Popup shown on long pressing an empty space in launcher
 */
class OptionsPopupView
@JvmOverloads constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int = 0
) : ArrowPopup(context, attrs, defStyleAttr), View.OnClickListener, OnLongClickListener {

    private val itemMap = ArrayMap<View, OptionItem>()
    private lateinit var targetRect: RectF

    override fun onClick(view: View) {
        handleViewClick(view, LauncherLogProto.Action.Touch.TAP)
    }

    override fun onLongClick(view: View): Boolean {
        return handleViewClick(view, LauncherLogProto.Action.Touch.LONGPRESS)
    }

    private fun handleViewClick(view: View, action: Int): Boolean {
        val item = itemMap[view] ?: return false
        if (item.controlTypeForLog > 0) {
            logTap(action, item.controlTypeForLog)
        }
        if (item.clickListener.onLongClick(view)) {
            close(true)
            return true
        }
        return false
    }

    private fun logTap(action: Int, controlType: Int) {
        mLauncher.userEventDispatcher.logActionOnControl(action, controlType)
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action != MotionEvent.ACTION_DOWN) {
            return false
        }
        if (mLauncher.dragLayer.isEventOverView(this, ev)) {
            return false
        }
        close(true)
        return true
    }

    override fun logActionCommand(command: Int) {
        // TODO:
    }

    override fun isOfType(type: Int): Boolean {
        return type and AbstractFloatingView.TYPE_OPTIONS_POPUP != 0
    }

    override fun getTargetObjectLocation(outPos: Rect) {
        targetRect.roundOut(outPos)
    }

    class OptionItem(val labelRes: Int, val iconRes: Int, val controlTypeForLog: Int,
                     val clickListener: OnLongClickListener)

    companion object {
        fun show(launcher: Launcher, targetRect: RectF, items: List<OptionItem>) {
            val popup = launcher.layoutInflater
                    .inflate(R.layout.longpress_options_menu, launcher.dragLayer, false) as OptionsPopupView
            popup.targetRect = targetRect
            for (item in items) {
                val view = popup.inflateAndAdd<DeepShortcutView>(R.layout.system_shortcut, popup)
                view.iconView.setBackgroundResource(item.iconRes)
                view.bubble_text.setText(item.labelRes)
                view.setDividerVisibility(View.INVISIBLE)
                view.setOnClickListener(popup)
                view.setOnLongClickListener(popup)
                popup.itemMap[view] = item
            }
            popup.reorderAndShow(popup.childCount)
        }

        @JvmStatic
        fun showDefaultOptions(launcher: Launcher, x: Float, y: Float) {
            var x = x
            var y = y
            val halfSize = launcher.resources.getDimension(R.dimen.options_menu_thumb_size) / 2
            if (x < 0 || y < 0) {
                x = launcher.dragLayer.width / 2f
                y = launcher.dragLayer.height / 2f
            }
            val target = RectF(x - halfSize, y - halfSize, x + halfSize, y + halfSize)
            val options = ArrayList<OptionItem>()
            options.add(OptionItem(R.string.wallpaper_button_text, R.drawable.ic_wallpaper,
                    LauncherLogProto.ControlType.WALLPAPER_BUTTON, OnLongClickListener { v: View -> startWallpaperPicker(v) }))
            options.add(OptionItem(R.string.widget_button_text, R.drawable.ic_widget,
                    LauncherLogProto.ControlType.WIDGETS_BUTTON, OnLongClickListener { view: View -> onWidgetsClicked(view) }))
            options.add(OptionItem(R.string.settings_button_text, R.drawable.ic_setting,
                    LauncherLogProto.ControlType.SETTINGS_BUTTON, OnLongClickListener { view: View -> startSettings(view) }))
            show(launcher, target, options)
        }

        fun onWidgetsClicked(view: View): Boolean {
            val launcher = Launcher.getLauncher(view.context)
            return if (launcher.packageManager.isSafeMode) {
                Toast.makeText(launcher, R.string.safemode_widget_error, Toast.LENGTH_SHORT).show()
                false
            } else {
                WidgetsFullSheet.show(launcher, true)
                true
            }
        }

        fun startSettings(view: View): Boolean {
            val launcher = Launcher.getLauncher(view.context)
            launcher.startActivity(Intent(Intent.ACTION_APPLICATION_PREFERENCES)
                    .setPackage(launcher.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return true
        }

        /**
         * Event handler for the wallpaper picker button that appears after a long press
         * on the home screen.
         */
        fun startWallpaperPicker(v: View): Boolean {
            val launcher = Launcher.getLauncher(v.context)
            if (!Utilities.isWallpaperAllowed(launcher)) {
                Toast.makeText(launcher, R.string.msg_disabled_by_admin, Toast.LENGTH_SHORT).show()
                return false
            }
            val intent = Intent(Intent.ACTION_SET_WALLPAPER)
                    .putExtra(Utilities.EXTRA_WALLPAPER_OFFSET,
                            launcher.workspace.wallpaperOffsetForCenterPage)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val pickerPackage = launcher.getString(R.string.wallpaper_picker_package)
            if (pickerPackage.isNotEmpty()) {
                intent.setPackage(pickerPackage)
            } else {
                // If there is no target package, use the default intent chooser animation
                intent.putExtra(BaseDraggingActivity.INTENT_EXTRA_IGNORE_LAUNCH_ANIMATION, true)
            }
            return launcher.startActivitySafely(v, intent, null)
        }
    }
}