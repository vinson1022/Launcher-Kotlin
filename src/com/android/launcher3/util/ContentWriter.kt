package com.android.launcher3.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.UserHandle
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherSettings.BaseLauncherColumns.ICON
import com.android.launcher3.LauncherSettings.Favorites.CONTENT_URI
import com.android.launcher3.Utilities
import com.android.launcher3.compat.UserManagerCompat

/**
 * Copyright (C) 2016 The Android Open Source Project
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
/**
 * A wrapper around [ContentValues] with some utility methods.
 */
class ContentWriter {
    private var commitParams: CommitParams? = null
    private var icon: Bitmap? = null
    private var user: UserHandle? = null
    private val values: ContentValues
    private val context: Context?

    constructor(context: Context, commitParams: CommitParams) : this(context) {
        this.commitParams = commitParams
    }

    constructor(context: Context?, values: ContentValues) {
        this.context = context
        this.values = values
    }

    constructor(context: Context) {
        this.context = context
        this.values = ContentValues()
    }

    fun put(key: String, value: Int) = apply { values.put(key, value) }
    fun put(key: String, value: Long) = apply { values.put(key, value) }
    fun put(key: String, value: String) = apply { values.put(key, value) }
    fun put(key: String, value: CharSequence) = apply { values.put(key, value.toString()) }
    fun put(key: String, value: Intent) = apply { values.put(key, value.toUri(0)) }
    fun put(key: String, user: UserHandle) = apply { put(key, UserManagerCompat.getInstance(context).getSerialNumberForUser(user)) }

    fun putIcon(value: Bitmap, userHandle: UserHandle) = apply {
        icon = value
        user = userHandle
    }

    /**
     * Commits any pending validation and returns the final values.
     * Must not be called on UI thread.
     */
    fun getValues(context: Context): ContentValues {
        Preconditions.assertNonUiThread()
        if (icon != null && !LauncherAppState.getInstance(context).iconCache
                        .isDefaultIcon(icon, user)) {
            values.put(ICON, Utilities.flattenBitmap(icon))
            icon = null
        }
        return values
    }

    fun commit() = commitParams?.run {
            context?.run { contentResolver.update(uri, getValues(context), where, selectionArgs) }?: 0
        }?: 0

    class CommitParams(var where: String, var selectionArgs: Array<String>) {
        val uri: Uri = CONTENT_URI
    }

}