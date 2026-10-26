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
package com.jetbrains.jsonSchema.internal;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.light.JsonSchemaNodePointer;
import com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy;
import org.jspecify.annotations.Nullable;

public interface RootJsonSchemaObject<T, V extends JsonSchemaObject & JsonSchemaNodePointer<T>> {
    @Nullable
    JsonSchemaObject getChildSchemaObjectByName(V parentSchemaObject, String... childNodeRelativePointer);

    @Nullable
    JsonSchemaObject getSchemaObjectByAbsoluteJsonPointer(String jsonPointer);

    @Nullable
    String resolveId(String id);

    @Nullable
    String resolveDynamicAnchor(String anchor);

    JsonSchemaInterpretationStrategy getSchemaInterpretationStrategy();
}
