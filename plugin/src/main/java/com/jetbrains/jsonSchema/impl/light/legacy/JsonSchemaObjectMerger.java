// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.legacy;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.light.nodes.LightweightJsonSchemaObjectMerger;
import consulo.application.util.registry.Registry;
import jakarta.annotation.Nonnull;

@Deprecated
public interface JsonSchemaObjectMerger {
  @Nonnull
  JsonSchemaObject mergeObjects(@Nonnull JsonSchemaObject base,
                                @Nonnull JsonSchemaObject other,
                                @Nonnull JsonSchemaObject pointTo);

  @Nonnull
  static JsonSchemaObjectMerger getJsonSchemaObjectMerger() {
    if (Registry.is("json.schema.object.v2")) {
      return LightweightJsonSchemaObjectMerger.INSTANCE;
    } else {
      return new LegacyMutableJsonSchemaObjectMerger();
    }
  }
}
