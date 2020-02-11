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
package com.android.launcher3.qsb

import android.app.Activity.RESULT_OK
import android.app.SearchManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX
import android.content.Context
import android.content.Context.SEARCH_SERVICE
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentActivity
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.android.launcher3.AppWidgetResizeFrame
import com.android.launcher3.LauncherAppState.Companion.getIDP
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.config.FeatureFlags

/**
 * A frame layout which contains a QSB. This internally uses fragment to bind the view, which
 * allows it to contain the logic for [Fragment.startActivityForResult].
 *
 * Note: AppWidgetManagerCompat can be disabled using FeatureFlags. In QSB, we should use
 * AppWidgetManager directly, so that it keeps working in that case.
 */
class QsbContainerView : FrameLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(0, 0, 0, 0)
    }

    /**
     * A fragment to display the QSB.
     */
    class QsbFragment : Fragment(), OnClickListener {
        private val qsbWidgetHost: QsbWidgetHost by lazy { QsbWidgetHost(requireActivity()) }
        private lateinit var info: AppWidgetProviderInfo
        private var qsb: QsbWidgetHostView? = null
        // We need to store the orientation here, due to a bug (b/64916689) that results in widgets
        // being inflated in the wrong orientation.
        private var orientation = 0

        private val wrapper: FrameLayout by lazy { FrameLayout(requireActivity()) }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            orientation = requireContext().resources.configuration.orientation
        }

        override fun onCreateView(
                inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            // Only add the view when enabled
            if (isQsbEnabled) {
                wrapper.addView(createQsb(wrapper))
            }
            return wrapper
        }

        private fun createQsb(container: ViewGroup): View? {
            val activity = requireActivity()
            info = getSearchWidgetProvider(activity)?: run {
                // There is no search provider, just show the default widget.
                return QsbWidgetHostView.getDefaultView(container)
            }
            val widgetManager = AppWidgetManager.getInstance(activity)
            val opts = getOption(activity)
            val widgetId = getWidgetId()
            val widgetInfo = widgetManager.getAppWidgetInfo(widgetId)
            val isWidgetBound = widgetInfo != null && widgetInfo.provider == info.provider
            return if (isWidgetBound) {
                getQsbView(activity, widgetId, info, opts)
            } else {
                rebindWidget(activity, container, widgetId, info, opts)
            }
        }

        private fun getOption(activity: FragmentActivity): Bundle {
            val idp = getIDP(activity)
            val size = AppWidgetResizeFrame.getWidgetSizeRanges(activity, idp.numColumns, 1, null)
            return Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, size.left)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, size.top)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, size.right)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, size.bottom)
            }
        }

        private fun rebindWidget(context: Context, container: ViewGroup, oldWidgetId: Int, info: AppWidgetProviderInfo, opts: Bundle): View? {
            if (oldWidgetId > -1) {
                // widgetId is already bound and its not the correct provider. reset host.
                qsbWidgetHost.deleteHost()
            }
            var newWidgetId = qsbWidgetHost.allocateAppWidgetId()
            val success = AppWidgetManager.getInstance(context).bindAppWidgetIdIfAllowed(
                    newWidgetId, info.profile, info.provider, opts)
            if (!success) {
                qsbWidgetHost.deleteAppWidgetId(oldWidgetId)
                newWidgetId = -1
            }
            if (oldWidgetId != newWidgetId) {
                saveWidgetId(newWidgetId)
            }

            return if (success) {
                getQsbView(context, newWidgetId, info, opts)
            } else {
                // Return a default widget with setup icon.
                getDefaultView(container)
            }
        }

        private fun getQsbView(context: Context, widgetId: Int, info: AppWidgetProviderInfo, opts: Bundle): View? {
            return qsbWidgetHost.createQsbView(context, widgetId, info).apply {
                qsb = this
                id = R.id.qsb_widget
                if (!Utilities.containsAll(AppWidgetManager.getInstance(context)
                                .getAppWidgetOptions(widgetId), opts)) {
                    updateAppWidgetOptions(opts)
                }
                setPadding(0, 0, 0, 0)
                qsbWidgetHost.startListening()
            }
        }

        private fun getDefaultView(container: ViewGroup): View {
            return QsbWidgetHostView.getDefaultView(container).also {
                it.findViewById<View>(R.id.btn_qsb_setup).apply {
                    visibility = View.VISIBLE
                    setOnClickListener(this@QsbFragment)
                }
            }
        }

        private fun getWidgetId() = Utilities.getPrefs(activity).getInt(QSB_WIDGET_ID, -1)

        private fun saveWidgetId(widgetId: Int) {
            Utilities.getPrefs(activity).edit().putInt(QSB_WIDGET_ID, widgetId).apply()
        }

        override fun onClick(view: View) { // Start intent for bind the widget
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            // Allocate a new widget id for QSB
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, qsbWidgetHost.allocateAppWidgetId())
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            startActivityForResult(intent, REQUEST_BIND_QSB)
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode == REQUEST_BIND_QSB) {
                if (resultCode == RESULT_OK && data != null) {
                    saveWidgetId(data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
                    rebindFragment()
                } else {
                    qsbWidgetHost.deleteHost()
                }
            }
        }

        override fun onResume() {
            super.onResume()
            qsb.takeIf { qsb?.isReinflateRequired(orientation) == true }?.run { rebindFragment() }
        }

        override fun onDestroy() {
            qsbWidgetHost.stopListening()
            super.onDestroy()
        }

        private fun rebindFragment() {
            // Exit if the embedded qsb is disabled
            if (!isQsbEnabled) return

            if (activity != null) {
                wrapper.removeAllViews()
                wrapper.addView(createQsb(wrapper))
            }
        }

        private val isQsbEnabled: Boolean
            get() = FeatureFlags.QSB_ON_FIRST_SCREEN

        companion object {
            private const val REQUEST_BIND_QSB = 1
            private const val QSB_WIDGET_ID = "qsb_widget_id"
        }
    }

    private class QsbWidgetHost(context: Context) : AppWidgetHost(context, QSB_WIDGET_HOST_ID) {
        override fun onCreateView(
                context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo): AppWidgetHostView {
            return QsbWidgetHostView(context)
        }

        fun createQsbView(activity: Context, widgetId: Int, info: AppWidgetProviderInfo)
                = super.createView(activity, widgetId, info) as QsbWidgetHostView

        companion object {
            private const val QSB_WIDGET_HOST_ID = 1026
        }
    }

    companion object {
        /**
         * Returns a widget with category [AppWidgetProviderInfo.WIDGET_CATEGORY_SEARCHBOX]
         * provided by the same package which is set to be global search activity.
         * If widgetCategory is not supported, or no such widget is found, returns the first widget
         * provided by the package.
         */
        fun getSearchWidgetProvider(context: Context): AppWidgetProviderInfo? {
            val searchManager = context.getSystemService(SEARCH_SERVICE) as SearchManager
            val searchComponent = searchManager.globalSearchActivity ?: return null
            val providerPkg = searchComponent.packageName
            val appWidgetManager = AppWidgetManager.getInstance(context)
            return appWidgetManager.installedProviders
                    .filter { it.provider.packageName == providerPkg && it.configure == null }
                    .run {
                        find { it.widgetCategory and WIDGET_CATEGORY_SEARCHBOX != 0 }
                                ?: lastOrNull()
                    }
        }
    }
}