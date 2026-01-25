// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import jakarta.annotation.Nonnull;

import java.util.Arrays;
import java.util.stream.Collectors;

public final class EnumArrayValueWrapper {
  private final Object @Nonnull [] myValues;

  public EnumArrayValueWrapper(Object @Nonnull [] values) {
    myValues = values;
  }

  public Object @Nonnull [] getValues() {
    return myValues;
  }

  @Override
  public String toString() {
    return "[" + Arrays.stream(myValues).map(v -> v.toString()).collect(Collectors.joining(", ")) + "]";
  }
}
