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

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class JsonSchemaValidationsCollector {
    private JsonSchemaValidationsCollector() {
    }

    public static Stream<JsonSchemaValidation> getSchema7AndEarlierValidations(JsonSchemaObject schema,
                                                                               @Nullable JsonSchemaType type,
                                                                               JsonValueAdapter value) {
        Stream<JsonSchemaValidation> typeValidations = type != null ? getTypeValidations(type) : Stream.empty();
        return Stream.concat(typeValidations, getBaseValidations(schema, value)).distinct();
    }

    public static Stream<JsonSchemaValidation> getTypeValidations(JsonSchemaType type) {
        List<JsonSchemaValidation> validations = new ArrayList<>();
        validations.add(TypeValidation.INSTANCE);
        switch (type) {
            case _string_number:
                validations.add(NumericValidation.INSTANCE);
                validations.add(StringValidation.INSTANCE);
                break;
            case _number:
            case _integer:
                validations.add(NumericValidation.INSTANCE);
                break;
            case _string:
                validations.add(StringValidation.INSTANCE);
                break;
            case _array:
                validations.add(ArrayValidation.INSTANCE);
                break;
            case _object:
                validations.add(ObjectValidation.INSTANCE);
                break;
            default:
                break;
        }
        return validations.stream();
    }

    public static Stream<JsonSchemaValidation> getBaseValidations(JsonSchemaObject schema, JsonValueAdapter value) {
        if (schema.getConstantSchema() != null) {
            return Stream.of(ConstantSchemaValidation.INSTANCE);
        }

        List<JsonSchemaValidation> validations = new ArrayList<>();
        validations.add(EnumValidation.INSTANCE);

        if (!value.isShouldBeIgnored()) {
            if (JsonSchemaObjectReadingUtils.hasNumericChecks(schema) && value.isNumberLiteral()) {
                validations.add(NumericValidation.INSTANCE);
            }
            if (JsonSchemaObjectReadingUtils.hasStringChecks(schema) && value.isStringLiteral()) {
                validations.add(StringValidation.INSTANCE);
            }
            if (JsonSchemaObjectReadingUtils.hasArrayChecks(schema) && value.isArray()) {
                validations.add(ArrayValidation.INSTANCE);
            }
            if (hasMinMaxLengthChecks(schema)) {
                if (value.isStringLiteral()) {
                    validations.add(StringValidation.INSTANCE);
                }
                else if (value.isArray()) {
                    validations.add(ArrayValidation.INSTANCE);
                }
            }
            if (JsonSchemaObjectReadingUtils.hasObjectChecks(schema) && value.isObject()) {
                validations.add(ObjectValidation.INSTANCE);
            }
        }

        if (schema.getNot() != null) {
            validations.add(NotValidation.INSTANCE);
        }

        return validations.stream();
    }

    public static boolean hasMinMaxLengthChecks(JsonSchemaObject schema) {
        return schema.getMinLength() != null || schema.getMaxLength() != null;
    }
}
