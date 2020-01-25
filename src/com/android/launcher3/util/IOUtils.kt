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
package com.android.launcher3.util

import java.io.*

/**
 * Supports various IO utility functions
 */
object IOUtils {
    private const val BUF_SIZE = 0x1000 // 4K

    @JvmStatic
    @Throws(IOException::class)
    fun toByteArray(file: File): ByteArray {
        FileInputStream(file).use { `in` -> return toByteArray(`in`) }
    }

    @Throws(IOException::class)
    private fun toByteArray(`in`: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        copy(`in`, out)
        return out.toByteArray()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun copy(from: InputStream, to: OutputStream): Long {
        val buf = ByteArray(BUF_SIZE)
        var total = 0L
        var r: Int
        while (from.read(buf).also { r = it } != -1) {
            to.write(buf, 0, r)
            total += r.toLong()
        }
        return total
    }
}