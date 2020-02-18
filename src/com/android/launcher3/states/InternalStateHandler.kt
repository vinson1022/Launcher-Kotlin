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
package com.android.launcher3.states

import android.content.Intent
import android.os.Binder
import android.os.Bundle
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAppState.Companion.instanceNoCreate
import com.android.launcher3.MainThreadExecutor
import java.lang.ref.WeakReference

/**
 * Utility class to sending state handling logic to Launcher from within the same process.
 *
 * Extending [Binder] ensures that the platform maintains a single instance of each object
 * which allows this object to safely navigate the system process.
 */
abstract class InternalStateHandler : Binder() {
    /**
     * Initializes the handler when the launcher is ready.
     * @return true if the handler wants to stay alive.
     */
    protected abstract fun init(launcher: Launcher, alreadyOnHome: Boolean): Boolean

    fun addToIntent(intent: Intent): Intent {
        return intent.apply { putExtras(Bundle().apply { putBinder(EXTRA_STATE_HANDLER, this@InternalStateHandler) }) }
    }

    fun initWhenReady() {
        scheduler.schedule(this)
    }

    fun clearReference() = scheduler.clearReference(this)

    private class Scheduler : Runnable {
        private var pendingHandler = WeakReference<InternalStateHandler>(null)
        private val mainThreadExecutor: MainThreadExecutor by lazy { MainThreadExecutor() }
        @Synchronized
        fun schedule(handler: InternalStateHandler) {
            pendingHandler = WeakReference(handler)
            mainThreadExecutor.execute(this)
        }

        override fun run() {
            val app = instanceNoCreate ?: return
            val cb = app.model.callback as? Launcher ?: return
            initIfPending(cb, cb.isStarted)
        }

        @Synchronized
        fun initIfPending(launcher: Launcher, alreadyOnHome: Boolean) =
            pendingHandler.get()?.run {
                if (init(launcher, alreadyOnHome)) pendingHandler.clear()
                true
            } ?: false

        @Synchronized
        fun clearReference(handler: InternalStateHandler): Boolean {
            if (pendingHandler.get() === handler) {
                pendingHandler.clear()
                return true
            }
            return false
        }

        fun hasPending() = pendingHandler.get() != null
    }

    companion object {
        const val EXTRA_STATE_HANDLER = "launcher.state_handler"

        private val scheduler = Scheduler()
        @JvmStatic
        fun hasPending() = scheduler.hasPending()

        @JvmStatic
        fun handleCreate(launcher: Launcher, intent: Intent) = handleIntent(launcher, intent)

        @JvmStatic
        fun handleNewIntent(launcher: Launcher, intent: Intent, alreadyOnHome: Boolean)
            = handleIntent(launcher, intent, alreadyOnHome, true)

        private fun handleIntent(
                launcher: Launcher, intent: Intent, alreadyOnHome: Boolean = false, explicitIntent: Boolean = false): Boolean {
            var result = false
            intent.extras?.apply {
                val stateBinder = getBinder(EXTRA_STATE_HANDLER)
                if (stateBinder is InternalStateHandler) {
                    if (!stateBinder.init(launcher, alreadyOnHome)) {
                        remove(EXTRA_STATE_HANDLER)
                    }
                    result = true
                }
            }
            if (!result && !explicitIntent) {
                result = scheduler.initIfPending(launcher, alreadyOnHome)
            }
            return result
        }
    }
}