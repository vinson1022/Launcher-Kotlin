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
package com.android.launcher3.graphics

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Process
import android.os.UserHandle
import android.support.annotation.UiThread
import android.util.ArrayMap
import android.util.Log
import com.android.launcher3.FastBitmapDrawable
import com.android.launcher3.ItemInfoWithIcon
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.allapps.AllAppsBackgroundDrawable
import com.android.launcher3.graphics.PreloadIconDrawable.PATH_SIZE
import com.android.launcher3.util.aboveApi26

/**
 * Factory for creating new drawables.
 */
class DrawableFactory {
    private var preloadProgressPath: Path? = null
    private val myUser = Process.myUserHandle()
    private val userBadges = ArrayMap<UserHandle, Bitmap>()

    /**
     * Returns a FastBitmapDrawable with the icon.
     */
    fun newIcon(info: ItemInfoWithIcon) = FastBitmapDrawable(info).also { it.setIsDisabled(info.isDisabled) }

    fun newIcon(info: BitmapInfo?, target: ActivityInfo?) = FastBitmapDrawable(info)

    /**
     * Returns a FastBitmapDrawable with the icon.
     */
    fun newPendingIcon(info: ItemInfoWithIcon?, context: Context): PreloadIconDrawable {
        if (preloadProgressPath == null) {
            preloadProgressPath = getPreloadProgressPath(context)
        }
        return PreloadIconDrawable(info, preloadProgressPath, context)
    }

    private fun getPreloadProgressPath(context: Context): Path {
        aboveApi26 {
            try {
                // Try to load the path from Mask Icon
                val icon = context.getDrawable(R.drawable.adaptive_icon_drawable_wrapper)
                icon!!.setBounds(0, 0, PATH_SIZE, PATH_SIZE)
                return icon.javaClass.getMethod("getIconMask").invoke(icon) as Path
            } catch (e: Exception) {
                Log.e(TAG, "Error loading mask icon", e)
            }
        }

        // Create a circle static from top center and going clockwise.
        return Path().apply {
            moveTo(PATH_SIZE / 2f, 0f)
            addArc(0f, 0f, PATH_SIZE.toFloat(), PATH_SIZE.toFloat(), -90f, 360f)
        }
    }

    fun getAllAppsBackground(context: Context) = AllAppsBackgroundDrawable(context)

    /**
     * Returns a drawable that can be used as a badge for the user or null.
     */
    @UiThread
    fun getBadgeForUser(user: UserHandle, context: Context): Drawable? {
        if (myUser == user) return null

        return FastBitmapDrawable(getUserBadge(user, context)).apply {
            isFilterBitmap = true
            setBounds(0, 0, badgeBitmap.width, badgeBitmap.height)
        }
    }

    @Synchronized
    private fun getUserBadge(user: UserHandle, context: Context): Bitmap {
        var badgeBitmap = userBadges[user]
        if (badgeBitmap != null) {
            return badgeBitmap
        }
        val res = context.applicationContext.resources
        val badgeSize = res.getDimensionPixelSize(R.dimen.profile_badge_size)
        badgeBitmap = Bitmap.createBitmap(badgeSize, badgeSize, Bitmap.Config.ARGB_8888)
        val drawable = context.packageManager.getUserBadgedDrawableForDensity(
                BitmapDrawable(res, badgeBitmap), user, Rect(0, 0, badgeSize, badgeSize),
                0)
        if (drawable is BitmapDrawable) {
            badgeBitmap = drawable.bitmap
        } else {
            badgeBitmap.eraseColor(Color.TRANSPARENT)
            val c = Canvas(badgeBitmap)
            drawable.setBounds(0, 0, badgeSize, badgeSize)
            drawable.draw(c)
            c.setBitmap(null)
        }
        userBadges[user] = badgeBitmap
        return badgeBitmap
    }

    companion object {

        private const val TAG = "DrawableFactory"
        private var sInstance: DrawableFactory? = null

        @JvmStatic
        operator fun get(context: Context): DrawableFactory {
            return sInstance ?: synchronized(this) {
                Utilities.getOverrideObject(DrawableFactory::class.java,
                        context.applicationContext, R.string.drawable_factory_class).also { sInstance = it }
            }
        }
    }
}