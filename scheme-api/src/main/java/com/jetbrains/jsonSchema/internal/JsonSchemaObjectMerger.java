// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import com.intellij.json.internal.JsonRegistry;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import jakarta.annotation.Nonnull;

@Deprecated
public interface JsonSchemaObjectMerger {
  @Nonnull
  JsonSchemaObject mergeObjects(@Nonnull JsonSchemaObject base,
                                @Nonnull JsonSchemaObject other,
                                @Nonnull JsonSchemaObject pointTo);

  @Nonnull
  static JsonSchemaObjectMerger getJsonSchemaObjectMerger() {
    if (JsonRegistry.JSON_SCHEME_OBJECT_V2) {
      return LightweightJsonSchemaObjectMerger.INSTANCE;
    } else {
      return new LegacyMutableJsonSchemaObjectMerger();
    }
  }
}
