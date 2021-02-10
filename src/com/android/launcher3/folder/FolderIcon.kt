/*
 * Copyright (C) 2008 The Android Open Source Project
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
package com.android.launcher3.folder

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.util.AttributeSet
import android.util.Property
import android.view.*
import android.widget.FrameLayout
import com.android.launcher3.*
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.FolderInfo.FolderListener
import com.android.launcher3.anim.Interpolators
import com.android.launcher3.badge.BadgeRenderer
import com.android.launcher3.badge.FolderBadgeInfo
import com.android.launcher3.dragndrop.BaseItemDragListener
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.dragndrop.DragView
import com.android.launcher3.touch.ItemClickHandler
import com.android.launcher3.util.Thunk
import com.android.launcher3.widget.PendingAddShortcutInfo
import kotlinx.android.synthetic.main.folder_icon.view.*
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * An icon that can appear on in the workspace representing an [Folder].
 */
class FolderIcon : FrameLayout, FolderListener {
    @Thunk
    lateinit var launcher: Launcher

    @JvmField
    @Thunk
    var folder: Folder? = null
    private var info: FolderInfo? = null
    private val longPressHelper by lazy { CheckLongPressHelper(this) }
    private val stylusEventHelper by lazy {
        StylusEventHelper(SimpleOnStylusPressListener(this), this)
    }

    @JvmField
    var background = PreviewBackground()
    private var backgroundIsVisible = true
    private var previewVerifier: FolderIconPreviewVerifier? = null
    val layoutRule = ClippedFolderIconLayoutRule()
    val previewItemManager by lazy { PreviewItemManager(this) }
    private var tmpParams: PreviewItemDrawingParams? = PreviewItemDrawingParams(0f, 0f, 0f, 0f)
    private val currentPreviewItems: MutableList<BubbleTextView> = ArrayList()
    private val tempBounds = Rect()
    private var slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val openAlarm = Alarm()
    private var badgeInfo = FolderBadgeInfo()
    private var badgeRenderer: BadgeRenderer? = null
    private var badgeScale = 0f
    private val tempSpaceForBadgeOffset = Point()

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    override fun onSaveInstanceState(): Parcelable? {
        sStaticValuesDirty = true
        return super.onSaveInstanceState()
    }

    private fun setFolder(folder: Folder) {
        this.folder = folder
        previewVerifier = FolderIconPreviewVerifier(launcher.deviceProfile.inv)
        updatePreviewItems(false)
    }

    private fun willAcceptItem(item: ItemInfo): Boolean {
        val itemType = item.itemType
        return (itemType == LauncherSettings.Favorites.ITEM_TYPE_APPLICATION || itemType == LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT || itemType == LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT) && item !== info && !folder!!.isOpen
    }

    fun acceptDrop(dragInfo: ItemInfo): Boolean {
        return !folder!!.isDestroyed && willAcceptItem(dragInfo)
    }

    @JvmOverloads
    fun addItem(item: ShortcutInfo?, animate: Boolean = true) {
        info!!.add(item, animate)
    }

    private fun removeItem(item: ShortcutInfo?, animate: Boolean) {
        info!!.remove(item, animate)
    }

    fun onDragEnter(dragInfo: ItemInfo) {
        if (folder!!.isDestroyed || !willAcceptItem(dragInfo)) return
        val lp = layoutParams as CellLayout.LayoutParams
        val cl = parent.parent as CellLayout
        background.animateToAccept(cl, lp.cellX, lp.cellY)
        openAlarm.setOnAlarmListener(onOpenListener)
        if (SPRING_LOADING_ENABLED &&
                (dragInfo is AppInfo
                        || dragInfo is ShortcutInfo
                        || dragInfo is PendingAddShortcutInfo)) {
            openAlarm.setAlarm(ON_OPEN_DELAY)
        }
    }

    private val onOpenListener = OnAlarmListener {
        folder?.beginExternalDrag()
        folder?.animateOpen()
    }

    fun prepareCreateAnimation(destView: View?): Drawable {
        return previewItemManager.prepareCreateAnimation(destView!!)
    }

    fun performCreateAnimation(destInfo: ShortcutInfo?, destView: View?,
                               srcInfo: ShortcutInfo, srcView: DragView?, dstRect: Rect?,
                               scaleRelativeToDragLayer: Float) {
        prepareCreateAnimation(destView)
        addItem(destInfo)
        // This will animate the first item from it's position as an icon into its
        // position as the first item in the preview
        previewItemManager.createFirstItemAnimation(false, null).start()

        // This will animate the dragView (srcView) into the new folder
        onDrop(srcInfo, srcView, dstRect, scaleRelativeToDragLayer, 1, false)
    }

    fun performDestroyAnimation(onCompleteRunnable: Runnable?) {
        // This will animate the final item in the preview to be full size.
        previewItemManager.createFirstItemAnimation(true, onCompleteRunnable).start()
    }

