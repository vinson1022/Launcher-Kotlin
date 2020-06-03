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
import android.content.Intent
import android.content.Intent.ShortcutIconResource
import android.graphics.*
import android.graphics.drawable.*
import android.os.Build
import android.os.Process
import android.os.UserHandle
import com.android.launcher3.*
import com.android.launcher3.LauncherAppState.Companion.getIDP
import com.android.launcher3.graphics.BitmapInfo.Companion.fromBitmap
import com.android.launcher3.graphics.BitmapRenderer.createHardwareBitmap
import com.android.launcher3.graphics.ShadowGenerator.BLUR_FACTOR
import com.android.launcher3.model.PackageItemInfo
import com.android.launcher3.shortcuts.DeepShortcutManager
import com.android.launcher3.shortcuts.ShortcutInfoCompat
import com.android.launcher3.util.Provider
import com.android.launcher3.util.getColorAccent
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Helper methods for generating various launcher icons
 */
class LauncherIcons private constructor(context: Context) : AutoCloseable {
    /**
     * Recycles a LauncherIcons that may be in-use.
     */
    fun recycle() {
        synchronized(sPoolSync) {

            // Clear any temporary state variables
            wrapperBackgroundColor = DEFAULT_WRAPPER_BACKGROUND
            next = sPool
            sPool = this
        }
    }

    override fun close() {
        recycle()
    }

    private val oldBounds = Rect()
    private val mContext = context.applicationContext
    private val canvas = Canvas().apply {
        drawFilter = PaintFlagsDrawFilter(Paint.DITHER_FLAG, Paint.FILTER_BITMAP_FLAG)
    }
    private val packageManager = mContext.packageManager
    private val fillResIconDpi = getIDP(mContext).fillResIconDpi
    private val iconBitmapSize = getIDP(mContext).iconBitmapSize
    private var wrapperIcon: Drawable? = null
    private var wrapperBackgroundColor = DEFAULT_WRAPPER_BACKGROUND

    // sometimes we store linked lists of these things
    private var next: LauncherIcons? = null
    private val shadowGenerator: ShadowGenerator by lazy { ShadowGenerator(mContext) }

    val normalizer by lazy { IconNormalizer(mContext) }

    /**
     * Returns a bitmap suitable for the all apps view. If the package or the resource do not
     * exist, it returns null.
     */
    fun createIconBitmap(iconRes: ShortcutIconResource): BitmapInfo? {
        try {
            val resources = packageManager.getResourcesForApplication(iconRes.packageName)
            val id = resources.getIdentifier(iconRes.resourceName, null, null)
            // do not stamp old legacy shortcuts as the app may have already forgotten about it
            return createBadgedIconBitmap(
                    resources.getDrawableForDensity(id, fillResIconDpi),
                    Process.myUserHandle() /* only available on primary user */,
                    0 /* do not apply legacy treatment */)
        } catch (e: Exception) {
            // Icon not found.
        }
        return null
    }

    /**
     * Returns a bitmap which is of the appropriate size to be displayed as an icon
     */
    fun createIconBitmap(icon: Bitmap): BitmapInfo {
        return if (iconBitmapSize == icon.width && iconBitmapSize == icon.height) {
            fromBitmap(icon)
        } else fromBitmap(
                createIconBitmap(BitmapDrawable(mContext.resources, icon), 1f))
    }
    /**
     * Returns a bitmap suitable for displaying as an icon at various launcher UIs like all apps
     * view or workspace. The icon is badged for {@param user}.
     * The bitmap is also visually normalized with other icons.
     */
    /**
     * Returns a bitmap suitable for displaying as an icon at various launcher UIs like all apps
     * view or workspace. The icon is badged for {@param user}.
     * The bitmap is also visually normalized with other icons.
     */
    @JvmOverloads
    fun createBadgedIconBitmap(icon: Drawable?, user: UserHandle?, iconAppTargetSdk: Int,
                               isInstantApp: Boolean = false): BitmapInfo {
        var icon = icon
        val scale = FloatArray(1)
        icon = normalizeAndWrapToAdaptiveIcon(icon, iconAppTargetSdk, null, scale)
        val bitmap = createIconBitmap(icon, scale[0])
        if (Utilities.ATLEAST_OREO && icon is AdaptiveIconDrawable) {
            canvas.setBitmap(bitmap)
            shadowGenerator.recreateIcon(Bitmap.createBitmap(bitmap), canvas)
            canvas.setBitmap(null)
        }
        val result: Bitmap
        result = if (user != null && Process.myUserHandle() != user) {
            val drawable: BitmapDrawable = FixedSizeBitmapDrawable(bitmap)
            val badged = packageManager.getUserBadgedIcon(drawable, user)
            if (badged is BitmapDrawable) {
                badged.bitmap
            } else {
                createIconBitmap(badged, 1f)
            }
        } else if (isInstantApp) {
            badgeWithDrawable(bitmap, mContext.getDrawable(R.drawable.ic_instant_app_badge)!!)
            bitmap
        } else {
            bitmap
        }
        return fromBitmap(result)
    }

