// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import jakarta.annotation.Nullable;

public interface JsonSchemaRefResolver {
  @Nullable
  JsonSchemaObject resolve(String reference, JsonSchemaObjectBackedByJacksonBase referenceOwner, JsonSchemaService service);
}