    fun onDragExit() {
        background.animateToRest()
        openAlarm.cancelAlarm()
    }

    private fun onDrop(item: ShortcutInfo, animateView: DragView?, finalRect: Rect?,
                       scaleRelativeToDragLayer: Float, index: Int,
                       itemReturnedOnFailedDrop: Boolean) {
        var scaleToDragLayer = scaleRelativeToDragLayer
        var idx = index
        item.cellX = -1
        item.cellY = -1

        // Typically, the animateView corresponds to the DragView; however, if this is being done
        // after a configuration activity (ie. for a Shortcut being dragged from AllApps) we
        // will not have a view to animate
        if (animateView != null) {
            val dragLayer = launcher.dragLayer
            val from = Rect()
            dragLayer.getViewRectRelativeToSelf(animateView, from)
            var to = finalRect
            if (to == null) {
                to = Rect()
                val workspace = launcher.workspace
                // Set cellLayout and this to it's final state to compute final animation locations
                workspace.setFinalTransitionTransform()
                val scaleX = scaleX
                val scaleY = scaleY
                setScaleX(1.0f)
                setScaleY(1.0f)
                scaleToDragLayer = dragLayer.getDescendantRectRelativeToSelf(this, to)
                // Finished computing final animation locations, restore current state
                setScaleX(scaleX)
                setScaleY(scaleY)
                workspace.resetTransitionTransform()
            }
            val numItemsInPreview = min(ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW, idx + 1)
            var itemAdded = false
            if (itemReturnedOnFailedDrop || idx >= ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW) {
                val oldPreviewItems: List<BubbleTextView> = ArrayList(currentPreviewItems)
                addItem(item, false)
                currentPreviewItems.clear()
                currentPreviewItems.addAll(previewItems)
                if (oldPreviewItems != currentPreviewItems) {
                    for (i in currentPreviewItems.indices) {
                        if (currentPreviewItems[i].tag == item) {
                            // If the item dropped is going to be in the preview, we update the
                            // index here to reflect its position in the preview.
                            idx = i
                        }
                    }
                    previewItemManager.hidePreviewItem(idx, true)
                    previewItemManager.onDrop(oldPreviewItems, currentPreviewItems, item)
                    itemAdded = true
                } else {
                    removeItem(item, false)
                }
            }
            if (!itemAdded) {
                addItem(item)
            }
            val center = IntArray(2)
            val scale = getLocalCenterForIndex(idx, numItemsInPreview, center)
            center[0] = (scaleToDragLayer * center[0]).roundToInt()
            center[1] = (scaleToDragLayer * center[1]).roundToInt()
            to.offset(center[0] - animateView.measuredWidth / 2,
                    center[1] - animateView.measuredHeight / 2)
            val finalAlpha = if (idx < ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW) 0.5f else 0f
            val finalScale = scale * scaleToDragLayer
            dragLayer.animateView(animateView, from, to, finalAlpha, 1f, 1f, finalScale, finalScale, DROP_IN_ANIMATION_DURATION,
                    Interpolators.DEACCEL_2, Interpolators.ACCEL_2,
                    null, DragLayer.ANIMATION_END_DISAPPEAR, null)
            folder!!.hideItem(item)
            if (!itemAdded) previewItemManager.hidePreviewItem(idx, true)
            val finalIndex = idx
            postDelayed({
                previewItemManager.hidePreviewItem(finalIndex, false)
                folder!!.showItem(item)
                invalidate()
            }, DROP_IN_ANIMATION_DURATION)
        } else {
            addItem(item)
        }
    }

    fun onDrop(d: DragObject, itemReturnedOnFailedDrop: Boolean) {
        val item = when {
            d.dragInfo is AppInfo -> {
                // Came from all apps -- make a copy
                (d.dragInfo as AppInfo).makeShortcut()
            }
            d.dragSource is BaseItemDragListener -> {
                // Came from a different window -- make a copy
                ShortcutInfo(d.dragInfo as ShortcutInfo)
            }
            else -> {
                d.dragInfo as ShortcutInfo
            }
        }
        folder!!.notifyDrop()
        onDrop(item, d.dragView, null, 1.0f, info!!.contents.size,
                itemReturnedOnFailedDrop)
    }

    fun setBadgeInfo(badgeInfo: FolderBadgeInfo) {
        updateBadgeScale(this.badgeInfo.hasBadge(), badgeInfo.hasBadge())
        this.badgeInfo = badgeInfo
    }

