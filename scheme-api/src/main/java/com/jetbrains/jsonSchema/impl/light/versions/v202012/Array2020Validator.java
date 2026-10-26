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
package com.jetbrains.jsonSchema.impl.light.versions.v202012;

import com.jetbrains.jsonSchema.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.validations.ArrayValidation;
import consulo.json.localize.JsonLocalize;

import java.util.List;

public final class Array2020Validator extends ArrayValidation {
    public static final Array2020Validator INSTANCE = new Array2020Validator();

    private Array2020Validator() {
    }

    @Override
    protected boolean validateIndividualItems(List<JsonValueAdapter> instanceArrayItems,
                                              JsonSchemaObject schema,
                                              JsonValidationHost consumer,
                                              JsonComplianceCheckerOptions options) {
        List<? extends JsonSchemaObject> additionalItemsSchemaList = schema.getItemsSchemaList();
        int firstRegularItemIndex =
            additionalItemsSchemaList == null || additionalItemsSchemaList.isEmpty() ? 0 : additionalItemsSchemaList.size();

        boolean isValid = true;

        // check instance items with positional schema
        for (int index = 0; index < firstRegularItemIndex; index++) {
            if (additionalItemsSchemaList == null || index >= instanceArrayItems.size()) {
                break;
            }
            JsonSchemaObject positionalSchema = additionalItemsSchemaList.get(index);
            JsonValueAdapter inspectedInstanceItem = instanceArrayItems.get(index);
            consumer.checkObjectBySchemaRecordErrors(positionalSchema, inspectedInstanceItem);

            isValid = isValid && consumer.getErrors().isEmpty();
            if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) {
                return false;
            }
        }

        // check the rest of instance items with regular schema
        JsonSchemaObject additionalItemsSchema = schema.getAdditionalItemsSchema();
        if (additionalItemsSchema != null) {
            isValid = isValid && validateAgainstNonPositionalSchema(
                additionalItemsSchema, instanceArrayItems, firstRegularItemIndex, consumer, options,
                JsonLocalize.schemaValidationArrayNoExtra().get());
            if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) {
                return false;
            }
        }

        JsonSchemaObject unevaluatedItemsSchema = schema.getUnevaluatedItemsSchema();
        if (unevaluatedItemsSchema != null) {
            isValid = isValid && validateAgainstNonPositionalSchema(
                unevaluatedItemsSchema, instanceArrayItems, firstRegularItemIndex, consumer, options,
                JsonLocalize.schemaValidationArrayNoUnevaluated().get());
            if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) {
                return false;
            }
        }

        return isValid;
    }

    private static boolean validateAgainstNonPositionalSchema(JsonSchemaObject nonPositionalItemsSchema,
                                                              List<JsonValueAdapter> instanceArrayItems,
                                                              int firstRegularItemIndex,
                                                              JsonValidationHost consumer,
                                                              JsonComplianceCheckerOptions options,
                                                              String errorMessage) {
        if (Boolean.TRUE.equals(nonPositionalItemsSchema.getConstantSchema())) {
            return true;
        }

        if (Boolean.FALSE.equals(nonPositionalItemsSchema.getConstantSchema()) && firstRegularItemIndex < instanceArrayItems.size()) {
            consumer.error(errorMessage, instanceArrayItems.get(firstRegularItemIndex).getDelegate(), JsonErrorPriority.LOW_PRIORITY);
            return false;
        }

        boolean isValid = true;
        for (int index = firstRegularItemIndex; index < instanceArrayItems.size(); index++) {
            JsonValueAdapter instanceArrayItem = instanceArrayItems.get(index);
            consumer.checkObjectBySchemaRecordErrors(nonPositionalItemsSchema, instanceArrayItem);

            isValid = isValid && consumer.getErrors().isEmpty();
            if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) {
                return false;
            }
        }
        return isValid;
    }
}
