// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static com.jetbrains.jsonSchema.internal.SchemaKeywords.DESCRIPTION;
import static com.jetbrains.jsonSchema.internal.SchemaKeywords.X_INTELLIJ_ENUM_METADATA;

public class SchemaImplOldHacks {
  private static final Set<String> FIELD_NAMES_OLD_PARSER_IS_AWARE_OF = Set.of(
    "$anchor", "$id", "id", "$schema", "description", "deprecationMessage", "x-intellij-html-description",
    "x-intellij-language-injection", "x-intellij-case-insensitive", "x-intellij-enum-metadata", "title", "$ref",
    "$recursiveRef", "$recursiveAnchor", "default", "example", "format", "definitions", "$defs", "properties",
    "items", "multipleOf", "maximum", "minimum", "exclusiveMaximum", "exclusiveMinimum", "maxLength", "minLength",
    "pattern", "additionalItems", "contains", "maxItems", "minItems", "uniqueItems", "maxProperties", "minProperties",
    "required", "additionalProperties", "propertyNames", "patternProperties", "dependencies", "enum",
    "const", "type", "allOf", "anyOf", "oneOf", "not", "if", "then", "else", "instanceof", "typeof"
  );

  public static boolean isOldParserAwareOfFieldName(String fieldName) {
    return FIELD_NAMES_OLD_PARSER_IS_AWARE_OF.contains(fieldName);
  }

  // Old code did not have any tests for this method, so the updated implementation might have mistakes.
  // Consider adding a test if you know something about 'x-intellij-enum-metadata'
  @Nullable
  public static Map<String, Map<String, String>> tryReadEnumMetadata(JsonSchemaObjectBackedByJacksonBase schema) {
    Iterator<Map.Entry<String, JsonNode>> properties = schema.getRawSchemaNode().fields();
    JsonNode metadataNode = null;
    while (properties.hasNext()) {
      Map.Entry<String, JsonNode> entry = properties.next();
      if (X_INTELLIJ_ENUM_METADATA.equals(entry.getKey())) {
        metadataNode = entry.getValue();
        break;
      }
    }

    if (metadataNode == null) {
      return null;
    }

    Map<String, Map<String, String>> result = new HashMap<>();
    Iterator<Map.Entry<String, JsonNode>> metadataEntries = metadataNode.fields();
    while (metadataEntries.hasNext()) {
      Map.Entry<String, JsonNode> entry = metadataEntries.next();
      String name = entry.getKey();
      JsonNode valueNode = entry.getValue();

      if (valueNode.isTextual()) {
        Map<String, String> innerMap = new HashMap<>();
        innerMap.put(DESCRIPTION, valueNode.asText());
        result.put(name, innerMap);
      } else if (valueNode.isObject()) {
        Map<String, String> innerMap = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fieldEntries = valueNode.fields();
        while (fieldEntries.hasNext()) {
          Map.Entry<String, JsonNode> fieldEntry = fieldEntries.next();
          if (fieldEntry.getValue().isTextual()) {
            innerMap.put(fieldEntry.getKey(), fieldEntry.getValue().asText());
          }
        }
        result.put(name, innerMap);
      }
    }

    return result.isEmpty() ? null : result;
  }
}
