// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.light.JsonSchemaNodePointer;
import com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public interface RootJsonSchemaObject<T, V extends JsonSchemaObject & JsonSchemaNodePointer<T>> {
  @Nullable
  JsonSchemaObject getChildSchemaObjectByName(@Nonnull V parentSchemaObject, @Nonnull String... childNodeRelativePointer);

  @Nullable
  JsonSchemaObject getSchemaObjectByAbsoluteJsonPointer(@Nonnull String jsonPointer);

  @Nullable
  String resolveId(@Nonnull String id);

  @Nullable
  String resolveDynamicAnchor(@Nonnull String anchor);

  @Nonnull
  JsonSchemaInterpretationStrategy getSchemaInterpretationStrategy();
}
