// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema.impl.light.legacy;

import tools.jackson.databind.JsonNode;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.DESCRIPTION;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.X_INTELLIJ_ENUM_METADATA;

public final class SchemaImplOldHacks {
    private static final Set<String> FIELD_NAMES_OLD_PARSER_IS_AWARE_OF = Set.of(
        "$anchor", "$id", "id", "$schema", "description", "deprecationMessage", "x-intellij-html-description",
        "x-intellij-language-injection", "x-intellij-case-insensitive", "x-intellij-enum-metadata", "title", "$ref",
        "$recursiveRef", "$recursiveAnchor", "default", "example", "format", "definitions", "$defs", "properties",
        "items", "multipleOf", "maximum", "minimum", "exclusiveMaximum", "exclusiveMinimum", "maxLength", "minLength",
        "pattern", "additionalItems", "contains", "maxItems", "minItems", "uniqueItems", "maxProperties", "minProperties",
        "required", "additionalProperties", "propertyNames", "patternProperties", "dependencies", "enum",
        "const", "type", "allOf", "anyOf", "oneOf", "not", "if", "then", "else", "instanceof", "typeof"
    );

    private SchemaImplOldHacks() {
    }

    public static boolean isOldParserAwareOfFieldName(String fieldName) {
        return FIELD_NAMES_OLD_PARSER_IS_AWARE_OF.contains(fieldName);
    }

    // Old code did not have any tests for this method, so the updated implementation might have mistakes.
    // Consider adding a test if you know something about 'x-intellij-enum-metadata'
    public static @Nullable Map<String, Map<String, String>> tryReadEnumMetadata(JsonSchemaObjectBackedByJacksonBase schemaObject) {
        JsonNode metadataNode = schemaObject.getRawSchemaNode().get(X_INTELLIJ_ENUM_METADATA);
        if (metadataNode == null) {
            return null;
        }

        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : metadataNode.properties()) {
            String name = entry.getKey();
            JsonNode valueNode = entry.getValue();
            if (valueNode.isString()) {
                result.put(name, Map.of(DESCRIPTION, valueNode.stringValue()));
            }
            else if (valueNode.isObject()) {
                Map<String, String> fields = new LinkedHashMap<>();
                for (Map.Entry<String, JsonNode> field : valueNode.properties()) {
                    if (field.getValue().isString()) {
                        fields.put(field.getKey(), field.getValue().stringValue());
                    }
                }
                result.put(name, fields);
            }
        }
        return result;
    }
}