    /**
     * Sets mBadgeScale to 1 or 0, animating if wasBadged or isBadged is false
     * (the badge is being added or removed).
     */
    private fun updateBadgeScale(wasBadged: Boolean, isBadged: Boolean) {
        val newBadgeScale = if (isBadged) 1f else 0f
        // Animate when a badge is first added or when it is removed.
        if (wasBadged xor isBadged && isShown) {
            createBadgeScaleAnimator(newBadgeScale).start()
        } else {
            badgeScale = newBadgeScale
            invalidate()
        }
    }

    fun createBadgeScaleAnimator(vararg badgeScales: Float): Animator {
        return ObjectAnimator.ofFloat(this, BADGE_SCALE_PROPERTY, *badgeScales)
    }

    fun hasBadge(): Boolean {
        return badgeInfo.hasBadge()
    }

    private fun getLocalCenterForIndex(index: Int, curNumItems: Int, center: IntArray): Float {
        tmpParams = previewItemManager.computePreviewItemDrawingParams(
                min(ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW, index), curNumItems, tmpParams)
        tmpParams?.apply {
            transX += background.basePreviewOffsetX
            transY += background.basePreviewOffsetY
            val intrinsicIconSize = previewItemManager.intrinsicIconSize
            val offsetX = transX + scale * intrinsicIconSize / 2
            val offsetY = transY + scale * intrinsicIconSize / 2
            center[0] = offsetX.roundToInt()
            center[1] = offsetY.roundToInt()
            return scale
        }
        return 1f
    }

    fun setBackgroundVisible(visible: Boolean) {
        backgroundIsVisible = visible
        invalidate()
    }

