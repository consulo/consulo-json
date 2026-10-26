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
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import consulo.json.localize.JsonLocalize;
import consulo.language.psi.PsiElement;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.jsonSchema.internal.JsonSchemaAnnotatorChecker.getValue;

public final class StringValidation implements JsonSchemaValidation {
    public static final StringValidation INSTANCE = new StringValidation();

    @Override
    public boolean validate(JsonValueAdapter propValue,
                            JsonSchemaObject schema,
                            @Nullable JsonSchemaType schemaType,
                            JsonValidationHost consumer,
                            JsonComplianceCheckerOptions options) {
        return checkString(propValue.getDelegate(), schema, consumer, options);
    }

    private static boolean checkString(PsiElement propValue,
                                       JsonSchemaObject schema,
                                       JsonValidationHost consumer,
                                       JsonComplianceCheckerOptions options) {
        String v = getValue(propValue, schema);
        if (v == null) {
            return true;
        }
        String value = StringUtil.unquoteString(v);
        Integer minLength = schema.getMinLength();
        if (minLength != null) {
            if (value.length() < minLength) {
                consumer.error(JsonLocalize.schemaValidationStringShorterThan(minLength).get(), propValue, JsonErrorPriority.LOW_PRIORITY);
                return false;
            }
        }
        Integer maxLength = schema.getMaxLength();
        if (maxLength != null) {
            if (value.length() > maxLength) {
                consumer.error(JsonLocalize.schemaValidationStringLongerThan(maxLength).get(), propValue, JsonErrorPriority.LOW_PRIORITY);
                return false;
            }
        }
        String pattern = schema.getPattern();
        if (pattern != null) {
            String patternError = schema.getPatternError();
            if (patternError != null) {
                consumer.error(JsonLocalize.schemaValidationInvalidStringPattern(StringUtil.convertLineSeparators(patternError)).get(),
                    propValue, JsonErrorPriority.LOW_PRIORITY);
                return false;
            }
            if (!schema.checkByPattern(value)) {
                consumer.error(JsonLocalize.schemaValidationStringViolatesPattern(StringUtil.convertLineSeparators(pattern)).get(),
                    propValue, JsonErrorPriority.LOW_PRIORITY);
                return false;
            }
        }
        return true;
    }
}
