// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase;
import jakarta.annotation.Nullable;

public interface JsonSchemaRefResolver {
  @Nullable
  JsonSchemaObject resolve(String reference, JsonSchemaObjectBackedByJacksonBase referenceOwner, JsonSchemaService service);
}

