package com.android.launcher3.widget

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import com.android.launcher3.AppWidgetResizeFrame
import com.android.launcher3.DropTarget.DragObject
import com.android.launcher3.Launcher
import com.android.launcher3.compat.AppWidgetManagerCompat
import com.android.launcher3.dragndrop.DragController.DragListener
import com.android.launcher3.dragndrop.DragOptions
import com.android.launcher3.util.Thunk
import com.android.launcher3.views.BaseDragLayerLayoutParams
import java.lang.Runnable

class WidgetHostViewLoader(
        // TODO: technically, this class should not have to know the existence of the launcher.
        @Thunk var launcher: Launcher,
        @Thunk val view: View
) : DragListener {

    /* Runnables to handle inflation and binding. */
    @Thunk
    var inflateWidgetRunnable: Runnable? = null
    private var bindWidgetRunnable: Runnable? = null

    @Thunk
    var handler = Handler()

    @Thunk
    val info = view.tag as PendingAddWidgetInfo

    // Widget id generated for binding a widget host view or -1 for invalid id. The id is
    // not is use as long as it is stored here and can be deleted safely. Once its used, this value
    // to be set back to -1.
    @Thunk
    var widgetLoadingId = -1

    override fun onDragStart(dragObject: DragObject, options: DragOptions) {
        preloadWidget()
    }

    override fun onDragEnd() {
        if (LOGD) {
            Log.d(TAG, "Cleaning up in onDragEnd()...")
        }

        // Cleanup up preloading state.
        launcher.dragController.removeDragListener(this)
        handler.removeCallbacks(bindWidgetRunnable)
        handler.removeCallbacks(inflateWidgetRunnable)

        // Cleanup widget id
        if (widgetLoadingId != -1) {
            launcher.appWidgetHost.deleteAppWidgetId(widgetLoadingId)
            widgetLoadingId = -1
        }

        // The widget was inflated and added to the DragLayer -- remove it.
        info.boundWidget?.apply {
            if (LOGD) {
                Log.d(TAG, "...removing widget from drag layer")
            }
            launcher.dragLayer.removeView(this)
            launcher.appWidgetHost.deleteAppWidgetId(appWidgetId)
            info.boundWidget = null
        }
    }

    /**
     * Start preloading the widget.
     */
    private fun preloadWidget(): Boolean {
        val pInfo = info.info
        if (pInfo.isCustomWidget) return false

        val options = getDefaultOptionsForWidget(launcher, info)

        // If there is a configuration activity, do not follow thru bound and inflate.
        if (info.handler!!.needsConfigure()) {
            info.bindOptions = options
            return false
        }
        bindWidgetRunnable = Runnable {
            widgetLoadingId = launcher.appWidgetHost.allocateAppWidgetId()
            if (LOGD) {
                Log.d(TAG, "Binding widget, id: $widgetLoadingId")
            }
            if (AppWidgetManagerCompat.getInstance(launcher).bindAppWidgetIdIfAllowed(
                            widgetLoadingId, pInfo, options)) {

                // Widget id bound. Inflate the widget.
                handler.post(inflateWidgetRunnable)
            }
        }
        inflateWidgetRunnable = Runnable {
            if (LOGD) {
                Log.d(TAG, "Inflating widget, id: $widgetLoadingId")
            }
            if (widgetLoadingId == -1) return@Runnable

            val hostView = launcher.appWidgetHost.createView(
                    launcher, widgetLoadingId, pInfo)
            info.boundWidget = hostView

            // We used up the widget Id in binding the above view.
            widgetLoadingId = -1
            hostView.visibility = View.INVISIBLE
            val unScaledSize = launcher.workspace.estimateItemSize(info)
            // We want the first widget layout to be the correct size. This will be important
            // for width size reporting to the AppWidgetManager.
            val lp = BaseDragLayerLayoutParams(unScaledSize[0],
                    unScaledSize[1])
            lp.y = 0
            lp.x = lp.y
            lp.customPosition = true
            hostView.layoutParams = lp
            if (LOGD) {
                Log.d(TAG, "Adding host view to drag layer")
            }
            launcher.dragLayer.addView(hostView)
            view.tag = info
        }
        if (LOGD) {
            Log.d(TAG, "About to bind/inflate widget")
        }
        handler.post(bindWidgetRunnable)
        return true
    }

    companion object {
        private const val TAG = "WidgetHostViewLoader"
        private const val LOGD = false

        @JvmStatic
        fun getDefaultOptionsForWidget(context: Context, info: PendingAddWidgetInfo): Bundle {
            val rect = Rect()
            AppWidgetResizeFrame.getWidgetSizeRanges(context, info.spanX, info.spanY, rect)
            val padding = AppWidgetHostView.getDefaultPaddingForWidget(context,
                    info.componentName, null)
            val density = context.resources.displayMetrics.density
            val xPaddingDips = ((padding.left + padding.right) / density).toInt()
            val yPaddingDips = ((padding.top + padding.bottom) / density).toInt()
            val options = Bundle()
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,
                    rect.left - xPaddingDips)
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,
                    rect.top - yPaddingDips)
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,
                    rect.right - xPaddingDips)
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,
                    rect.bottom - yPaddingDips)
            return options
        }
    }

}