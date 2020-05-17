package com.android.launcher3.util

import android.os.Build

inline fun aboveApi23(action: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        action()
    }
}

inline fun aboveApi25(action: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
        action()
    }
}

inline fun aboveApi26(action: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        action()
    }
}

inline fun aboveApi28(action: () -> Unit) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        action()
    }
}