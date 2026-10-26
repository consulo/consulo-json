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
package com.jetbrains.jsonSchema.impl.validations;

import com.jetbrains.jsonSchema.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import consulo.json.localize.JsonLocalize;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

public final class ConstantSchemaValidation implements JsonSchemaValidation {
    public static final ConstantSchemaValidation INSTANCE = new ConstantSchemaValidation();

    private ConstantSchemaValidation() {
    }

    @Override
    public boolean validate(JsonValueAdapter propValue,
                            JsonSchemaObject schema,
                            @Nullable JsonSchemaType schemaType,
                            JsonValidationHost consumer,
                            JsonComplianceCheckerOptions options) {
        if (Boolean.FALSE.equals(schema.getConstantSchema())) {
            PsiElement parent = propValue.getDelegate().getParent();
            assert parent != null;
            consumer.error(JsonLocalize.schemaValidationConstantSchema().get(), parent, JsonErrorPriority.LOW_PRIORITY);
            return false;
        }
        return true;
    }
}
