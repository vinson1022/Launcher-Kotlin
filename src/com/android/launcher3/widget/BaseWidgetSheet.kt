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
package com.android.launcher3.widget

import android.content.Context
import android.graphics.Point
import android.util.AttributeSet
import android.view.View
import android.view.View.OnLongClickListener
import android.widget.Toast
import com.android.launcher3.DragSource
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.ItemInfo
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.graphics.ColorScrim
import com.android.launcher3.logging.LoggerUtils
import com.android.launcher3.touch.ItemLongClickListener
import com.android.launcher3.userevent.nano.LauncherLogProto
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType.WIDGETS
import com.android.launcher3.util.SystemUiController.Companion.FLAG_DARK_NAV
import com.android.launcher3.util.SystemUiController.Companion.FLAG_LIGHT_NAV
import com.android.launcher3.util.SystemUiController.Companion.UI_STATE_WIDGET_BOTTOM_SHEET
import com.android.launcher3.util.aboveApi23
import com.android.launcher3.util.getAttrBoolean
import com.android.launcher3.views.AbstractSlideInView

/**
 * Base class for various widgets popup
 */
internal abstract class BaseWidgetSheet(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : AbstractSlideInView(context, attrs, defStyleAttr), View.OnClickListener, OnLongClickListener, DragSource {
    /* Touch handling related member variables. */
    private var widgetInstructionToast = Toast.makeText(context, Utilities.wrapForTts(
            context.getText(R.string.long_press_widget_to_add),
            context.getString(R.string.long_accessible_way_to_add)), Toast.LENGTH_SHORT)
    private val colorScrim = ColorScrim.createExtractedColorScrim(this)

    override fun onClick(v: View) {
        // Let the user know that they have to long press to add a widget
        widgetInstructionToast.cancel()
        widgetInstructionToast.show()
    }

    override fun onLongClick(v: View): Boolean {
        if (!ItemLongClickListener.canStartDrag(launcher)) return false
        return if (v is WidgetCell) {
            beginDraggingWidget(v)
        } else true
    }

    override fun setTranslationShift(translationShift: Float) {
        super.setTranslationShift(translationShift)
        colorScrim.setProgress(1 - translationShift)
    }

    private fun beginDraggingWidget(v: WidgetCell): Boolean {
        // Get the widget preview as the drag representation
        val image = v.getWidgetPreview()
        // If the ImageView doesn't have a drawable yet, the widget preview hasn't been loaded and
        // we abort the drag.
        if (image.bitmap == null) return false

        val loc = IntArray(2)
        launcher.dragLayer.getLocationInDragLayer(image, loc)
        PendingItemDragHelper(v).startDrag(
                image.bitmapBounds, image.bitmap.width, image.width,
                Point(loc[0], loc[1]), this, DragOptions())
        close(true)
        return true
    }

    //
    // Drag related handling methods that implement {@link DragSource} interface.
    //
    override fun onDropCompleted(target: View, d: DragObject, success: Boolean) {}

    override fun onCloseComplete() {
        super.onCloseComplete()
        clearNavBarColor()
    }

    protected fun clearNavBarColor() {
        aboveApi23 {
            launcher.systemUiController.updateUiState(UI_STATE_WIDGET_BOTTOM_SHEET, 0)
        }
    }

    protected fun setupNavBarColor() {
        aboveApi23 {
            val isSheetDark = getAttrBoolean(launcher, R.attr.isMainColorDark)
            launcher.systemUiController.updateUiState(
                    UI_STATE_WIDGET_BOTTOM_SHEET,
                    if (isSheetDark) FLAG_DARK_NAV else FLAG_LIGHT_NAV)
        }
    }

    override fun fillInLogContainerData(v: View, info: ItemInfo, target: LauncherLogProto.Target, targetParent: LauncherLogProto.Target) {
        targetParent.containerType = WIDGETS
        targetParent.cardinality = elementsRowCount
    }

    override fun logActionCommand(command: Int) {
        val target = LoggerUtils.newContainerTarget(WIDGETS)
        target.cardinality = elementsRowCount
        launcher.userEventDispatcher.logActionCommand(command, target)
    }

    protected abstract val elementsRowCount: Int
}