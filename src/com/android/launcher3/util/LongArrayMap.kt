/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.util.LongSparseArray

/**
 * Extension of [LongSparseArray] with some utility methods.
 */
class LongArrayMap<E> : LongSparseArray<E>(), Iterable<E> {

    fun containsKey(key: Long) = indexOfKey(key) >= 0

    val isEmpty: Boolean
        get() = size() <= 0

    override fun clone(): LongArrayMap<E> {
        return super.clone() as LongArrayMap<E>
    }

    override fun iterator(): MutableIterator<E> {
        return ValueIterator() as MutableIterator<E>
    }

    @Thunk
    internal inner class ValueIterator : MutableIterator<E?> {
        private var nextIndex = 0

        override fun hasNext() = nextIndex < size()

        override fun next(): E? = valueAt(nextIndex++)

        override fun remove() {
            throw UnsupportedOperationException()
        }
    }
}