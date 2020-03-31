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
package com.android.launcher3.shortcuts

import android.annotation.TargetApi
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.os.Build
import android.os.UserHandle
import com.android.launcher3.R

/**
 * Wrapper class for [android.content.pm.ShortcutInfo], representing deep shortcuts into apps.
 *
 * Not to be confused with [com.android.launcher3.ShortcutInfo].
 */
@TargetApi(Build.VERSION_CODES.N)
open class ShortcutInfoCompat(val shortcutInfo: ShortcutInfo) {

    @TargetApi(Build.VERSION_CODES.N)
    fun makeIntent(): Intent {
        return Intent(Intent.ACTION_MAIN)
                .addCategory(INTENT_CATEGORY)
                .setComponent(activity)
                .setPackage(packageName)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                .putExtra(EXTRA_SHORTCUT_ID, id)
    }

    val packageName: String
        get() = shortcutInfo.getPackage()

    fun getBadgePackage(context: Context): String? {
        val whitelistedPkg = context.getString(R.string.shortcutinfocompat_badgepkg_whitelist)
        return if (whitelistedPkg == packageName && shortcutInfo.extras!!.containsKey(EXTRA_BADGEPKG)) {
            shortcutInfo.extras!!.getString(EXTRA_BADGEPKG)
        } else packageName
    }

    open val id: String
        get() = shortcutInfo.id

    val shortLabel: CharSequence?
        get() = shortcutInfo.shortLabel

    val longLabel: CharSequence?
        get() = shortcutInfo.longLabel

    val lastChangedTimestamp: Long
        get() = shortcutInfo.lastChangedTimestamp

    val activity: ComponentName?
        get() = shortcutInfo.activity

    val userHandle: UserHandle
        get() = shortcutInfo.userHandle

    fun hasKeyFieldsOnly(): Boolean {
        return shortcutInfo.hasKeyFieldsOnly()
    }

    val isPinned: Boolean
        get() = shortcutInfo.isPinned

    open val isDeclaredInManifest: Boolean
        get() = shortcutInfo.isDeclaredInManifest

    val isEnabled: Boolean
        get() = shortcutInfo.isEnabled

    open val isDynamic: Boolean
        get() = shortcutInfo.isDynamic

    open val rank: Int
        get() = shortcutInfo.rank

    val disabledMessage: CharSequence?
        get() = shortcutInfo.disabledMessage

    override fun toString(): String {
        return shortcutInfo.toString()
    }

    companion object {
        private const val INTENT_CATEGORY = "com.android.launcher3.DEEP_SHORTCUT"
        private const val EXTRA_BADGEPKG = "badge_package"
        const val EXTRA_SHORTCUT_ID = "shortcut_id"
    }

}