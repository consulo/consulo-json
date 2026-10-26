// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.impl.light;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import org.jspecify.annotations.Nullable;

public interface JsonSchemaObjectFactory<T, V extends JsonSchemaObject & JsonSchemaNodePointer<T>> {
    /**
     * @return an instance of schema object backed by physically existing schema node that can be found by a combined json pointer, where
     * the combined pointer is created by concatenation of the current node pointer and childNodeRelativePointer argument.
     */
    @Nullable
    JsonSchemaObject getChildSchemaObjectByName(V parentSchemaObject, String... childNodeRelativePointer);

    /**
     * @return an instance of schema object backed by physically existing schema node that can be found by an absolute json pointer
     */
    @Nullable
    JsonSchemaObject getSchemaObjectByAbsoluteJsonPointer(String jsonPointer);
}
