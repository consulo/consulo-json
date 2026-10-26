// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema.impl.validations;

import com.jetbrains.jsonSchema.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.internal.JsonSchemaAnnotatorChecker;
import org.jspecify.annotations.Nullable;

import java.util.Collections;

public final class TypeValidation implements JsonSchemaValidation {
    public static final TypeValidation INSTANCE = new TypeValidation();

    @Override
    public boolean validate(JsonValueAdapter propValue,
                            JsonSchemaObject schema,
                            @Nullable JsonSchemaType schemaType,
                            JsonValidationHost consumer,
                            JsonComplianceCheckerOptions options) {
        JsonSchemaType otherType = JsonSchemaAnnotatorChecker.getMatchingSchemaType(schema, schemaType);
        if (otherType != null && !otherType.equals(schemaType) && !otherType.equals(propValue.getAlternateType(schemaType))) {
            consumer.typeError(propValue.getDelegate(), propValue.getAlternateType(schemaType),
                JsonSchemaAnnotatorChecker.getExpectedTypes(Collections.singleton(schema)));
            return false;
        }
        return true;
    }
}
