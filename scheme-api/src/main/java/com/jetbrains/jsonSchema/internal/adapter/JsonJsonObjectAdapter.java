// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal.adapter;

import com.intellij.json.psi.JsonObject;
import com.jetbrains.jsonSchema.extension.adapter.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonPropertyAdapter;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public final class JsonJsonObjectAdapter implements JsonObjectValueAdapter {
  private final @Nonnull JsonObject myValue;

  public JsonJsonObjectAdapter(@Nonnull JsonObject value) {myValue = value;}

  @Override
  public boolean isObject() {
    return true;
  }

  @Override
  public boolean isArray() {
    return false;
  }

  @Override
  public boolean isStringLiteral() {
    return false;
  }

  @Override
  public boolean isNumberLiteral() {
    return false;
  }

  @Override
  public boolean isBooleanLiteral() {
    return false;
  }

  @Override
  public @Nonnull PsiElement getDelegate() {
    return myValue;
  }

  @Override
  public @Nullable JsonObjectValueAdapter getAsObject() {
    return this;
  }

  @Override
  public @Nullable JsonArrayValueAdapter getAsArray() {
    return null;
  }

  @Override
  public @Nonnull List<JsonPropertyAdapter> getPropertyList() {
    return myValue.getPropertyList().stream().filter(p -> p != null)
      .map(p -> new JsonJsonPropertyAdapter(p)).collect(Collectors.toList());
  }
}
