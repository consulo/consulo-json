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
package com.jetbrains.jsonSchema.impl.light.versions.v201909;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.light.ExternalSchemaRefLoader;
import com.jetbrains.jsonSchema.impl.light.InFileSchemaRefLoader;
import com.jetbrains.jsonSchema.impl.light.JsonSchemaRefLoader;
import com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy;
import com.jetbrains.jsonSchema.impl.validations.JsonSchemaValidationsCollector;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.ADDITIONAL_ITEMS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.DEFS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.DEPENDENT_REQUIRED;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.DEPENDENT_SCHEMAS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.ITEMS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.JSON_DOLLAR_ID;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.RECURSIVE_ANCHOR;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.RECURSIVE_REF;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.UNEVALUATED_ITEMS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.UNEVALUATED_PROPERTIES;

public final class JsonSchema201909Strategy implements JsonSchemaInterpretationStrategy {
    public static final JsonSchema201909Strategy INSTANCE = new JsonSchema201909Strategy();

    private static final List<JsonSchemaRefLoader> REF_LOADERS = List.of(
        RecursiveSchemaRefLoader.INSTANCE,
        InFileSchemaRefLoader.INSTANCE,
        ExternalSchemaRefLoader.INSTANCE,
        Vocabulary2019RefLoader.INSTANCE
    );

    private JsonSchema201909Strategy() {
    }

    @Override
    public String getIdKeyword() {
        return JSON_DOLLAR_ID;
    }

    @Override
    public String getNonPositionalItemsKeyword() {
        return ADDITIONAL_ITEMS;
    }

    @Override
    public String getPositionalItemsKeyword() {
        return ITEMS;
    }

    @Override
    public String getDefinitionsKeyword() {
        return DEFS;
    }

    @Override
    public String getDynamicReferenceKeyword() {
        return RECURSIVE_REF;
    }

    @Override
    public String getDynamicAnchorKeyword() {
        return RECURSIVE_ANCHOR;
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
        return JsonSchemaValidationsCollector.getSchema7AndEarlierValidations(schemaNode, type, value);
    }

    @Override
    public String toString() {
        return "JsonSchema201909Strategy";
    }
}