    /**
     * Creates a normalized bitmap suitable for the all apps view. The bitmap is also visually
     * normalized with other icons and has enough spacing to add shadow.
     */
    fun createScaledBitmapWithoutShadow(icon: Drawable?, iconAppTargetSdk: Int): Bitmap {
        var icon = icon
        val iconBounds = RectF()
        val scale = FloatArray(1)
        icon = normalizeAndWrapToAdaptiveIcon(icon, iconAppTargetSdk, iconBounds, scale)
        return createIconBitmap(icon,
                min(scale[0], ShadowGenerator.getScaleForBounds(iconBounds)))
    }

    /**
     * Sets the background color used for wrapped adaptive icon
     */
    fun setWrapperBackgroundColor(color: Int) {
        wrapperBackgroundColor = if (Color.alpha(color) < 255) DEFAULT_WRAPPER_BACKGROUND else color
    }

    private fun normalizeAndWrapToAdaptiveIcon(icon: Drawable?, iconAppTargetSdk: Int,
                                               outIconBounds: RectF?, outScale: FloatArray): Drawable? {
        var icon = icon
        var scale = 1f
        if (Utilities.ATLEAST_OREO && iconAppTargetSdk >= Build.VERSION_CODES.O) {
            val outShape = BooleanArray(1)
            if (wrapperIcon == null) {
                wrapperIcon = mContext.getDrawable(R.drawable.adaptive_icon_drawable_wrapper)!!
            }
            val dr = wrapperIcon as AdaptiveIconDrawable
            dr.setBounds(0, 0, 1, 1)
            scale = normalizer.getScale(icon!!, outIconBounds, dr.iconMask, outShape)
            if (!outShape[0] && icon !is AdaptiveIconDrawable) {
                val fsd = dr.foreground as FixedScaleDrawable
                fsd.drawable = icon
                fsd.setScale(scale)
                icon = dr
                scale = normalizer.getScale(icon, outIconBounds, null, null)
                (dr.background as ColorDrawable).color = wrapperBackgroundColor
            }
        } else {
            scale = normalizer.getScale(icon!!, outIconBounds, null, null)
        }
        outScale[0] = scale
        return icon
    }

    /**
     * Adds the {@param badge} on top of {@param target} using the badge dimensions.
     */
    private fun badgeWithDrawable(target: Bitmap?, badge: Drawable) {
        canvas.setBitmap(target)
        badgeWithDrawable(canvas, badge)
        canvas.setBitmap(null)
    }

    /**
     * Adds the {@param badge} on top of {@param target} using the badge dimensions.
     */
    private fun badgeWithDrawable(target: Canvas, badge: Drawable) {
        val badgeSize = mContext.resources.getDimensionPixelSize(R.dimen.profile_badge_size)
        badge.setBounds(iconBitmapSize - badgeSize, iconBitmapSize - badgeSize,
                iconBitmapSize, iconBitmapSize)
        badge.draw(target)
    }

    /**
     * @param scale the scale to apply before drawing {@param icon} on the canvas
     */
    private fun createIconBitmap(icon: Drawable?, scale: Float): Bitmap {
        var width = iconBitmapSize
        var height = iconBitmapSize
        if (icon is PaintDrawable) {
            icon.intrinsicWidth = width
            icon.intrinsicHeight = height
        } else if (icon is BitmapDrawable) {
            // Ensure the bitmap has a density.
            val bitmap = icon.bitmap
            if (bitmap != null && bitmap.density == Bitmap.DENSITY_NONE) {
                icon.setTargetDensity(mContext.resources.displayMetrics)
            }
        }
        val sourceWidth = icon!!.intrinsicWidth
        val sourceHeight = icon.intrinsicHeight
        if (sourceWidth > 0 && sourceHeight > 0) {
            // Scale the icon proportionally to the icon dimensions
            val ratio = sourceWidth.toFloat() / sourceHeight
            if (sourceWidth > sourceHeight) {
                height = (width / ratio).toInt()
            } else if (sourceHeight > sourceWidth) {
                width = (height * ratio).toInt()
            }
        }
        // no intrinsic size --> use default size
        val textureWidth = iconBitmapSize
        val textureHeight = iconBitmapSize
        val bitmap = Bitmap.createBitmap(textureWidth, textureHeight,
                Bitmap.Config.ARGB_8888)
        canvas.setBitmap(bitmap)
        val left = (textureWidth - width) / 2
        val top = (textureHeight - height) / 2
        oldBounds.set(icon.bounds)
        if (Utilities.ATLEAST_OREO && icon is AdaptiveIconDrawable) {
            val offset = max(ceil(BLUR_FACTOR * textureWidth.toDouble()).toInt(), max(left, top))
            val size = max(width, height)
            icon.setBounds(offset, offset, offset + size, offset + size)
        } else {
            icon.setBounds(left, top, left + width, top + height)
        }
        canvas.save()
        canvas.scale(scale, scale, textureWidth / 2.toFloat(), textureHeight / 2.toFloat())
        icon.draw(canvas)
        canvas.restore()
        icon.bounds = oldBounds
        canvas.setBitmap(null)
        return bitmap
    }

