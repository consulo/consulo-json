// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import jakarta.annotation.Nonnull;

public interface JsonSchemaNodePointer<T> {
    @Nonnull
    T getRawSchemaNode();
}