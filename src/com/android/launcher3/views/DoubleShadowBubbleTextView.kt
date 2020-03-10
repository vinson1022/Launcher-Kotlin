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
package com.android.launcher3.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.support.v4.graphics.ColorUtils
import android.util.AttributeSet
import android.widget.TextView
import com.android.launcher3.BubbleTextView
import com.android.launcher3.R

/**
 * Extension of [BubbleTextView] which draws two shadows on the text (ambient and key shadows}
 */
class DoubleShadowBubbleTextView
@JvmOverloads constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyle: Int = 0
) : BubbleTextView(context, attrs, defStyle) {

    private val shadowInfo: ShadowInfo

    init {
        shadowInfo = ShadowInfo(context, attrs, defStyle)
        setShadowLayer(shadowInfo.ambientShadowBlur, 0f, 0f, shadowInfo.ambientShadowColor)
    }

    override fun onDraw(canvas: Canvas) {
        // If text is transparent or shadow alpha is 0, don't draw any shadow
        if (shadowInfo.skipDoubleShadow(this)) {
            super.onDraw(canvas)
            return
        }
        val alpha = Color.alpha(currentTextColor)

        // We enhance the shadow by drawing the shadow twice
        paint.setShadowLayer(shadowInfo.ambientShadowBlur, 0f, 0f,
                ColorUtils.setAlphaComponent(shadowInfo.ambientShadowColor, alpha))
        drawWithoutBadge(canvas)
        canvas.save()
        canvas.clipRect(scrollX, scrollY + extendedPaddingTop,
                scrollX + width,
                scrollY + height)
        paint.setShadowLayer(shadowInfo.keyShadowBlur, 0.0f, shadowInfo.keyShadowOffset,
                ColorUtils.setAlphaComponent(shadowInfo.keyShadowColor, alpha))
        drawWithoutBadge(canvas)
        canvas.restore()
        drawBadgeIfNecessary(canvas)
    }

    class ShadowInfo(c: Context, attrs: AttributeSet?, defStyle: Int) {
        val ambientShadowBlur: Float
        val ambientShadowColor: Int
        val keyShadowBlur: Float
        val keyShadowOffset: Float
        val keyShadowColor: Int

        init {
            val a = c.obtainStyledAttributes(
                    attrs, R.styleable.ShadowInfo, defStyle, 0)
            ambientShadowBlur = a.getDimension(R.styleable.ShadowInfo_ambientShadowBlur, 0f)
            ambientShadowColor = a.getColor(R.styleable.ShadowInfo_ambientShadowColor, 0)
            keyShadowBlur = a.getDimension(R.styleable.ShadowInfo_keyShadowBlur, 0f)
            keyShadowOffset = a.getDimension(R.styleable.ShadowInfo_keyShadowOffset, 0f)
            keyShadowColor = a.getColor(R.styleable.ShadowInfo_keyShadowColor, 0)
            a.recycle()
        }

        fun skipDoubleShadow(textView: TextView): Boolean {
            val textAlpha = Color.alpha(textView.currentTextColor)
            val keyShadowAlpha = Color.alpha(keyShadowColor)
            val ambientShadowAlpha = Color.alpha(ambientShadowColor)
            return if (textAlpha == 0 || keyShadowAlpha == 0 && ambientShadowAlpha == 0) {
                textView.paint.clearShadowLayer()
                true
            } else if (ambientShadowAlpha > 0) {
                textView.paint.setShadowLayer(ambientShadowBlur, 0f, 0f,
                        ColorUtils.setAlphaComponent(ambientShadowColor, textAlpha))
                true
            } else if (keyShadowAlpha > 0) {
                textView.paint.setShadowLayer(keyShadowBlur, 0.0f, keyShadowOffset,
                        ColorUtils.setAlphaComponent(keyShadowColor, textAlpha))
                true
            } else {
                false
            }
        }
    }
}