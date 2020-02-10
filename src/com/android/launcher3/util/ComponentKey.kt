package com.android.launcher3.util

import android.content.ComponentName
import android.os.UserHandle

/**
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
open class ComponentKey(val componentName: ComponentName, val user: UserHandle) {
    private val mHashCode = arrayOf<Any>(componentName, user).contentHashCode()

    override fun hashCode() = mHashCode

    override fun equals(other: Any?) = (other as? ComponentKey)?.let {
        it.componentName == componentName && it.user == user
    } ?: false

    /**
     * Encodes a component key as a string of the form [flattenedComponentString#userId].
     */
    override fun toString() = "${componentName.flattenToString()}#$user"
}