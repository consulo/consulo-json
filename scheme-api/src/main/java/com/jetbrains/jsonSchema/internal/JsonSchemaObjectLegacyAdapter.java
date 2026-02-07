// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import jakarta.annotation.Nullable;

import java.util.Map;
import java.util.Set;

@Deprecated
public abstract class JsonSchemaObjectLegacyAdapter extends JsonSchemaObject {

  @Override
  @Nullable
  public JsonSchemaType mergeTypes(@Nullable JsonSchemaType selfType,
                                   @Nullable JsonSchemaType otherType,
                                   @Nullable Set<JsonSchemaType> otherTypeVariants) {
    throw new UnsupportedOperationException("Do not use!");
  }

  @Override
  public Set<JsonSchemaType> mergeTypeVariantSets(@Nullable Set<JsonSchemaType> self,
                                                   @Nullable Set<JsonSchemaType> other) {
    throw new UnsupportedOperationException("Do not use!");
  }

  @Override
  public void mergeValues(JsonSchemaObject other) {
    throw new UnsupportedOperationException("Do not use!");
  }

  @Override
  @Nullable
  public JsonSchemaObject getBackReference() {
    throw new UnsupportedOperationException("Do not use!");
  }

  @Override
  @Nullable
  public Map<String, ? extends JsonSchemaObject> getDefinitionsMap() {
    throw new UnsupportedOperationException("Use getDefinitionByName()");
  }

  @Override
  public Map<String, ? extends JsonSchemaObject> getProperties() {
    throw new UnsupportedOperationException("Use getPropertyByName()");
  }

  @Override
  @Nullable
  public Map<String, Object> getExample() {
    throw new UnsupportedOperationException("Use getExampleByName()");
  }
}
