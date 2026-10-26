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
package com.jetbrains.jsonSchema.impl.light.versions;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.light.ExternalSchemaRefLoader;
import com.jetbrains.jsonSchema.impl.light.InFileSchemaRefLoader;
import com.jetbrains.jsonSchema.impl.light.JsonSchemaRefLoader;
import com.jetbrains.jsonSchema.impl.validations.JsonSchemaValidationsCollector;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.ADDITIONAL_ITEMS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.DEPENDENCIES;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.ITEMS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.JSON_DEFINITIONS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.JSON_ID;

public final class JsonSchema6AndEarlierStrategy implements JsonSchemaInterpretationStrategy {
    public static final JsonSchema6AndEarlierStrategy INSTANCE = new JsonSchema6AndEarlierStrategy();

    private static final List<JsonSchemaRefLoader> REF_LOADERS = List.of(InFileSchemaRefLoader.INSTANCE, ExternalSchemaRefLoader.INSTANCE);

    private JsonSchema6AndEarlierStrategy() {
    }

    @Override
    public String getIdKeyword() {
        return JSON_ID;
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
        return JSON_DEFINITIONS;
    }

    @Override
    public @Nullable String getDynamicReferenceKeyword() {
        return null;
    }

    @Override
    public @Nullable String getDynamicAnchorKeyword() {
        return null;
    }

    @Override
    public String getDependencySchemasKeyword() {
        return DEPENDENCIES;
    }

    @Override
    public String getPropertyDependenciesKeyword() {
        return DEPENDENCIES;
    }

    @Override
    public @Nullable String getUnevaluatedItemsKeyword() {
        return null;
    }

    @Override
    public @Nullable String getUnevaluatedPropertiesKeyword() {
        return null;
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
        return "JsonSchema6AndEarlierStrategy";
    }
}
