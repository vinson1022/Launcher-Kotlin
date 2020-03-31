package com.android.launcher3.shortcuts

import android.content.ComponentName
import android.content.Intent
import android.os.UserHandle
import com.android.launcher3.ItemInfo
import com.android.launcher3.util.ComponentKey

/**
 * A key that uniquely identifies a shortcut using its package, id, and user handle.
 */
class ShortcutKey : ComponentKey {
    constructor(packageName: String, user: UserHandle, id: String) : super(ComponentName(packageName, id), user) {
        // Use the id as the class name.
    }

    constructor(componentName: ComponentName, user: UserHandle) : super(componentName, user)

    val id: String
        get() = componentName.className

    companion object {
        @JvmStatic
        fun fromInfo(shortcutInfo: ShortcutInfoCompat): ShortcutKey {
            return ShortcutKey(shortcutInfo.packageName, shortcutInfo.userHandle,
                    shortcutInfo.id)
        }

        @JvmStatic
        fun fromIntent(intent: Intent, user: UserHandle): ShortcutKey {
            val shortcutId = intent.getStringExtra(
                    ShortcutInfoCompat.EXTRA_SHORTCUT_ID)?: ""
            return ShortcutKey(intent.getPackage() ?: "", user, shortcutId)
        }

        @JvmStatic
        fun fromItemInfo(info: ItemInfo): ShortcutKey {
            return fromIntent(info.intent, info.user)
        }
    }
}