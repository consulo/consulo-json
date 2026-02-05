// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.jsonSchema.impl.light.nodes.JacksonSchemaNodeAccessor;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class JsonSchemaTraversalUtils {
  private static final Logger LOG = Logger.getInstance(JsonSchemaTraversalUtils.class);

  @Nullable
  public static String getChildAsText(JsonSchemaObject schemaObject, String... relativeChildPath) {
    if (schemaObject instanceof JsonSchemaObjectBackedByJacksonBase) {
      JsonSchemaObjectBackedByJacksonBase jacksonBacked = (JsonSchemaObjectBackedByJacksonBase) schemaObject;
      JsonNode rawChildNode = jacksonBacked.getRawSchemaNode();

      for (String childName : relativeChildPath) {
        rawChildNode = JacksonSchemaNodeAccessor.resolveRelativeNode(rawChildNode, childName);
        if (rawChildNode == null) {
          rawChildNode = MissingNode.getInstance();
          break;
        }
      }

      return JacksonSchemaNodeAccessor.readTextNodeValue(rawChildNode);
    } else {
      LOG.warn("JSON schema traverser does not provide support for " + schemaObject.getClass().getSimpleName());
      return null;
    }
  }
}
