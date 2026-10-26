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
import com.jetbrains.jsonSchema.JsonValidationError;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapter.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import consulo.json.localize.JsonLocalize;
import consulo.util.collection.MultiMap;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ArrayValidation implements JsonSchemaValidation {
    public static final ArrayValidation INSTANCE = new ArrayValidation();

    @Override
    public boolean validate(JsonValueAdapter propValue,
                            JsonSchemaObject schema,
                            @Nullable JsonSchemaType schemaType,
                            JsonValidationHost consumer,
                            JsonComplianceCheckerOptions options) {
        return checkArray(propValue, schema, consumer, options);
    }

    private boolean checkArray(JsonValueAdapter value,
                               JsonSchemaObject schema,
                               JsonValidationHost consumer,
                               JsonComplianceCheckerOptions options) {
        JsonArrayValueAdapter asArray = value.getAsArray();
        if (asArray == null) {
            return true;
        }
        List<JsonValueAdapter> elements = asArray.getElements();
        return checkArrayItems(value, elements, schema, consumer, options);
    }

    protected boolean checkArrayItems(JsonValueAdapter array,
                                      List<JsonValueAdapter> list,
                                      JsonSchemaObject schema,
                                      JsonValidationHost consumer,
                                      JsonComplianceCheckerOptions options) {
        if (options.shouldStopValidationAfterAnyErrorFound()) {
            return validateUniqueItems(array, list, schema, consumer, options) &&
                validateAgainstContainsSchema(array, list, schema, consumer, options) &&
                validateIndividualItems(list, schema, consumer, options) &&
                validateArrayLength(array, list, schema, consumer, options) &&
                validateArrayLengthHeuristically(array, list, schema, consumer, options);
        }
        else {
            return validateUniqueItems(array, list, schema, consumer, options) &
                validateAgainstContainsSchema(array, list, schema, consumer, options) &
                validateIndividualItems(list, schema, consumer, options) &
                validateArrayLength(array, list, schema, consumer, options) &
                validateArrayLengthHeuristically(array, list, schema, consumer, options);
        }
    }

    protected boolean validateIndividualItems(List<JsonValueAdapter> list,
                                              JsonSchemaObject schema,
                                              JsonValidationHost consumer,
                                              JsonComplianceCheckerOptions options) {
        boolean isValid = true;

        JsonSchemaObject itemsSchema = schema.getItemsSchema();
        List<? extends JsonSchemaObject> itemsSchemaList = schema.getItemsSchemaList();
        if (itemsSchema != null) {
            for (JsonValueAdapter item : list) {
                consumer.checkObjectBySchemaRecordErrors(itemsSchema, item);
                isValid &= consumer.getErrors().isEmpty();
                if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) {
                    return false;
                }
            }
        }
        else if (itemsSchemaList != null) {
            Iterator<? extends JsonSchemaObject> iterator = itemsSchemaList.iterator();
            for (JsonValueAdapter arrayValue : list) {
                if (iterator.hasNext()) {
                    consumer.checkObjectBySchemaRecordErrors(iterator.next(), arrayValue);
                    isValid &= consumer.getErrors().isEmpty();
                    if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) {
                        return false;
                    }
                }
                else {
                    JsonSchemaObject additionalItemsSchema = schema.getAdditionalItemsSchema();
                    if (!Boolean.TRUE.equals(schema.getAdditionalItemsAllowed())) {
                        consumer.error(JsonLocalize.schemaValidationArrayNoExtra().get(), arrayValue.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
                        isValid = false;
                        if (options.shouldStopValidationAfterAnyErrorFound()) {
                            return false;
                        }
                    }
                    else if (additionalItemsSchema != null) {
                        consumer.checkObjectBySchemaRecordErrors(additionalItemsSchema, arrayValue);
                        isValid &= consumer.getErrors().isEmpty();
                        if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) {
                            return false;
                        }
                    }
                }
            }
        }

        return isValid;
    }

    protected static boolean validateArrayLengthHeuristically(JsonValueAdapter array,
                                                              List<JsonValueAdapter> list,
                                                              JsonSchemaObject schema,
                                                              JsonValidationHost consumer,
                                                              JsonComplianceCheckerOptions options) {
        // these two are not correct by the schema spec, but are used in some schemas
        Integer minLength = schema.getMinLength();
        if (minLength != null && list.size() < minLength) {
            consumer.error(JsonLocalize.schemaValidationArrayShorterThan(minLength).get(), array.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
            return false;
        }
        Integer maxLength = schema.getMaxLength();
        if (maxLength != null && list.size() > maxLength) {
            consumer.error(JsonLocalize.schemaValidationArrayLongerThan(maxLength).get(), array.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
            return false;
        }
        return true;
    }

    protected static boolean validateArrayLength(JsonValueAdapter array,
                                                 List<JsonValueAdapter> list,
                                                 JsonSchemaObject schema,
                                                 JsonValidationHost consumer,
                                                 JsonComplianceCheckerOptions options) {
        Integer minItems = schema.getMinItems();
        if (minItems != null && list.size() < minItems) {
            consumer.error(JsonLocalize.schemaValidationArrayShorterThan(minItems).get(), array.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
            return false;
        }
        Integer maxItems = schema.getMaxItems();
        if (maxItems != null && list.size() > maxItems) {
            consumer.error(JsonLocalize.schemaValidationArrayLongerThan(maxItems).get(), array.getDelegate(), JsonErrorPriority.LOW_PRIORITY);
            return false;
        }
        return true;
    }

    protected static boolean validateAgainstContainsSchema(JsonValueAdapter array,
                                                           List<JsonValueAdapter> list,
                                                           JsonSchemaObject schema,
                                                           JsonValidationHost consumer,
                                                           JsonComplianceCheckerOptions options) {
        JsonSchemaObject containsSchema = schema.getContainsSchema();
        if (containsSchema != null) {
            boolean match = false;
            for (JsonValueAdapter item : list) {
                JsonValidationHost checker = consumer.checkByMatchResult(item, consumer.resolve(containsSchema, array), options);
                if (checker == null || checker.isValid()) {
                    match = true;
                    break;
                }
            }
            if (!match) {
                consumer.error(JsonLocalize.schemaValidationArrayNotContains().get(), array.getDelegate(), JsonErrorPriority.MEDIUM_PRIORITY);
                return false;
            }
        }
        return true;
    }

    protected static boolean validateUniqueItems(JsonValueAdapter array,
                                                 List<JsonValueAdapter> list,
                                                 JsonSchemaObject schema,
                                                 JsonValidationHost consumer,
                                                 JsonComplianceCheckerOptions options) {
        if (schema.isUniqueItems()) {
            MultiMap<String, JsonValueAdapter> valueTexts = new MultiMap<>();
            JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(array.getDelegate(), schema);
            assert walker != null;
            for (JsonValueAdapter adapter : list) {
                valueTexts.putValue(walker.getNodeTextForValidation(adapter.getDelegate()), adapter);
            }

            for (Map.Entry<String, Collection<JsonValueAdapter>> entry : valueTexts.entrySet()) {
                if (entry.getValue().size() > 1) {
                    for (JsonValueAdapter item : entry.getValue()) {
                        if (!item.shouldCheckAsValue()) {
                            continue;
                        }
                        consumer.error(JsonLocalize.schemaValidationNotUnique().get(), item.getDelegate(),
                            JsonValidationError.FixableIssueKind.DuplicateArrayItem,
                            new JsonValidationError.DuplicateArrayItemIssueData(
                                entry.getValue().stream().mapToInt(v -> list.indexOf(v)).toArray()
                            ),
                            JsonErrorPriority.TYPE_MISMATCH);
                        if (options.shouldStopValidationAfterAnyErrorFound()) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }
}
