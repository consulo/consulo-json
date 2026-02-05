// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.annotation.Nonnull;

public class JsonSchemaObjectBackedByJackson extends JsonSchemaObjectBackedByJacksonBase {
  private final RootJsonSchemaObjectBackedByJackson rootObject;

  public JsonSchemaObjectBackedByJackson(@Nonnull RootJsonSchemaObjectBackedByJackson rootObject,
                                         @Nonnull JsonNode rawSchemaNode,
                                         @Nonnull String jsonPointer) {
    super(rawSchemaNode, jsonPointer);
    this.rootObject = rootObject;
  }

  @Nonnull
  @Override
  public RootJsonSchemaObjectBackedByJackson getRootSchemaObject() {
    return rootObject;
  }
}
