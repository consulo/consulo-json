// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.versions.v201909;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.light.JsonSchemaRefResolver;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase;
import org.jetbrains.annotations.Nullable;

public class RecursiveRefResolver implements JsonSchemaRefResolver {
  public static final RecursiveRefResolver INSTANCE = new RecursiveRefResolver();

  private RecursiveRefResolver() {}

  @Nullable
  @Override
  public JsonSchemaObject resolve(String reference, JsonSchemaObjectBackedByJacksonBase referenceOwner, JsonSchemaService service) {
    return "#".equals(reference) ? referenceOwner.getRootSchemaObject() : null;
  }
}
