package com.android.launcher3.util

import android.os.Build

inline fun aboveApi23(action: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        action()
    }
}