/*
 * Copyright (C) 2015 The Android Open Source Project
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

/**
 * Indicates that the given field or method has package visibility solely to prevent the creation
 * of a synthetic method. In practice, you should treat this field/method as if it were private.
 *
 *
 *
 * When a private method is called from an inner class, the Java compiler generates a simple
 * package private shim method that the class generated from the inner class can call. This results
 * in unnecessary bloat and runtime method call overhead. It also gets us closer to the dex method
 * count limit.
 *
 *
 *
 * If you'd like to see warnings for these synthetic methods in eclipse, turn on:
 * Window > Preferences > Java > Compiler > Errors/Warnings > "Access to a non-accessible member
 * of an enclosing type".
 *
 *
 *
 */
@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.FIELD, AnnotationTarget.CONSTRUCTOR, AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
annotation class Thunk