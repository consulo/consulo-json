// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.validations;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.JsonSchemaObjectReadingUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class JsonSchemaValidationsCollector {
    private JsonSchemaValidationsCollector() {
    }

    public static Iterable<JsonSchemaValidation> getSchema7AndEarlierValidations(JsonSchemaObject schema,
                                                                                 JsonSchemaType type,
                                                                                 JsonValueAdapter value) {
        Set<JsonSchemaValidation> validations = new LinkedHashSet<>();
        if (type != null) {
            validations.addAll(getTypeValidations(type));
        }
        validations.addAll(getBaseValidations(schema, value));
        return validations;
    }

    public static List<JsonSchemaValidation> getTypeValidations(JsonSchemaType type) {
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
                // no additional validations
                break;
        }
        return validations;
    }

    public static List<JsonSchemaValidation> getBaseValidations(JsonSchemaObject schema, JsonValueAdapter value) {
        if (schema.getConstantSchema() != null) {
            List<JsonSchemaValidation> result = new ArrayList<>();
            result.add(ConstantSchemaValidation.INSTANCE);
            return result;
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

        return validations;
    }

    public static boolean hasMinMaxLengthChecks(JsonSchemaObject schema) {
        return schema.getMinLength() != null || schema.getMaxLength() != null;
    }
}
