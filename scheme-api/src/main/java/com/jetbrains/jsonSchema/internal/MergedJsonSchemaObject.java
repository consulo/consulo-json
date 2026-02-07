// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import jakarta.annotation.Nonnull;

public interface MergedJsonSchemaObject {
  @Nonnull
  JsonSchemaObject getBase();

  @Nonnull
  JsonSchemaObject getOther();

  default boolean isInherited() {
    JsonSchemaObject baseRef = getBase();
    JsonSchemaObject otherRef = getOther();

    if (this instanceof InheritedJsonSchemaObjectView ||
        baseRef instanceof InheritedJsonSchemaObjectView ||
        otherRef instanceof InheritedJsonSchemaObjectView) {
      return true;
    }

    if (baseRef instanceof MergedJsonSchemaObject && ((MergedJsonSchemaObject) baseRef).isInherited()) {
      return true;
    }

    if (otherRef instanceof MergedJsonSchemaObject && ((MergedJsonSchemaObject) otherRef).isInherited()) {
      return true;
    }

    return false;
  }
}
