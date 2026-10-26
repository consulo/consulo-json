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

import com.intellij.json.pointer.JsonPointerPosition;
import com.jetbrains.jsonSchema.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.JsonValidationError;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapter.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.internal.EditDistance;
import com.jetbrains.jsonSchema.internal.MatchResult;
import consulo.json.localize.JsonLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ThreeState;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static com.jetbrains.jsonSchema.internal.JsonSchemaVariantsTreeBuilder.doSingleStep;

public final class ObjectValidation implements JsonSchemaValidation {
    public static final ObjectValidation INSTANCE = new ObjectValidation();

    @Override
    public boolean validate(JsonValueAdapter propValue,
                            JsonSchemaObject schema,
                            @Nullable JsonSchemaType schemaType,
                            JsonValidationHost consumer,
                            JsonComplianceCheckerOptions options) {
        return checkObject(propValue, schema, consumer, options);
    }

    private static final int MIN_LENGTH_TO_FIX_TYPOS = 3;

    private static boolean checkObject(JsonValueAdapter value,
                                       JsonSchemaObject schema,
                                       JsonValidationHost consumer,
                                       JsonComplianceCheckerOptions options) {
        JsonObjectValueAdapter object = value.getAsObject();
        if (object == null) {
            return true;
        }

        boolean isValid = true;

        List<JsonPropertyAdapter> propertyList = object.getPropertyList();
        Set<String> set = new HashSet<>();
        for (JsonPropertyAdapter property : propertyList) {
            String name = StringUtil.notNullize(property.getName());
            JsonSchemaObject propertyNamesSchema = schema.getPropertyNamesSchema();
            JsonValueAdapter nameValueAdapter = property.getNameValueAdapter();
            if (propertyNamesSchema != null) {
                if (nameValueAdapter != null) {
                    JsonValidationHost checker = consumer.checkByMatchResult(nameValueAdapter, consumer.resolve(propertyNamesSchema,
                                                                                                                nameValueAdapter), options);
                    if (checker != null) {
                        consumer.addErrorsFrom(checker);
                        isValid = false;
                        if (options.shouldStopValidationAfterAnyErrorFound()) {
                            return false;
                        }
                    }
                }
            }

            JsonPointerPosition step = JsonPointerPosition.createSingleProperty(name);
            Pair<ThreeState, @Nullable JsonSchemaObject> pair = doSingleStep(step, schema);
            if (ThreeState.NO.equals(pair.getFirst()) && !set.contains(name)) {
                Iterator<String> propertyNamesIterator = schema.getPropertyNames();
                List<String> typoCandidates = ContainerUtil.filter(
                  iteratorToList(propertyNamesIterator),
                  s -> EditDistance.optimalAlignment(s, name, false, 1) <= 1
                );
                consumer.error((name.length() < MIN_LENGTH_TO_FIX_TYPOS || typoCandidates.isEmpty() ?
                                JsonLocalize.jsonSchemaAnnotationNotAllowedProperty(name) :
                                JsonLocalize.jsonSchemaAnnotationNotAllowedPropertyPossiblyTypo(name)).get(),
                               nameValueAdapter != null ? nameValueAdapter.getDelegate() : property.getDelegate(),
                               JsonValidationError.FixableIssueKind.ProhibitedProperty,
                               new JsonValidationError.ProhibitedPropertyIssueData(
                                 name,
                                 name.length() >= MIN_LENGTH_TO_FIX_TYPOS ? typoCandidates : Collections.emptyList()
                               ), JsonErrorPriority.LOW_PRIORITY);
                isValid = false;
                if (options.shouldStopValidationAfterAnyErrorFound()) {
                    return false;
                }
            }
            else if (ThreeState.UNSURE.equals(pair.getFirst()) && pair.getSecond() != null && pair.getSecond().getConstantSchema() == null) {
                JsonSchemaObject propertySchema = pair.getSecond();
                for (JsonValueAdapter propertyValue : property.getValues()) {
                    consumer.checkObjectBySchemaRecordErrors(propertySchema, propertyValue);
                    isValid &= consumer.getErrors().isEmpty();
                    if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) {
                        return false;
                    }
                }
            }
            set.add(name);
        }
        reportMissingOptionalProperties(value, schema, consumer, options);

