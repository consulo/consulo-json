// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import jakarta.annotation.Nonnull;

import java.util.Map;
import java.util.stream.Collectors;

public final class EnumObjectValueWrapper {
  private final @Nonnull Map<String, Object> myValues;

  public EnumObjectValueWrapper(@Nonnull Map<String, Object> values) {
    myValues = values;
  }

  public @Nonnull Map<String, Object> getValues() {
    return myValues;
  }

  @Override
  public String toString() {
    return "{" + myValues.entrySet().stream().map(v -> "\"" + v.getKey() + "\": " + v.getValue()).collect(Collectors.joining(", ")) + "}";
  }
}
