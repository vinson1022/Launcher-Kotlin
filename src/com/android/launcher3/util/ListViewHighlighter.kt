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
package com.android.launcher3.util

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator.REVERSE
import android.graphics.Color.WHITE
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.support.v4.graphics.ColorUtils
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.widget.AbsListView
import android.widget.ListView
import com.android.launcher3.R
import java.lang.Boolean.TRUE

/**
 * Utility class to scroll and highlight a list view item
 */
class ListViewHighlighter(
        private val listView: ListView,
        private var posHighlight: Int
) : AbsListView.OnScrollListener, AbsListView.RecyclerListener, OnLayoutChangeListener {

    private var colorAnimated = false

    init {
        listView.setOnScrollListener(this)
        listView.setRecyclerListener(this)
        listView.addOnLayoutChangeListener(this)
        listView.post { listView.tryHighlight() }
    }

    override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int,
                                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
        listView.post { listView.tryHighlight() }
    }

    private fun ListView.tryHighlight() {
        if (!highlightIfVisible(firstVisiblePosition..lastVisiblePosition)) {
            smoothScrollToPosition(posHighlight)
        }
    }

    override fun onScrollStateChanged(absListView: AbsListView, i: Int) {}

    override fun onScroll(view: AbsListView, firstVisibleItem: Int,
                          visibleItemCount: Int, totalItemCount: Int) {
        highlightIfVisible(firstVisibleItem..(firstVisibleItem + visibleItemCount))
    }

    private fun highlightIfVisible(range: IntRange): Boolean {
        if (posHighlight !in range) return false

        listView.getChildAt(posHighlight - range.first).highlightView()
        // finish highlight
        listView.setOnScrollListener(null)
        listView.removeOnLayoutChangeListener(this)
        posHighlight = -1
        return true
    }

    override fun onMovedToScrapHeap(view: View) {
        view.unhighlightView()
    }

    private fun View.highlightView() {
        if (TRUE == getTag(R.id.view_highlighted)) {
            // already highlighted
        } else {
            setTag(R.id.view_highlighted, true)
            setTag(R.id.view_unhighlight_background, background)
            background = highlightBackground
            postDelayed({ unhighlightView() }, 15000L)
        }
    }

    private fun View.unhighlightView() {
        if (TRUE == getTag(R.id.view_highlighted)) {
            val bg = getTag(R.id.view_unhighlight_background)
            if (bg is Drawable) {
                background = bg
            }
            setTag(R.id.view_unhighlight_background, null)
            setTag(R.id.view_highlighted, false)
        }
    }

    private val highlightBackground: ColorDrawable
        get() {
            val color = ColorUtils.setAlphaComponent(Themes.getColorAccent(listView.context), 26)
            if (colorAnimated) {
                return ColorDrawable(color)
            }
            colorAnimated = true
            val bg = ColorDrawable(WHITE)
            ObjectAnimator.ofInt(bg, "color", WHITE, color).apply {
                setEvaluator(ArgbEvaluator())
                duration = 200
                repeatMode = REVERSE
                repeatCount = 4
                start()
            }
            return bg
        }
}