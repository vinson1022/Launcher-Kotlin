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
package com.android.launcher3.widget

import android.os.Process
import com.android.launcher3.model.WidgetItem
import java.text.Collator
import java.util.*

/**
 * Comparator for sorting WidgetItem based on their user, title and size.
 */
class WidgetItemComparator : Comparator<WidgetItem> {

    private val myUserHandle = Process.myUserHandle()
    private val collator = Collator.getInstance()

    override fun compare(a: WidgetItem, b: WidgetItem): Int {
        // Independent of how the labels compare, if only one of the two widget info belongs to
        // work profile, put that one in the back.
        val thisWorkProfile = myUserHandle != a.user
        val otherWorkProfile = myUserHandle != b.user
        if (thisWorkProfile xor otherWorkProfile) {
            return if (thisWorkProfile) 1 else -1
        }
        val labelCompare = collator.compare(a.label, b.label)
        if (labelCompare != 0) {
            return labelCompare
        }

        // If the label is same, put the smaller widget before the larger widget. If the area is
        // also same, put the widget with smaller height before.
        val thisArea = a.spanX * a.spanY
        val otherArea = b.spanX * b.spanY
        return if (thisArea == otherArea) a.spanY.compareTo(b.spanY) else thisArea.compareTo(otherArea)
    }
}