        if (object.shouldCheckIntegralRequirements() || options.isForceStrict()) {
            Set<String> required = schema.getRequired();
            if (required != null) {
                Set<String> requiredNames = new LinkedHashSet<>(required);
                requiredNames.removeAll(set);
                if (!requiredNames.isEmpty()) {
                    JsonValidationError.MissingMultiplePropsIssueData data = createMissingPropertiesData(schema, requiredNames, consumer, value);
                    consumer.error(JsonLocalize.schemaValidationMissingRequiredPropertyOrProperties(data.getMessage(false)).get(),
                                   value.getDelegate(), JsonValidationError.FixableIssueKind.MissingProperty, data,
                                   JsonErrorPriority.MISSING_PROPS);
                    isValid = false;
                    if (options.shouldStopValidationAfterAnyErrorFound()) {
                        return false;
                    }
                }
            }
            if (schema.getMinProperties() != null && propertyList.size() < schema.getMinProperties()) {
                consumer.error(JsonLocalize.schemaValidationNumberOfPropsLessThan(schema.getMinProperties()).get(), value.getDelegate(),
                               JsonErrorPriority.LOW_PRIORITY);
                isValid = false;
                if (options.shouldStopValidationAfterAnyErrorFound()) {
                    return false;
                }
            }
            if (schema.getMaxProperties() != null && propertyList.size() > schema.getMaxProperties()) {
                consumer.error(JsonLocalize.schemaValidationNumberOfPropsGreaterThan(schema.getMaxProperties()).get(), value.getDelegate(),
                               JsonErrorPriority.LOW_PRIORITY);
                isValid = false;
                if (options.shouldStopValidationAfterAnyErrorFound()) {
                    return false;
                }
            }
            Map<String, List<String>> dependencies = schema.getPropertyDependencies();
            if (dependencies != null) {
                for (Map.Entry<String, List<String>> entry : dependencies.entrySet()) {
                    if (set.contains(entry.getKey())) {
                        List<String> list = entry.getValue();
                        Set<String> deps = new HashSet<>(list);
                        deps.removeAll(set);
                        if (!deps.isEmpty()) {
                            JsonValidationError.MissingMultiplePropsIssueData data = createMissingPropertiesData(schema, deps, consumer, value);
                            consumer.error(
                              JsonLocalize.schemaValidationViolatedDependency(data.getMessage(false), entry.getKey()).get(),
                              value.getDelegate(),
                              JsonValidationError.FixableIssueKind.MissingProperty,
                              data, JsonErrorPriority.MISSING_PROPS);
                            isValid = false;
                            if (options.shouldStopValidationAfterAnyErrorFound()) {
                                return false;
                            }
                        }
                    }
                }
            }
            for (Iterator<String> iterator = schema.getSchemaDependencyNames(); iterator.hasNext(); ) {
                String name = iterator.next();
                JsonSchemaObject dependency = schema.getSchemaDependencyByName(name);
                if (set.contains(name) && dependency != null) {
                    consumer.checkObjectBySchemaRecordErrors(dependency, value);
                    isValid &= consumer.getErrors().isEmpty();
                    if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) {
                        return false;
                    }
                }
            }
        }
        return checkUnevaluatedPropertiesSchemaViolation(consumer, schema, object, options);
    }

    private static List<String> iteratorToList(Iterator<String> propertyNamesIterator) {
        return Collections.list(
          new Enumeration<>() {
              @Override
              public boolean hasMoreElements() {
                  return propertyNamesIterator.hasNext();
              }

              @Override
              public String nextElement() {
                  return propertyNamesIterator.next();
              }
          }
        );
    }

    private static boolean checkUnevaluatedPropertiesSchemaViolation(JsonValidationHost consumer,
                                                                     JsonSchemaObject schemaNode,
                                                                     JsonObjectValueAdapter inspectedObject,
                                                                     JsonComplianceCheckerOptions options) {
        JsonSchemaObject unevaluatedPropertiesSchema = schemaNode.getUnevaluatedPropertiesSchema();
        if (unevaluatedPropertiesSchema == null) {
            return true;
        }

        Boolean constantSchemaValue = unevaluatedPropertiesSchema.getConstantSchema();
        if (Boolean.TRUE.equals(constantSchemaValue)) {
            return true;
        }

        boolean isValid = true;
        for (JsonPropertyAdapter childPropertyAdapter : inspectedObject.getPropertyList()) {
            if (isCoveredByAdjacentSchemas(consumer, childPropertyAdapter, schemaNode)) {
                continue;
            }

            JsonValueAdapter childPropertyNameAdapter = childPropertyAdapter.getNameValueAdapter();
            if (childPropertyNameAdapter == null) {
                continue;
            }

            consumer.checkObjectBySchemaRecordErrors(unevaluatedPropertiesSchema, childPropertyNameAdapter);
            isValid &= consumer.getErrors().isEmpty();
            if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) {
                return false;
            }
        }
        return isValid;
    }

    private static boolean isCoveredByAdjacentSchemas(JsonValidationHost validationHost,
                                                      JsonPropertyAdapter propertyAdapter,
                                                      JsonSchemaObject schemaNode) {
        String instancePropertyName = propertyAdapter.getName();
        if (instancePropertyName != null && schemaNode.getPropertyByName(instancePropertyName) != null) {
            return true;
        }
        if (instancePropertyName != null && schemaNode.getMatchingPatternPropertySchema(instancePropertyName) != null) {
            return true;
        }
        if (!schemaNode.getAdditionalPropertiesAllowed()) {
            return true;
        }
        JsonSchemaObject additionalPropertiesSchema = schemaNode.getAdditionalPropertiesSchema();
        if (additionalPropertiesSchema != null && Boolean.TRUE.equals(additionalPropertiesSchema.getConstantSchema())) {
            return true;
        }
        JsonValueAdapter propertyNameAdapter = propertyAdapter.getNameValueAdapter();
        if (propertyNameAdapter != null && validationHost.hasRecordedErrorsFor(propertyNameAdapter)) {
            return true;
        }

        return false;
    }

    public static JsonValidationError.MissingMultiplePropsIssueData createMissingPropertiesData(JsonSchemaObject schema,
                                                                                                Set<String> requiredNames,
                                                                                                JsonValidationHost consumer,
                                                                                                JsonValueAdapter inspectedElementAdapter) {
        List<JsonValidationError.MissingPropertyIssueData> allProps = new ArrayList<>();
        for (String req : requiredNames) {
            JsonSchemaObject propertySchema = resolvePropertySchema(schema, req);
            Object defaultValue = propertySchema == null ? null : propertySchema.getDefault();
            if (defaultValue == null) {
                defaultValue = schema.getExampleByName(req);
            }
            Ref<Integer> enumCount = Ref.create(0);

            JsonSchemaType type = null;

            if (propertySchema != null) {
                MatchResult result = null;
                Object valueFromEnum = getDefaultValueFromEnum(propertySchema, enumCount);
                if (valueFromEnum != null) {
                    defaultValue = valueFromEnum;
                }
                else {
                    result = consumer.resolve(propertySchema, inspectedElementAdapter);
                    if (result.mySchemas.size() == 1) {
                        valueFromEnum = getDefaultValueFromEnum(result.mySchemas.get(0), enumCount);
                        if (valueFromEnum != null) {
                            defaultValue = valueFromEnum;
                        }
                    }
                }
                type = propertySchema.getType();
                if (type == null) {
                    if (result == null) {
                        result = consumer.resolve(propertySchema, inspectedElementAdapter);
                    }
                    if (result.mySchemas.size() == 1) {
                        type = result.mySchemas.get(0).getType();
                    }
                }
            }
            allProps.add(new JsonValidationError.MissingPropertyIssueData(req,
                                                                          type,
                                                                          defaultValue,
                                                                          ObjectUtil.notNull(enumCount.get(), 0)));
        }

        return new JsonValidationError.MissingMultiplePropsIssueData(allProps);
    }

    private static @Nullable JsonSchemaObject resolvePropertySchema(JsonSchemaObject schema, String req) {
        JsonSchemaObject propOrNull = schema.getPropertyByName(req);
        if (propOrNull != null) {
            return propOrNull;
        }

        JsonSchemaObject propertySchema = schema.getMatchingPatternPropertySchema(req);
        if (propertySchema != null) {
            return propertySchema;
        }

        JsonSchemaObject additionalPropertiesSchema = schema.getAdditionalPropertiesSchema();
        if (additionalPropertiesSchema != null) {
            return additionalPropertiesSchema;
        }

        JsonSchemaObject unevaluatedPropertiesSchema = schema.getUnevaluatedPropertiesSchema();
        if (unevaluatedPropertiesSchema != null && unevaluatedPropertiesSchema.getConstantSchema() == null) {
            return unevaluatedPropertiesSchema;
        }

        return null;
    }


    private static @Nullable Object getDefaultValueFromEnum(JsonSchemaObject propertySchema, Ref<Integer> enumCount) {
        List<Object> enumValues = propertySchema.getEnum();
        if (enumValues != null) {
            enumCount.set(enumValues.size());
            if (!enumValues.isEmpty()) {
                Object defaultObject = enumValues.get(0);
                return defaultObject instanceof String ? StringUtil.unquoteString((String)defaultObject) : defaultObject;
            }
        }
        return null;
    }

    private static void reportMissingOptionalProperties(JsonValueAdapter inspectedValue,
                                                        JsonSchemaObject schema,
                                                        JsonValidationHost validationHost,
                                                        JsonComplianceCheckerOptions options) {
        JsonObjectValueAdapter objectValueAdapter = inspectedValue.getAsObject();
        if (!options.isReportMissingOptionalProperties() || objectValueAdapter == null) {
            return;
        }

        Set<String> existingProperties = new HashSet<>();
        for (JsonPropertyAdapter propertyAdapter : objectValueAdapter.getPropertyList()) {
            String name = propertyAdapter.getName();
            if (name != null) {
                existingProperties.add(name);
            }
        }

        Iterable<String> iter = (() -> schema.getPropertyNames());
        Set<String> missingProperties =
          StreamSupport.stream(iter.spliterator(), false).filter(it -> !existingProperties.contains(it)).collect(Collectors.toSet());
        JsonValidationError.MissingMultiplePropsIssueData missingPropertiesData = createMissingPropertiesData(schema, missingProperties, validationHost, objectValueAdapter);
        validationHost.error(
          JsonLocalize.schemaValidationMissingNotRequiredPropertyOrProperties(missingPropertiesData.getMessage(false)).get(),
          inspectedValue.getDelegate(),
          JsonValidationError.FixableIssueKind.MissingOptionalProperty,
          missingPropertiesData,
          JsonErrorPriority.MISSING_PROPS);
    }
}
