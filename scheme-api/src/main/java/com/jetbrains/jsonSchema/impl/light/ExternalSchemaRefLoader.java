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
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase;
import consulo.application.Application;
import org.jspecify.annotations.Nullable;

public final class ExternalSchemaRefLoader implements JsonSchemaRefLoader {
    public static final ExternalSchemaRefLoader INSTANCE = new ExternalSchemaRefLoader();

    private static final boolean ENABLE_NESTED_REMOTE_SCHEMA_RESOLVE = false;

    private ExternalSchemaRefLoader() {
    }

    @Override
    public @Nullable JsonSchemaObject load(String reference,
                                           JsonSchemaObjectBackedByJacksonBase referenceOwner,
                                           JsonSchemaService service) {
        // leave tests with default behaviour to not accidentally miss even more bugs
        if (!Application.get().isUnitTestMode() && !ENABLE_NESTED_REMOTE_SCHEMA_RESOLVE) {
            return null;
        }

        return JsonSchemaRefLoader.resolveRemoteSchemaByUrl(reference, referenceOwner, service);
    }

    @Override
    public String toString() {
        return "ExternalSchemaRefLoader";
    }
}
