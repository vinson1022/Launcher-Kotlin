package com.android.launcher3.graphics

import android.annotation.TargetApi
import android.content.res.Resources
import android.content.res.Resources.Theme
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.DrawableWrapper
import android.os.Build
import android.util.AttributeSet
import org.xmlpull.v1.XmlPullParser

/**
 * Extension of [DrawableWrapper] which scales the child drawables by a fixed amount.
 */
@TargetApi(Build.VERSION_CODES.N)
class FixedScaleDrawable : DrawableWrapper(ColorDrawable()) {
    private var scaleX = LEGACY_ICON_SCALE
    private var scaleY = LEGACY_ICON_SCALE

    override fun draw(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.scale(scaleX, scaleY,
                bounds.exactCenterX(), bounds.exactCenterY())
        super.draw(canvas)
        canvas.restoreToCount(saveCount)
    }

    override fun inflate(r: Resources, parser: XmlPullParser, attrs: AttributeSet) = Unit

    override fun inflate(r: Resources, parser: XmlPullParser, attrs: AttributeSet, theme: Theme?) = Unit

    fun setScale(scale: Float) {
        val h = intrinsicHeight.toFloat()
        val w = intrinsicWidth.toFloat()
        scaleX = scale * LEGACY_ICON_SCALE
        scaleY = scale * LEGACY_ICON_SCALE
        if (h > w && w > 0) {
            scaleX *= w / h
        } else if (w > h && h > 0) {
            scaleY *= h / w
        }
    }

    companion object {
        // TODO b/33553066 use the constant defined in MaskableIconDrawable
        private const val LEGACY_ICON_SCALE = .7f * .6667f
    }
}