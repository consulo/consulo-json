// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import consulo.logging.Logger;

public final class JsonSchemaObjectRendering {
  private static final Logger LOG = Logger.getInstance(JsonSchemaObjectRendering.class);

  private JsonSchemaObjectRendering() {}

  public static String renderSchemaNode(JsonSchemaObject schemaNode, JsonSchemaObjectRenderingLanguage language) {
    JsonNode mappedNode;
    if (schemaNode instanceof JsonSchemaObjectBackedByJacksonBase) {
      mappedNode = ((JsonSchemaObjectBackedByJacksonBase)schemaNode).getRawSchemaNode();
    }
    else {
      LOG.warn("Unsupported JsonSchemaObject implementation provided: " + schemaNode.getClass().getSimpleName());
      return schemaNode.toString();
    }

    ObjectMapper mapper;
    switch (language) {
      case JSON:
        mapper = JsonSchemaObjectStorageKt.getJson5ObjectMapper();
        break;
      case YAML:
        mapper = JsonSchemaObjectStorageKt.getYamlObjectMapper();
        break;
      default:
        throw new IllegalArgumentException("Unknown language: " + language);
    }

    return serializeJsonNodeSafe(mappedNode, mapper);
  }

  private static String serializeJsonNodeSafe(JsonNode jsonNode, ObjectMapper serializer) {
    try {
      return serializer.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode).trim();
    }
    catch (Exception exception) {
      LOG.warn("Error during JsonSchemaObjectSerialization", exception);
      return "";
    }
  }

  public enum JsonSchemaObjectRenderingLanguage {
    JSON, YAML
  }

  // Utility method used by Kotlin code
  public static JsonSchemaObject inheritBaseSchemaIfNeeded(JsonSchemaObject baseSchema, JsonSchemaObject childSchema) {
    if (baseSchema instanceof RootJsonSchemaObjectBackedByJackson) {
      return ((RootJsonSchemaObjectBackedByJackson)baseSchema).getSchemaInterpretationStrategy()
        .inheritBaseSchema(baseSchema, childSchema);
    }
    if (baseSchema instanceof JsonSchemaObjectBackedByJackson) {
      return ((JsonSchemaObjectBackedByJackson)baseSchema).getRootSchemaObject()
        .getSchemaInterpretationStrategy()
        .inheritBaseSchema(baseSchema, childSchema);
    }
    // Fallback for unknown implementations
    return childSchema;
  }
}
