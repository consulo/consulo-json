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
import com.jetbrains.jsonSchema.extension.adapter.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.internal.EnumArrayValueWrapper;
import com.jetbrains.jsonSchema.internal.EnumObjectValueWrapper;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import consulo.json.localize.JsonLocalize;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.X_INTELLIJ_CASE_INSENSITIVE;

public final class EnumValidation implements JsonSchemaValidation {
    public static final EnumValidation INSTANCE = new EnumValidation();

    @Override
    @SuppressWarnings("deprecation")
    public boolean validate(JsonValueAdapter propValue,
                            JsonSchemaObject schema,
                            @Nullable JsonSchemaType schemaType,
                            JsonValidationHost consumer,
                            JsonComplianceCheckerOptions options) {
        List<Object> enumItems = schema.getEnum();
        if (enumItems == null) {
            return true;
        }
        JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(propValue.getDelegate(), schema);
        if (walker == null) {
            return true;
        }
        String text = StringUtil.notNullize(walker.getNodeTextForValidation(propValue.getDelegate()));
        boolean caseInsensitive = Boolean.parseBoolean(schema.readChildNodeValue(X_INTELLIJ_CASE_INSENSITIVE)) || schema.isForceCaseInsensitive();
        BiFunction<String, String, Boolean> eq = options.isCaseInsensitiveEnumCheck() || caseInsensitive
            ? String::equalsIgnoreCase
            : String::equals;
        for (Object object : enumItems) {
            if (checkEnumValue(object, walker, propValue, text, eq)) {
                return true;
            }
        }
        consumer.error(JsonLocalize.schemaValidationEnumMismatch(StringUtil.join(enumItems, Object::toString, ", ")).get(),
            propValue.getDelegate(),
            JsonValidationError.FixableIssueKind.NonEnumValue, null, JsonErrorPriority.MEDIUM_PRIORITY);
        return false;
    }

    private static boolean checkEnumValue(Object object,
                                          JsonLikePsiWalker walker,
                                          @Nullable JsonValueAdapter adapter,
                                          String text,
                                          BiFunction<String, String, Boolean> stringEq) {
        if (adapter != null && !adapter.shouldCheckAsValue()) {
            return true;
        }
        if (object instanceof EnumArrayValueWrapper arrayValueWrapper) {
            if (adapter instanceof JsonArrayValueAdapter arrayValueAdapter) {
                List<JsonValueAdapter> elements = arrayValueAdapter.getElements();
                Object[] values = arrayValueWrapper.getValues();
                if (elements.size() == values.length) {
                    for (int i = 0; i < values.length; i++) {
                        if (!checkEnumValue(values[i], walker, elements.get(i),
                            walker.getNodeTextForValidation(elements.get(i).getDelegate()), stringEq)) {
                            return false;
                        }
                    }
                    return true;
                }
            }
        }
        else if (object instanceof EnumObjectValueWrapper objectValueWrapper) {
            if (adapter instanceof JsonObjectValueAdapter objectValueAdapter) {
                List<JsonPropertyAdapter> props = objectValueAdapter.getPropertyList();
                Map<String, @Nullable Object> values = objectValueWrapper.getValues();
                if (props.size() == values.size()) {
                    for (JsonPropertyAdapter prop : props) {
                        if (!values.containsKey(prop.getName())) {
                            return false;
                        }
                        Object enumValue = values.get(prop.getName());
                        for (JsonValueAdapter value : prop.getValues()) {
                            if (enumValue == null || !checkEnumValue(enumValue, walker, value,
                                walker.getNodeTextForValidation(value.getDelegate()), stringEq)) {
                                return false;
                            }
                        }
                    }

                    return true;
                }
            }
        }
        else {
            if (!walker.allowsSingleQuotes()) {
                if (stringEq.apply(object.toString(), text)) {
                    return true;
                }
            }
            else {
                if (equalsIgnoreQuotes(object.toString(), text, walker.requiresValueQuotes(), stringEq)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean equalsIgnoreQuotes(String s1,
                                              String s2,
                                              boolean requireQuotedValues,
                                              BiFunction<String, String, Boolean> eq) {
        boolean quoted1 = StringUtil.isQuotedString(s1);
        boolean quoted2 = StringUtil.isQuotedString(s2);
        if (requireQuotedValues && quoted1 != quoted2) {
            return false;
        }
        if (requireQuotedValues && !quoted1) {
            return eq.apply(s1, s2);
        }
        return eq.apply(StringUtil.unquoteString(s1), StringUtil.unquoteString(s2));
    }
}
