// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.legacy;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.internal.JsonSchemaObjectImpl;
import jakarta.annotation.Nonnull;

@Deprecated
public class LegacyMutableJsonSchemaObjectMerger implements JsonSchemaObjectMerger {
  @Override
  public @Nonnull JsonSchemaObject mergeObjects(@Nonnull JsonSchemaObject base, @Nonnull JsonSchemaObject other, @Nonnull JsonSchemaObject pointTo) {
    return JsonSchemaObjectImpl.merge(((JsonSchemaObjectImpl)base), ((JsonSchemaObjectImpl)other), ((JsonSchemaObjectImpl)pointTo));
  }
}
