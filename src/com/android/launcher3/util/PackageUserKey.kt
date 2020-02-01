package com.android.launcher3.util

import android.os.UserHandle
import android.service.notification.StatusBarNotification
import com.android.launcher3.ItemInfo
import com.android.launcher3.shortcuts.DeepShortcutManager

/** Creates a hash key based on package name and user.  */
class PackageUserKey(packageName: String?, user: UserHandle?) {
    @JvmField
    var packageName: String? = null
    @JvmField
    var userHandle: UserHandle? = null
    private var hashCode = 0

    init {
        update(packageName, user)
    }

    private fun update(name: String?, user: UserHandle?) {
        packageName = name
        userHandle = user
        hashCode = arrayOf(name, user).contentHashCode()
    }

    /**
     * This should only be called to avoid new object creations in a loop.
     * @return Whether this PackageUserKey was successfully updated - it shouldn't be used if not.
     */
    fun updateFromItemInfo(info: ItemInfo): Boolean {
        if (DeepShortcutManager.supportsShortcuts(info)) {
            update(info.targetComponent.packageName, info.user)
            return true
        }
        return false
    }

    override fun hashCode(): Int {
        return hashCode
    }

    override fun equals(other: Any?): Boolean {
        if (other !is PackageUserKey) return false
        return packageName == other.packageName && userHandle == other.userHandle
    }

    companion object {
        @JvmStatic
        fun fromItemInfo(info: ItemInfo)
                = PackageUserKey(info.targetComponent.packageName, info.user)

        @JvmStatic
        fun fromNotification(notification: StatusBarNotification)
                = PackageUserKey(notification.packageName, notification.user)
    }
}