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
package com.android.launcher3.views

import android.content.Context
import android.graphics.Canvas
import android.support.animation.DynamicAnimation.OnAnimationEndListener
import android.support.animation.FloatPropertyCompat
import android.support.animation.SpringAnimation
import android.support.animation.SpringForce
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.RecyclerView.EdgeEffectFactory
import android.util.AttributeSet
import android.util.SparseBooleanArray
import android.view.View
import android.widget.EdgeEffect
import android.widget.RelativeLayout

open class SpringRelativeLayout
@JvmOverloads
constructor(
        context: Context?,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {
    private val springViews = SparseBooleanArray()
    private val spring = SpringAnimation(this, DAMPED_SCROLL, 0f).also {
        it.spring = SpringForce(0f)
                .setStiffness(STIFFNESS)
                .setDampingRatio(DAMPING_RATIO)
    }
    private var dampedScrollShift = 0f
    private var activeEdge: SpringEdgeEffect? = null

    fun addSpringView(id: Int) {
        springViews.put(id, true)
    }

    fun removeSpringView(id: Int) {
        springViews.delete(id)
        invalidate()
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (dampedScrollShift != 0f && springViews[child.id]) {
            canvas.translate(0f, dampedScrollShift)
            val result = super.drawChild(canvas, child, drawingTime)
            canvas.translate(0f, -dampedScrollShift)
            return result
        }
        return super.drawChild(canvas, child, drawingTime)
    }

    private fun setActiveEdge(edge: SpringEdgeEffect) {
        if (activeEdge != edge) {
            activeEdge?.distance = 0f
        }
        activeEdge = edge
    }

    protected open fun setDampedScrollShift(shift: Float) {
        if (shift != dampedScrollShift) {
            dampedScrollShift = shift
            invalidate()
        }
    }

    private fun finishScrollWithVelocity(velocity: Float) {
        spring.setStartVelocity(velocity)
        spring.setStartValue(dampedScrollShift)
        spring.start()
    }

    protected fun finishWithShiftAndVelocity(shift: Float, velocity: Float,
                                             listener: OnAnimationEndListener?) {
        setDampedScrollShift(shift)
        spring.addEndListener(listener)
        finishScrollWithVelocity(velocity)
    }

    fun createEdgeEffectFactory(): EdgeEffectFactory = SpringEdgeEffectFactory()

    private inner class SpringEdgeEffectFactory : EdgeEffectFactory() {
        override fun createEdgeEffect(view: RecyclerView, direction: Int): EdgeEffect {
            when (direction) {
                DIRECTION_TOP -> return SpringEdgeEffect(context, +VELOCITY_MULTIPLIER)
                DIRECTION_BOTTOM -> return SpringEdgeEffect(context, -VELOCITY_MULTIPLIER)
            }
            return super.createEdgeEffect(view, direction)
        }
    }

    private inner class SpringEdgeEffect(context: Context?, private val mVelocityMultiplier: Float) : EdgeEffect(context) {
        var distance = 0f
        override fun draw(canvas: Canvas) = false

        override fun onAbsorb(velocity: Int) {
            finishScrollWithVelocity(velocity * mVelocityMultiplier)
        }

        override fun onPull(deltaDistance: Float, displacement: Float) {
            setActiveEdge(this)
            distance += deltaDistance * (mVelocityMultiplier / 3f)
            setDampedScrollShift(distance * height)
        }

        override fun onRelease() {
            distance = 0f
            finishScrollWithVelocity(0f)
        }

    }

    companion object {
        private const val STIFFNESS = (SpringForce.STIFFNESS_MEDIUM + SpringForce.STIFFNESS_LOW) / 2
        private const val DAMPING_RATIO = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        private const val VELOCITY_MULTIPLIER = 0.3f
        private val DAMPED_SCROLL = object : FloatPropertyCompat<SpringRelativeLayout>("value") {
            override fun getValue(`object`: SpringRelativeLayout): Float {
                return `object`.dampedScrollShift
            }

            override fun setValue(`object`: SpringRelativeLayout, value: Float) {
                `object`.setDampedScrollShift(value)
            }
        }
    }
}