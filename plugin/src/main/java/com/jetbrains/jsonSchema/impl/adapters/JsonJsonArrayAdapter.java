// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.adapters;

import com.intellij.json.psi.JsonArray;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public final class JsonJsonArrayAdapter implements JsonArrayValueAdapter {
  private final @Nonnull JsonArray myArray;

  public JsonJsonArrayAdapter(@Nonnull JsonArray array) {myArray = array;}

  @Override
  public boolean isObject() {
    return false;
  }

  @Override
  public boolean isArray() {
    return true;
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
    return myArray;
  }

  @Override
  public @Nullable JsonObjectValueAdapter getAsObject() {
    return null;
  }

  @Override
  public @Nullable JsonArrayValueAdapter getAsArray() {
    return this;
  }

  @Override
  public @Nonnull List<JsonValueAdapter> getElements() {
    return myArray.getValueList().stream().filter(e -> e != null).map(e -> JsonJsonPropertyAdapter.createAdapterByType(e)).collect(
      Collectors.toList());
  }
}
