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
package com.android.launcher3.popup

import android.animation.AnimatorSet
import android.animation.LayoutTransition
import android.annotation.TargetApi
import android.content.Context
import android.graphics.Point
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Pair
import android.view.MotionEvent
import android.view.View
import android.view.View.OnLongClickListener
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import com.android.launcher3.*
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.accessibility.ShortcutMenuAccessibilityDelegate
import com.android.launcher3.badge.BadgeInfo
import com.android.launcher3.dragndrop.DragController.DragListener
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.dragndrop.DragOptions.PreDragCondition
import com.android.launcher3.logging.LoggerUtils
import com.android.launcher3.notification.NotificationInfo
import com.android.launcher3.notification.NotificationItemView
import com.android.launcher3.notification.NotificationKeyData
import com.android.launcher3.notification.NotificationKeyData.Companion.extractKeysOnly
import com.android.launcher3.notification.NotificationMainView
import com.android.launcher3.popup.SystemShortcut.Widgets
import com.android.launcher3.shortcuts.DeepShortcutManager.Companion.supportsShortcuts
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.shortcuts.ShortcutDragPreviewProvider
import com.android.launcher3.touch.ItemLongClickListener
import com.android.launcher3.userevent.nano.LauncherLogProto
import com.android.launcher3.util.PackageUserKey
import com.android.launcher3.util.PackageUserKey.Companion.fromItemInfo
import kotlin.math.hypot

/**
 * A container for shortcuts to deep links and notifications associated with an app.
 */