    var folderBackground: PreviewBackground
        get() = background
        set(bg) {
            background = bg
            background.setInvalidateDelegate(this)
        }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (!backgroundIsVisible) return
        previewItemManager.recomputePreviewDrawingParams()
        if (!background.drawingDelegated()) {
            background.drawBackground(canvas)
        }
        if (folder == null) return
        if (folder!!.itemCount == 0) return
        val saveCount: Int
        if (canvas.isHardwareAccelerated) {
            saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        } else {
            saveCount = canvas.save()
            canvas.clipPath(background.clipPath)
        }
        previewItemManager.draw(canvas)
        if (canvas.isHardwareAccelerated) {
            background.clipCanvasHardware(canvas)
        }
        canvas.restoreToCount(saveCount)
        if (!background.drawingDelegated()) {
            background.drawBackgroundStroke(canvas)
        }
        drawBadge(canvas)
    }

    fun drawBadge(canvas: Canvas?) {
        if (badgeInfo.hasBadge() || badgeScale > 0) {
            val offsetX = background.offsetX
            val offsetY = background.offsetY
            val previewSize = (background.previewSize * background.scale).toInt()
            tempBounds[offsetX, offsetY, offsetX + previewSize] = offsetY + previewSize

            // If we are animating to the accepting state, animate the badge out.
            val badgeScale = max(0f, badgeScale - background.scaleProgress)
            tempSpaceForBadgeOffset[width - tempBounds.right] = tempBounds.top
            badgeRenderer!!.draw(canvas, background.badgeColor, tempBounds,
                    badgeScale, tempSpaceForBadgeOffset)
        }
    }

    var textVisible: Boolean
        get() = name.visibility == View.VISIBLE
        set(visible) {
            if (visible) {
                name.visibility = View.VISIBLE
            } else {
                name.visibility = View.INVISIBLE
            }
        }

    /**
     * Returns the list of preview items displayed in the icon.
     */
    val previewItems: List<BubbleTextView>
        get() = getPreviewItemsOnPage(0)

    /**
     * Returns the list of "preview items" on {@param page}.
     */
    fun getPreviewItemsOnPage(page: Int): List<BubbleTextView> {
        previewVerifier!!.setFolderInfo(folder!!.info)
        val itemsToDisplay: MutableList<BubbleTextView> = ArrayList()
        val itemsOnPage = folder!!.getItemsOnPage(page)
        val numItems = itemsOnPage.size
        for (rank in 0 until numItems) {
            if (previewVerifier!!.isItemInPreview(rank, page)) {
                itemsToDisplay.add(itemsOnPage[rank])
            }
            if (itemsToDisplay.size == ClippedFolderIconLayoutRule.MAX_NUM_ITEMS_IN_PREVIEW) {
                break
            }
        }
        return itemsToDisplay
    }

    override fun verifyDrawable(who: Drawable): Boolean {
        return previewItemManager.verifyDrawable(who) || super.verifyDrawable(who)
    }

    override fun onItemsChanged(animate: Boolean) {
        updatePreviewItems(animate)
        invalidate()
        requestLayout()
    }

    private fun updatePreviewItems(animate: Boolean) {
        previewItemManager.updatePreviewItems(animate)
        currentPreviewItems.clear()
        currentPreviewItems.addAll(previewItems)
    }

    override fun prepareAutoUpdate() {}
    override fun onAdd(item: ShortcutInfo, rank: Int) {
        val wasBadged = badgeInfo.hasBadge()
        badgeInfo.addBadgeInfo(launcher.getBadgeInfoForItem(item))
        val isBadged = badgeInfo.hasBadge()
        updateBadgeScale(wasBadged, isBadged)
        invalidate()
        requestLayout()
    }

    override fun onRemove(item: ShortcutInfo) {
        val wasBadged = badgeInfo.hasBadge()
        badgeInfo.subtractBadgeInfo(launcher.getBadgeInfoForItem(item))
        val isBadged = badgeInfo.hasBadge()
        updateBadgeScale(wasBadged, isBadged)
        invalidate()
        requestLayout()
    }

    override fun onTitleChanged(title: CharSequence) {
        name.text = title
        contentDescription = context.getString(R.string.folder_name_format, title)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Call the superclass onTouchEvent first, because sometimes it changes the state to
        // isPressed() on an ACTION_UP
        val result = super.onTouchEvent(event)

        // Check for a stylus button press, if it occurs cancel any long press checks.
        if (stylusEventHelper.onMotionEvent(event)) {
            longPressHelper.cancelLongPress()
            return true
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> longPressHelper.postCheckForLongPress()
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> longPressHelper.cancelLongPress()
            MotionEvent.ACTION_MOVE -> if (!Utilities.pointInView(this, event.x, event.y, slop)) {
                longPressHelper.cancelLongPress()
            }
        }
        return result
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        longPressHelper.cancelLongPress()
    }

    fun removeListeners() {
        info?.removeListener(this)
        info?.removeListener(folder)
    }

    fun clearLeaveBehindIfExists() {
        (layoutParams as CellLayout.LayoutParams).canReorder = true
        if (info?.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT.toLong()) {
            val cl = parent.parent as CellLayout
            cl.clearFolderLeaveBehind()
        }
    }

    fun drawLeaveBehindIfExists() {
        val lp = layoutParams as CellLayout.LayoutParams
        // While the folder is open, the position of the icon cannot change.
        lp.canReorder = false
        if (info!!.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT.toLong()) {
            val cl = parent.parent as CellLayout
            cl.setFolderLeaveBehindCell(lp.cellX, lp.cellY)
        }
    }

    fun onFolderClose(currentPage: Int) {
        previewItemManager.onFolderClose(currentPage)
    }

    fun getName() = name!!

    companion object {
        @Thunk
        var sStaticValuesDirty = true
        const val DROP_IN_ANIMATION_DURATION = 400L

        // Flag whether the folder should open itself when an item is dragged over is enabled.
        const val SPRING_LOADING_ENABLED = true

        // Delay when drag enters until the folder opens, in miliseconds.
        private const val ON_OPEN_DELAY = 800L
        private val BADGE_SCALE_PROPERTY: Property<FolderIcon, Float> = object : Property<FolderIcon, Float>(java.lang.Float.TYPE, "badgeScale") {
            override fun get(folderIcon: FolderIcon): Float {
                return folderIcon.badgeScale
            }

            override fun set(folderIcon: FolderIcon, value: Float) {
                folderIcon.badgeScale = value
                folderIcon.invalidate()
            }
        }

        @JvmStatic
        fun fromXml(resId: Int, launcher: Launcher, group: ViewGroup,
                    folderInfo: FolderInfo): FolderIcon {
            val error = PreviewItemManager.INITIAL_ITEM_ANIMATION_DURATION >= DROP_IN_ANIMATION_DURATION
            check(!error) {
                "DROP_IN_ANIMATION_DURATION must be greater than " +
                        "INITIAL_ITEM_ANIMATION_DURATION, as sequencing of adding first two items " +
                        "is dependent on this"
            }
            val grid = launcher.deviceProfile
            val icon = LayoutInflater.from(group.context)
                    .inflate(resId, group, false) as FolderIcon
            icon.clipToPadding = false
            icon.name.text = folderInfo.title
            icon.name.compoundDrawablePadding = 0
            val lp = icon.name.layoutParams as LayoutParams
            lp.topMargin = grid.iconSizePx + grid.iconDrawablePaddingPx
            icon.tag = folderInfo
            icon.setOnClickListener(ItemClickHandler.clickListener)
            icon.info = folderInfo
            icon.launcher = launcher
            icon.badgeRenderer = launcher.deviceProfile.mBadgeRenderer
            icon.contentDescription = launcher.getString(R.string.folder_name_format, folderInfo.title)
            val folder = Folder.fromXml(launcher)
            folder.dragController = launcher.dragController
            folder.folderIcon = icon
            folder.bind(folderInfo)
            icon.setFolder(folder)
            icon.accessibilityDelegate = launcher.accessibilityDelegate
            folderInfo.addListener(icon)
            icon.onFocusChangeListener = launcher.mFocusHandler
            return icon
        }
    }
}