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

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.light.ExternalSchemaRefLoader;
import com.jetbrains.jsonSchema.impl.light.InFileSchemaRefLoader;
import com.jetbrains.jsonSchema.impl.light.JsonSchemaRefLoader;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy;
import com.jetbrains.jsonSchema.impl.validations.ConstantSchemaValidation;
import com.jetbrains.jsonSchema.impl.validations.EnumValidation;
import com.jetbrains.jsonSchema.impl.validations.JsonSchemaValidationsCollector;
import com.jetbrains.jsonSchema.impl.validations.NotValidation;
import com.jetbrains.jsonSchema.impl.validations.NumericValidation;
import com.jetbrains.jsonSchema.impl.validations.ObjectValidation;
import com.jetbrains.jsonSchema.impl.validations.StringValidation;
import com.jetbrains.jsonSchema.impl.validations.TypeValidation;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.DEFS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.DEPENDENT_REQUIRED;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.DEPENDENT_SCHEMAS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.DYNAMIC_ANCHOR;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.DYNAMIC_REF;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.ITEMS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.JSON_DOLLAR_ID;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.PREFIX_ITEMS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.UNEVALUATED_ITEMS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.UNEVALUATED_PROPERTIES;

public final class JsonSchema202012Strategy implements JsonSchemaInterpretationStrategy {
    public static final JsonSchema202012Strategy INSTANCE = new JsonSchema202012Strategy();

    private static final List<JsonSchemaRefLoader> REF_LOADERS = List.of(
        InFileSchemaRefLoader.INSTANCE,
        ExternalSchemaRefLoader.INSTANCE,
        Vocabulary2020RefLoader.INSTANCE
    );

    private JsonSchema202012Strategy() {
    }

    @Override
    public String getIdKeyword() {
        return JSON_DOLLAR_ID;
    }

    @Override
    public String getNonPositionalItemsKeyword() {
        return ITEMS;
    }

    @Override
    public String getPositionalItemsKeyword() {
        return PREFIX_ITEMS;
    }

    @Override
    public String getDefinitionsKeyword() {
        return DEFS;
    }

    @Override
    public String getDynamicReferenceKeyword() {
        return DYNAMIC_REF;
    }

    @Override
    public String getDynamicAnchorKeyword() {
        return DYNAMIC_ANCHOR;
    }

    @Override
    public String getDependencySchemasKeyword() {
        return DEPENDENT_SCHEMAS;
    }

    @Override
    public String getPropertyDependenciesKeyword() {
        return DEPENDENT_REQUIRED;
    }

    @Override
    public String getUnevaluatedItemsKeyword() {
        return UNEVALUATED_ITEMS;
    }

    @Override
    public String getUnevaluatedPropertiesKeyword() {
        return UNEVALUATED_PROPERTIES;
    }

    @Override
    public List<JsonSchemaRefLoader> getRefLoaders() {
        return REF_LOADERS;
    }

    @Override
    public Stream<JsonSchemaValidation> getValidations(JsonSchemaObject schemaNode, @Nullable JsonSchemaType type, JsonValueAdapter value) {
        Stream<JsonSchemaValidation> typeValidations = type != null ? getTypeValidations(type) : Stream.empty();
        return Stream.concat(typeValidations, getBaseValidations(value, schemaNode));
    }

    private Stream<JsonSchemaValidation> getBaseValidations(JsonValueAdapter value, JsonSchemaObject schemaNode) {
        if (schemaNode.getConstantSchema() != null) {
            return Stream.of(ConstantSchemaValidation.INSTANCE);
        }

        Stream.Builder<JsonSchemaValidation> validations = Stream.builder();
        validations.add(EnumValidation.INSTANCE);

        if (!value.isShouldBeIgnored()) {
            if (JsonSchemaObjectReadingUtils.hasNumericChecks(schemaNode) && value.isNumberLiteral()) {
                validations.add(NumericValidation.INSTANCE);
            }
            if (JsonSchemaObjectReadingUtils.hasStringChecks(schemaNode) && value.isStringLiteral()) {
                validations.add(StringValidation.INSTANCE);
            }
            if (JsonSchemaObjectReadingUtils.hasArrayChecks(schemaNode) && value.isArray()) {
                validations.add(Array2020Validator.INSTANCE);
            }
            if (JsonSchemaValidationsCollector.hasMinMaxLengthChecks(schemaNode)) {
                if (value.isStringLiteral()) {
                    validations.add(StringValidation.INSTANCE);
                }
                else if (value.isArray()) {
                    validations.add(Array2020Validator.INSTANCE);
                }
            }
            if (JsonSchemaObjectReadingUtils.hasObjectChecks(schemaNode) && value.isObject()) {
                validations.add(ObjectValidation.INSTANCE);
            }
        }

        if (schemaNode.getNot() != null) {
            validations.add(NotValidation.INSTANCE);
        }
        return validations.build();
    }

    private Stream<JsonSchemaValidation> getTypeValidations(JsonSchemaType type) {
        Stream.Builder<JsonSchemaValidation> validations = Stream.builder();
        validations.add(TypeValidation.INSTANCE);
        switch (type) {
            case _string_number -> {
                validations.add(NumericValidation.INSTANCE);
                validations.add(StringValidation.INSTANCE);
            }
            case _number, _integer -> validations.add(NumericValidation.INSTANCE);
            case _string -> validations.add(StringValidation.INSTANCE);
            case _array -> validations.add(Array2020Validator.INSTANCE);
            case _object -> validations.add(ObjectValidation.INSTANCE);
            default -> {
            }
        }
        return validations.build();
    }

    @Override
    public String toString() {
        return "JsonSchema202012Strategy";
    }
}
