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

import com.jetbrains.jsonSchema.JsonAnnotationsCollectionMode;
import com.jetbrains.jsonSchema.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.language.psi.PsiElement;
import consulo.util.dataholder.Key;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class JsonSchemaValidityCache {
    private static final Key<CachedValue<Map<JsonSchemaObject, Boolean>>> JSON_SCHEMA_VALIDATION_MAP =
        Key.create("JSON_SCHEMA_VALIDATION_MAP");

    private JsonSchemaValidityCache() {
    }

    public static boolean getOrComputeAdapterValidityAgainstGivenSchema(JsonValueAdapter value, JsonSchemaObject schema) {
        PsiElement delegatePsi = value.getDelegate();
        Map<JsonSchemaObject, Boolean> cachedMap = CachedValuesManager.getManager(delegatePsi.getProject()).getCachedValue(
            delegatePsi,
            JSON_SCHEMA_VALIDATION_MAP,
            () -> CachedValueProvider.Result.create(
                new ConcurrentHashMap<>(),
                delegatePsi.getManager().getModificationTracker().forLanguage(delegatePsi.getLanguage()),
                JsonSchemaService.Impl.get(delegatePsi.getProject())
            ),
            false
        );

        Boolean cachedValue = cachedMap.get(schema);
        if (cachedValue != null) {
            return cachedValue;
        }

        JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker(
            value.getDelegate().getProject(),
            new JsonComplianceCheckerOptions(false, false, false, JsonAnnotationsCollectionMode.FIND_FIRST)
        );
        checker.checkByScheme(value, schema);
        boolean computedValue = checker.isCorrect();

        cachedMap.put(schema, computedValue);
        return computedValue;
    }
}
