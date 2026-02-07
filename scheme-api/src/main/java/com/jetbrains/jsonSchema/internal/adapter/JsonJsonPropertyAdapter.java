// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal.adapter;

import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.jetbrains.jsonSchema.extension.adapter.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class JsonJsonPropertyAdapter implements JsonPropertyAdapter {
  private final @Nonnull JsonProperty myProperty;

  public JsonJsonPropertyAdapter(@Nonnull JsonProperty property) {
    myProperty = property;
  }

  @Override
  public @Nullable String getName() {
    return myProperty.getName();
  }

  @Override
  public @Nonnull Collection<JsonValueAdapter> getValues() {
    return myProperty.getValue() == null ? List.of() : Collections.singletonList(createAdapterByType(myProperty.getValue()));
  }

  @Override
  public @Nullable JsonValueAdapter getNameValueAdapter() {
    return createAdapterByType(myProperty.getNameElement());
  }

  @Override
  public @Nonnull PsiElement getDelegate() {
    return myProperty;
  }

  @Override
  public @Nullable JsonObjectValueAdapter getParentObject() {
    return myProperty.getParent() instanceof JsonObject ? new JsonJsonObjectAdapter((JsonObject)myProperty.getParent()) : null;
  }

  public static @Nonnull JsonValueAdapter createAdapterByType(@Nonnull JsonValue value) {
    if (value instanceof JsonObject) return new JsonJsonObjectAdapter((JsonObject)value);
    if (value instanceof JsonArray) return new JsonJsonArrayAdapter((JsonArray)value);
    return new JsonJsonGenericValueAdapter(value);
  }
}