    @JvmOverloads
    fun createShortcutIcon(shortcutInfo: ShortcutInfoCompat,
                           badged: Boolean = true, fallbackIconProvider: Provider<Bitmap?>? = null): BitmapInfo {
        val unbadgedDrawable = DeepShortcutManager.getInstance(mContext)
                .getShortcutIconDrawable(shortcutInfo, fillResIconDpi)
        val cache = LauncherAppState.getInstance(mContext).iconCache
        val unbadgedBitmap: Bitmap?
        unbadgedBitmap = if (unbadgedDrawable != null) {
            createScaledBitmapWithoutShadow(unbadgedDrawable, 0)
        } else {
            if (fallbackIconProvider != null) {
                // Fallback icons are already badged and with appropriate shadow
                val fullIcon = fallbackIconProvider.get()
                if (fullIcon != null) {
                    return createIconBitmap(fullIcon)
                }
            }
            cache.getDefaultIcon(Process.myUserHandle()).icon
        }
        val result = BitmapInfo()
        if (!badged) {
            result.color = getColorAccent(mContext)
            result.icon = unbadgedBitmap
            return result
        }
        val badge = getShortcutInfoBadge(shortcutInfo, cache)
        result.color = badge.iconColor

        result.icon = createHardwareBitmap(iconBitmapSize, iconBitmapSize, object : BitmapRenderer.Renderer {
            override fun draw(out: Canvas) {
                shadowGenerator.recreateIcon(unbadgedBitmap, out)
                badgeWithDrawable(out, FastBitmapDrawable(badge))
            }
        })
        return result
    }

    fun getShortcutInfoBadge(shortcutInfo: ShortcutInfoCompat, cache: IconCache): ItemInfoWithIcon {
        val cn = shortcutInfo.activity
        val badgePkg = shortcutInfo.getBadgePackage(mContext)
        val hasBadgePkgSet = badgePkg != shortcutInfo.packageName
        return if (cn != null && !hasBadgePkgSet) {
            // Get the app info for the source activity.
            val appInfo = AppInfo()
            appInfo.user = shortcutInfo.userHandle
            appInfo.componentName = cn
            appInfo.intent = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(cn)
            cache.getTitleAndIcon(appInfo, false)
            appInfo
        } else {
            val pkgInfo = PackageItemInfo(badgePkg)
            cache.getTitleAndIconForApp(pkgInfo, false)
            pkgInfo
        }
    }

    /**
     * An extension of [BitmapDrawable] which returns the bitmap pixel size as intrinsic size.
     * This allows the badging to be done based on the action bitmap size rather than
     * the scaled bitmap size.
     */
    private class FixedSizeBitmapDrawable(bitmap: Bitmap?) : BitmapDrawable(null, bitmap) {
        override fun getIntrinsicHeight(): Int {
            return bitmap.width
        }

        override fun getIntrinsicWidth(): Int {
            return bitmap.width
        }
    }

    companion object {
        private const val DEFAULT_WRAPPER_BACKGROUND = Color.WHITE
        val sPoolSync = Any()
        private var sPool: LauncherIcons? = null

        /**
         * Return a new Message instance from the global pool. Allows us to
         * avoid allocating new objects in many cases.
         */
        @JvmStatic
        fun obtain(context: Context): LauncherIcons {
            synchronized(sPoolSync) {
                if (sPool != null) {
                    val m = sPool
                    sPool = m!!.next
                    m.next = null
                    return m
                }
            }
            return LauncherIcons(context)
        }
    }
}