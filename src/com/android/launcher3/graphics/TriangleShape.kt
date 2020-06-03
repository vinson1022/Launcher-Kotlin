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

import android.graphics.Outline
import android.graphics.Path
import android.graphics.drawable.shapes.PathShape

/**
 * Wrapper around [android.graphics.drawable.shapes.PathShape]
 * that creates a shape with a triangular path (pointing up or down).
 */
class TriangleShape(
        private val triangularPath: Path,
        stdWidth: Float,
        stdHeight: Float
) : PathShape(triangularPath, stdWidth, stdHeight) {

    override fun getOutline(outline: Outline) {
        outline.setConvexPath(triangularPath)
    }

    companion object {
        fun create(width: Float, height: Float, isPointingUp: Boolean): TriangleShape {
            val triangularPath = Path().apply {
                if (isPointingUp) {
                    moveTo(0f, height)
                    lineTo(width, height)
                    lineTo(width / 2, 0f)
                    close()
                } else {
                    moveTo(0f, 0f)
                    lineTo(width / 2, height)
                    lineTo(width, 0f)
                    close()
                }
            }
            return TriangleShape(triangularPath, width, height)
        }
    }

}