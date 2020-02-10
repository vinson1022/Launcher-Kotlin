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
@file:JvmName("UiThreadHelper")

package com.android.launcher3.util

import android.content.Context
import android.content.Context.INPUT_METHOD_SERVICE
import android.os.*
import android.os.Process.THREAD_PRIORITY_FOREGROUND
import android.view.inputmethod.InputMethodManager

/**
 * Utility class for offloading some class from UI thread
 */
private val handlerThread: HandlerThread by lazy {
    HandlerThread("UiThreadHelper", THREAD_PRIORITY_FOREGROUND).also { it.start() }
}
private var handler: Handler? = null
private const val MSG_HIDE_KEYBOARD = 1

val backgroundLooper: Looper = handlerThread.looper

private fun getHandler(context: Context): Handler? {
    if (handler == null) {
        handler = Handler(backgroundLooper,
                UiCallbacks(context.applicationContext))
    }
    return handler
}

fun hideKeyboardAsync(context: Context, token: IBinder) {
    Message.obtain(getHandler(context), MSG_HIDE_KEYBOARD, token).sendToTarget()
}

private class UiCallbacks internal constructor(context: Context) : Handler.Callback {
    private val imm = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
    override fun handleMessage(message: Message): Boolean {
        when (message.what) {
            MSG_HIDE_KEYBOARD -> {
                imm.hideSoftInputFromWindow(message.obj as IBinder, 0)
                return true
            }
        }
        return false
    }

}