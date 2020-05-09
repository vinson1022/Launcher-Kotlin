package com.android.launcher3.popup

import android.view.View
import com.android.launcher3.*
import com.android.launcher3.userevent.nano.LauncherLogProto
import com.android.launcher3.util.InstantAppResolver.Companion.newInstance
import com.android.launcher3.util.PackageManagerHelper
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.widget.WidgetsBottomSheet

/**
 * Represents a system shortcut for a given app. The shortcut should have a static label and
 * icon, and an onClickListener that depends on the item that the shortcut services.
 *
 * Example system shortcuts, defined as inner classes, include Widgets and AppInfo.
 */
abstract class SystemShortcut<T : BaseDraggingActivity>(val iconResId: Int, val labelResId: Int) : ItemInfo() {

    abstract fun getOnClickListener(activity: T, itemInfo: ItemInfo): View.OnClickListener?

    class Widgets : SystemShortcut<Launcher>(R.drawable.ic_widget, R.string.widget_button_text) {
        override fun getOnClickListener(activity: Launcher,
                                        itemInfo: ItemInfo): View.OnClickListener? {
            activity.popupDataProvider.getWidgetsForPackageUser(PackageUserKey(
                    itemInfo.targetComponent.packageName, itemInfo.user))
                    ?: return null
            return View.OnClickListener { view: View? ->
                AbstractFloatingView.closeAllOpenViews(activity)
                val widgetsBottomSheet = activity.layoutInflater.inflate(
                        R.layout.widgets_bottom_sheet, activity.dragLayer, false) as WidgetsBottomSheet
                widgetsBottomSheet.populateAndShow(itemInfo)
                activity.userEventDispatcher.logActionOnControl(LauncherLogProto.Action.Touch.TAP,
                        LauncherLogProto.ControlType.WIDGETS_BUTTON, view)
            }
        }
    }

    class AppInfo : SystemShortcut<BaseDraggingActivity>(R.drawable.ic_info_no_shadow, R.string.app_info_drop_target_label) {
        override fun getOnClickListener(
                activity: BaseDraggingActivity, itemInfo: ItemInfo): View.OnClickListener? {
            return View.OnClickListener { view: View? ->
                val sourceBounds = activity.getViewBounds(view)
                val opts = activity.getActivityLaunchOptionsAsBundle(view)
                PackageManagerHelper(activity).startDetailsActivityForInfo(
                        itemInfo, sourceBounds, opts)
                activity.userEventDispatcher.logActionOnControl(LauncherLogProto.Action.Touch.TAP,
                        LauncherLogProto.ControlType.APPINFO_TARGET, view)
            }
        }
    }

    class Install : SystemShortcut<BaseDraggingActivity>(R.drawable.ic_install_no_shadow, R.string.install_drop_target_label) {
        override fun getOnClickListener(
                activity: BaseDraggingActivity, itemInfo: ItemInfo): View.OnClickListener? {
            val supportsWebUI = itemInfo is ShortcutInfo &&
                    itemInfo.hasStatusFlag(ShortcutInfo.FLAG_SUPPORTS_WEB_UI)
            var isInstantApp = false
            if (itemInfo is com.android.launcher3.AppInfo) {
                isInstantApp = newInstance(activity).isInstantApp(itemInfo)
            }
            val enabled = supportsWebUI || isInstantApp
            return if (!enabled) {
                null
            } else createOnClickListener(activity, itemInfo)
        }

        private fun createOnClickListener(
                activity: BaseDraggingActivity?, itemInfo: ItemInfo): View.OnClickListener {
            return View.OnClickListener { view: View ->
                val intent = PackageManagerHelper(view.context).getMarketIntent(
                        itemInfo.targetComponent.packageName)
                activity!!.startActivitySafely(view, intent, itemInfo)
                AbstractFloatingView.closeAllOpenViews(activity)
            }
        }
    }

}