@TargetApi(Build.VERSION_CODES.N)
class PopupContainerWithArrow
@JvmOverloads
constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : ArrowPopup(context, attrs, defStyleAttr), DragSource, DragListener, OnLongClickListener, OnTouchListener {

    private val shortcuts = mutableListOf<DeepShortcutView>()
    private val interceptTouchDown = PointF()
    private val iconLastTouchPos = Point()
    private val startDragThreshold = resources.getDimensionPixelSize(R.dimen.deep_shortcuts_start_drag_threshold)
    private val accessibilityDelegate = ShortcutMenuAccessibilityDelegate(launcher)
    private var originalIcon: BubbleTextView? = null
    private var notificationItemView: NotificationItemView? = null
    private var numNotifications = 0
    private var systemShortcutContainer: ViewGroup? = null

    override fun getAccessibilityDelegate() = accessibilityDelegate

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            interceptTouchDown[ev.x] = ev.y
        }
        return if (notificationItemView?.onInterceptTouchEvent(ev) == true) {
            true
        } else {
            hypot(interceptTouchDown.x - ev.x.toDouble(), interceptTouchDown.y - ev.y.toDouble()) > ViewConfiguration.get(context).scaledTouchSlop
        }
        // Stop sending touch events to deep shortcut views if user moved beyond touch slop.
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return if (notificationItemView != null) {
            notificationItemView!!.onTouchEvent(ev) || super.onTouchEvent(ev)
        } else super.onTouchEvent(ev)
    }

    override fun isOfType(type: Int) = type and TYPE_ACTION_POPUP != 0

    override fun logActionCommand(command: Int) {
        launcher.userEventDispatcher.logActionCommand(
                command, originalIcon, LauncherLogProto.ContainerType.DEEPSHORTCUTS)
    }

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val dl = launcher.dragLayer
            if (!dl.isEventOverView(this, ev)) {
                launcher.userEventDispatcher.logActionTapOutside(
                        LoggerUtils.newContainerTarget(LauncherLogProto.ContainerType.DEEPSHORTCUTS))
                close(true)

                // We let touches on the original icon go through so that users can launch
                // the app with one tap if they don't find a shortcut they want.
                return originalIcon == null || !dl.isEventOverView(originalIcon!!, ev)
            }
        }
        return false
    }

    override fun onInflationComplete(isReversed: Boolean) {
        if (isReversed) {
            notificationItemView?.inverseGutterMargin()
        }

        // Update dividers
        val count = childCount
        var lastView: DeepShortcutView? = null
        for (i in 0 until count) {
            val view = getChildAt(i)
            if (view.visibility == View.VISIBLE && view is DeepShortcutView) {
                lastView?.setDividerVisibility(View.VISIBLE)
                lastView = view
                lastView.setDividerVisibility(View.INVISIBLE)
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.P)
    private fun populateAndShow(originalIcon: BubbleTextView?, shortcutIds: List<String>,
                                notificationKeys: List<NotificationKeyData>, systemShortcuts: List<SystemShortcut<BaseDraggingActivity>>) {
        numNotifications = notificationKeys.size
        this.originalIcon = originalIcon

        // Add views
        if (numNotifications > 0) {
            // Add notification entries
            View.inflate(context, R.layout.notification_content, this)
            notificationItemView = NotificationItemView(this)
            if (numNotifications == 1) {
                notificationItemView!!.removeFooter()
            }
            updateNotificationHeader()
        }
        val viewsToFlip = childCount
        systemShortcutContainer = this
        if (shortcutIds.isNotEmpty()) {
            notificationItemView?.addGutter()
            for (i in shortcutIds.size downTo 1) {
                shortcuts.add(inflateAndAdd(R.layout.deep_shortcut, this))
            }
            updateHiddenShortcuts()
            if (systemShortcuts.isNotEmpty()) {
                systemShortcutContainer = inflateAndAdd<ViewGroup>(R.layout.system_shortcut_icons, this)
                for (shortcut in systemShortcuts) {
                    initializeSystemShortcut(
                            R.layout.system_shortcut_icon_only, systemShortcutContainer, shortcut)
                }
            }
        } else if (systemShortcuts.isNotEmpty()) {
            notificationItemView?.addGutter()
            for (shortcut in systemShortcuts) {
                initializeSystemShortcut(R.layout.system_shortcut, this, shortcut)
            }
        }
        reorderAndShow(viewsToFlip)
        val originalItemInfo = originalIcon!!.tag as ItemInfo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            accessibilityPaneTitle = titleForAccessibility
        }
        launcher.dragController.addDragListener(this)
        this.originalIcon!!.forceHideBadge(true)

        // All views are added. Animate layout from now on.
        layoutTransition = LayoutTransition()

        // Load the shortcuts on a background thread and update the container as it animates.
        val workerLooper = LauncherModel.getWorkerLooper()
        Handler(workerLooper).postAtFrontOfQueue(PopupPopulator.createUpdateRunnable(
                launcher, originalItemInfo, Handler(Looper.getMainLooper()),
                this, shortcutIds, shortcuts, notificationKeys))
    }

    private val titleForAccessibility: String
        get() = context.getString(if (numNotifications == 0) R.string.action_deep_shortcut else R.string.shortcuts_menu_with_notifications_description)

    override fun getAccessibilityTarget(): Pair<View, String> {
        return Pair.create(this, "")!!
    }

    override fun getTargetObjectLocation(outPos: Rect?) {
        launcher.dragLayer.getDescendantRectRelativeToSelf(originalIcon!!, outPos!!)
        outPos.top += originalIcon!!.paddingTop
        outPos.left += originalIcon!!.paddingLeft
        outPos.right -= originalIcon!!.paddingRight
        outPos.bottom = outPos.top + if (originalIcon!!.icon != null) originalIcon!!.icon.bounds.height() else originalIcon!!.height
    }

    fun applyNotificationInfos(notificationInfos: List<NotificationInfo>) {
        notificationItemView?.applyNotificationInfos(notificationInfos)
    }

    private fun updateHiddenShortcuts() {
        val allowedCount = if (notificationItemView != null) PopupPopulator.MAX_SHORTCUTS_IF_NOTIFICATIONS else PopupPopulator.MAX_SHORTCUTS
        val originalHeight = resources.getDimensionPixelSize(R.dimen.bg_popup_item_height)
        val itemHeight = if (notificationItemView != null) resources.getDimensionPixelSize(R.dimen.bg_popup_item_condensed_height) else originalHeight
        val iconScale = itemHeight.toFloat() / originalHeight
        val total = shortcuts.size
        for (i in 0 until total) {
            val view = shortcuts[i]
            view.visibility = if (i >= allowedCount) View.GONE else View.VISIBLE
            view.layoutParams.height = itemHeight
            view.iconView.scaleX = iconScale
            view.iconView.scaleY = iconScale
        }
    }

    private fun updateDividers() {
        val count = childCount
        var lastView: DeepShortcutView? = null
        for (i in 0 until count) {
            val view = getChildAt(i)
            if (view.visibility == View.VISIBLE && view is DeepShortcutView) {
                lastView?.setDividerVisibility(View.VISIBLE)
                lastView = view
                lastView.setDividerVisibility(View.INVISIBLE)
            }
        }
    }

    override fun onWidgetsBound() {
        val itemInfo = originalIcon!!.tag as ItemInfo
        val widgetInfo = Widgets()
        val onClickListener = widgetInfo.getOnClickListener(launcher, itemInfo)
        var widgetsView: View? = null
        val count = systemShortcutContainer!!.childCount
        for (i in 0 until count) {
            val systemShortcutView = systemShortcutContainer!!.getChildAt(i)
            if (systemShortcutView.tag is Widgets) {
                widgetsView = systemShortcutView
                break
            }
        }
        if (onClickListener != null && widgetsView == null) {
            // We didn't have any widgets cached but now there are some, so enable the shortcut.
            if (systemShortcutContainer !== this) {
                initializeSystemShortcut(
                        R.layout.system_shortcut_icon_only, systemShortcutContainer, widgetInfo)
            } else {
                // If using the expanded system shortcut (as opposed to just the icon), we need to
                // reopen the container to ensure measurements etc. all work out. While this could
                // be quite janky, in practice the user would typically see a small flicker as the
                // animation restarts partway through, and this is a very rare edge case anyway.
                close(false)
                showForIcon(originalIcon)
            }
        } else if (onClickListener == null && widgetsView != null) {
            // No widgets exist, but we previously added the shortcut so remove it.
            if (systemShortcutContainer !== this) {
                systemShortcutContainer!!.removeView(widgetsView)
            } else {
                close(false)
                showForIcon(originalIcon)
            }
        }
    }

    private fun <R : BaseDraggingActivity, T : SystemShortcut<out R>> initializeSystemShortcut(resId: Int, container: ViewGroup?, info: T) {
        val view = inflateAndAdd<View>(resId, container!!)
        if (view is DeepShortcutView) {
            // Expanded system shortcut, with both icon and text shown on white background.
            view.iconView.setBackgroundResource(info.iconResId)
            view.bubbleText.setText(info.labelResId)
        } else if (view is ImageView) {
            // Only the system shortcut icon shows on a gray background header.
            view.setImageResource(info.iconResId)
            view.contentDescription = context.getText(info.labelResId)
        }
        view.tag = info as SystemShortcut<BaseDraggingActivity>
        view.setOnClickListener(info.getOnClickListener(launcher,
                originalIcon!!.tag as ItemInfo))
    }

    /**
     * Determines when the deferred drag should be started.
     *
     * Current behavior:
     * - Start the drag if the touch passes a certain distance from the original touch down.
     */
    fun createPreDragCondition(): PreDragCondition {
        return object : PreDragCondition {
            override fun shouldStartDrag(distanceDragged: Double) = distanceDragged > startDragThreshold

            override fun onPreDragStart(dragObject: DragObject) {
                if (isAboveIcon) {
                    // Hide only the icon, keep the text visible.
                    originalIcon!!.setIconVisible(false)
                    originalIcon!!.visibility = View.VISIBLE
                } else {
                    // Hide both the icon and text.
                    originalIcon!!.visibility = View.INVISIBLE
                }
            }

            override fun onPreDragEnd(dragObject: DragObject, dragStarted: Boolean) {
                originalIcon!!.setIconVisible(true)
                if (dragStarted) {
                    // Make sure we keep the original icon hidden while it is being dragged.
                    originalIcon!!.visibility = View.INVISIBLE
                } else {
                    launcher.userEventDispatcher.logDeepShortcutsOpen(originalIcon)
                    if (!isAboveIcon) {
                        // Show the icon but keep the text hidden.
                        originalIcon!!.visibility = View.VISIBLE
                        originalIcon!!.setTextVisibility(false)
                    }
                }
            }
        }
    }

    /**
     * Updates the notification header if the original icon's badge updated.
     */
    fun updateNotificationHeader(updatedBadges: Set<PackageUserKey?>) {
        val itemInfo = originalIcon!!.tag as ItemInfo
        val packageUser = fromItemInfo(itemInfo)
        if (updatedBadges.contains(packageUser)) {
            updateNotificationHeader()
        }
    }

    private fun updateNotificationHeader() {
        val itemInfo = originalIcon!!.tag as ItemInfoWithIcon
        val badgeInfo = launcher.getBadgeInfoForItem(itemInfo)
        if (notificationItemView != null && badgeInfo != null) {
            notificationItemView!!.updateHeader(
                    badgeInfo.notificationCount, itemInfo.iconColor)
        }
    }

    fun trimNotifications(updatedBadges: Map<PackageUserKey?, BadgeInfo?>) {
        if (notificationItemView == null) return

        val originalInfo = originalIcon!!.tag as ItemInfo
        val badgeInfo = updatedBadges[fromItemInfo(originalInfo)]
        if (badgeInfo == null || badgeInfo.notificationKeys.size == 0) {
            // No more notifications, remove the notification views and expand all shortcuts.
            notificationItemView!!.removeAllViews()
            notificationItemView = null
            updateHiddenShortcuts()
            updateDividers()
        } else {
            notificationItemView!!.trimNotifications(
                    extractKeysOnly(badgeInfo.notificationKeys))
        }
    }

    override fun onDropCompleted(target: View, d: DragObject, success: Boolean) {}

    override fun onDragStart(dragObject: DragObject, options: DragOptions) {
        // Either the original icon or one of the shortcuts was dragged.
        // Hide the container, but don't remove it yet because that interferes with touch events.
        deferContainerRemoval = true
        animateClose()
    }

    override fun onDragEnd() {
        if (!isOpen) {
            if (openCloseAnimator != null) {
                // Close animation is running.
                deferContainerRemoval = false
            } else {
                // Close animation is not running.
                if (deferContainerRemoval) {
                    closeComplete()
                }
            }
        }
    }

    override fun fillInLogContainerData(v: View, info: ItemInfo, target: LauncherLogProto.Target, targetParent: LauncherLogProto.Target) {
        if (info === NotificationMainView.NOTIFICATION_ITEM_INFO) {
            target.itemType = LauncherLogProto.ItemType.NOTIFICATION
        } else {
            target.itemType = LauncherLogProto.ItemType.DEEPSHORTCUT
            target.rank = info.rank
        }
        targetParent.containerType = LauncherLogProto.ContainerType.DEEPSHORTCUTS
    }

    override fun onCreateCloseAnimation(anim: AnimatorSet?) {
        // Animate original icon's text back in.
        anim!!.play(originalIcon!!.createTextAlphaAnimator(true /* fadeIn */))
        originalIcon!!.forceHideBadge(false)
    }

    override fun closeComplete() {
        super.closeComplete()
        originalIcon!!.setTextVisibility(originalIcon!!.shouldTextBeVisible())
        originalIcon!!.forceHideBadge(false)
    }

    override fun onTouch(v: View, ev: MotionEvent): Boolean {
        // Touched a shortcut, update where it was touched so we can drag from there on long click.
        when (ev.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> iconLastTouchPos[ev.x.toInt()] = ev.y.toInt()
        }
        return false
    }

    override fun onLongClick(v: View): Boolean {
        if (!ItemLongClickListener.canStartDrag(launcher)) return false
        // Return early if not the correct view
        if (v.parent !is DeepShortcutView) return false

        // Long clicked on a shortcut.
        val sv = v.parent as DeepShortcutView
        sv.setWillDrawIcon(false)

        // Move the icon to align with the center-top of the touch point
        val iconShift = Point()
        iconShift.x = iconLastTouchPos.x - sv.iconCenter.x
        iconShift.y = iconLastTouchPos.y - launcher.deviceProfile.iconSizePx
        val dv = launcher.workspace.beginDragShared(sv.iconView,
                this, sv.finalInfo,
                ShortcutDragPreviewProvider(sv.iconView, iconShift), DragOptions())
        dv.animateShift(-iconShift.x, -iconShift.y)

        // TODO: support dragging from within folder without having to close it
        closeOpenContainer(launcher, TYPE_FOLDER)
        return false
    }

    companion object {
        /**
         * Shows the notifications and deep shortcuts associated with {@param icon}.
         * @return the container if shown or null.
         */
        @JvmStatic
        fun showForIcon(icon: BubbleTextView?): PopupContainerWithArrow? {
            val launcher = Launcher.getLauncher(icon!!.context)
            if (getOpen(launcher) != null) {
                // There is already an items container open, so don't open this one.
                icon.clearFocus()
                return null
            }
            val itemInfo = icon.tag as ItemInfo
            if (!supportsShortcuts(itemInfo)) {
                return null
            }
            val popupDataProvider = launcher.popupDataProvider
            val shortcutIds = popupDataProvider.getShortcutIdsForItem(itemInfo)
            val notificationKeys = popupDataProvider
                    .getNotificationKeysForItem(itemInfo)
            val systemShortcuts = popupDataProvider
                    .getEnabledSystemShortcutsForItem(itemInfo)
            val container = launcher.layoutInflater.inflate(
                    R.layout.popup_container, launcher.dragLayer, false) as PopupContainerWithArrow
            container.populateAndShow(icon, shortcutIds, notificationKeys, systemShortcuts)
            return container
        }

        /**
         * Returns a PopupContainerWithArrow which is already open or null
         */
        @JvmStatic
        fun getOpen(launcher: Launcher?): PopupContainerWithArrow? {
            return getOpenView(launcher, TYPE_ACTION_POPUP)
        }
    }
}