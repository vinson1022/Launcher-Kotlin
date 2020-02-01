/*
 * Copyright (C) 2018 The Android Open Source Project
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

import android.animation.AnimatorSet
import android.annotation.TargetApi
import android.os.Build
import java.util.*
import java.util.function.Consumer

/**
 * Utility class to keep track of a running animation.
 *
 * This class allows attaching end callbacks to an animation is intended to be used with
 * [com.android.launcher3.anim.AnimatorPlaybackController], since in that case
 * AnimationListeners are not properly dispatched.
 */
@TargetApi(Build.VERSION_CODES.O)
class PendingAnimation(val anim: AnimatorSet) {
    private val endListeners = ArrayList<Consumer<OnEndListener>>()

    fun finish(isSuccess: Boolean, logAction: Int) {
        endListeners.forEach { it.accept(OnEndListener(isSuccess, logAction)) }
        endListeners.clear()
    }

    fun addEndListener(listener: Consumer<OnEndListener>) {
        endListeners.add(listener)
    }

    class OnEndListener(var isSuccess: Boolean, var logAction: Int)
}