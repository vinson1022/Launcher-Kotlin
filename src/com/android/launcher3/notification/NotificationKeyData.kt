/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.notification

import android.annotation.TargetApi
import android.service.notification.StatusBarNotification
import java.util.*
import kotlin.math.max

/**
 * The key data associated with the notification, used to determine what to include
 * in badges and dummy popup views before they are populated.
 *
 * @see NotificationInfo for the full data used when populating the dummy views.
 */
class NotificationKeyData
private constructor(
        val notificationKey: String,
        val shortcutId: String,
        count: Int
) {
    @JvmField
    var count: Int = max(1, count)

    override fun equals(obj: Any?): Boolean {
        return if (obj !is NotificationKeyData) {
            false
        } else obj.notificationKey == notificationKey
        // Only compare the keys.
    }

    override fun hashCode(): Int {
        var result = notificationKey.hashCode()
        result = 31 * result + shortcutId.hashCode()
        result = 31 * result + count
        return result
    }

    companion object {
        @JvmStatic
        @TargetApi(26)
        fun fromNotification(sbn: StatusBarNotification): NotificationKeyData {
            val notif = sbn.notification
            return NotificationKeyData(sbn.key, notif.shortcutId, notif.number)
        }

        @JvmStatic
        fun extractKeysOnly(notificationKeys: List<NotificationKeyData>): List<String> {
            return notificationKeys.map { it.notificationKey }
        }
    }
}