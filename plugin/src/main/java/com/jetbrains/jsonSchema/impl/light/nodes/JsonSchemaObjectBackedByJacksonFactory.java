// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes;

import com.fasterxml.jackson.databind.JsonNode;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaReader;
import com.jetbrains.jsonSchema.impl.light.JsonSchemaObjectFactory;
import com.jetbrains.jsonSchema.impl.light.SchemaKeywords;
import consulo.application.util.ConcurrentFactoryMap;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Map;

import static com.jetbrains.jsonSchema.impl.light.nodes.JacksonSchemaNodeAccessor.escapeForbiddenJsonPointerSymbols;

class JsonSchemaObjectBackedByJacksonFactory implements JsonSchemaObjectFactory<JsonNode, JsonSchemaObjectBackedByJacksonBase> {

  private final RootJsonSchemaObjectBackedByJackson rootSchemaObject;
  private final Map<String, JsonSchemaObjectBackedByJacksonBase> registeredChildren;

  public JsonSchemaObjectBackedByJacksonFactory(@Nonnull RootJsonSchemaObjectBackedByJackson rootSchemaObject) {
    this.rootSchemaObject = rootSchemaObject;
    this.registeredChildren = ConcurrentFactoryMap.createMap(this::computeSchemaObjectByPointer);
  }

  @Override
  @Nullable
  public JsonSchemaObject getSchemaObjectByAbsoluteJsonPointer(@Nonnull String jsonPointer) {
    JsonSchemaObjectBackedByJacksonBase result = registeredChildren.get(jsonPointer);
    return result instanceof MissingJsonSchemaObject ? null : result;
  }

  @Override
  @Nullable
  public JsonSchemaObjectBackedByJacksonBase getChildSchemaObjectByName(@Nonnull JsonSchemaObjectBackedByJacksonBase parentSchemaObject,
                                                                         @Nonnull String... childNodeRelativePointer) {
    if (childNodeRelativePointer.length == 0) return parentSchemaObject;

    String childAbsolutePointer = computeAbsoluteJsonPointer(parentSchemaObject.getPointer(), childNodeRelativePointer);

    if (isRootObjectPointer(childAbsolutePointer)) {
      return rootSchemaObject;
    } else {
      JsonSchemaObjectBackedByJacksonBase result = registeredChildren.get(childAbsolutePointer);
      return result instanceof MissingJsonSchemaObject ? null : result;
    }
  }

  @Nonnull
  private JsonSchemaObjectBackedByJacksonBase computeSchemaObjectByPointer(@Nonnull String jsonPointer) {
    JsonNode resolvedRelativeChildSchemaNode = JacksonSchemaNodeAccessor.INSTANCE.resolveNode(rootSchemaObject.getRawSchemaNode(), jsonPointer);

    if (resolvedRelativeChildSchemaNode == null
        || resolvedRelativeChildSchemaNode.isMissingNode()
        || resolvedRelativeChildSchemaNode.isArray()) {
      return MissingJsonSchemaObject.INSTANCE;
    } else {
      return new JsonSchemaObjectBackedByJackson(rootSchemaObject, resolvedRelativeChildSchemaNode, jsonPointer);
    }
  }

  @Nonnull
  private String computeAbsoluteJsonPointer(@Nonnull String basePointer, @Nonnull String... relativePointer) {
    StringBuilder joinedPointer = new StringBuilder();
    for (int i = 0; i < relativePointer.length; i++) {
      if (i > 0) joinedPointer.append("/");
      joinedPointer.append(escapeForbiddenJsonPointerSymbols(relativePointer[i]));
    }
    return JsonSchemaReader.getNewPointer(joinedPointer.toString(), basePointer);
  }

  private boolean isRootObjectPointer(@Nonnull String jsonPointer) {
    return SchemaKeywords.ROOT_POINTER_VARIANTS.contains(jsonPointer);
  }
}
