// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import consulo.annotation.internal.MigratedExtensionsTo;
import consulo.json.localize.JsonLocalize;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

@Deprecated
@MigratedExtensionsTo(JsonLocalize.class)
public final class JsonBundle {
  public static final @NonNls String BUNDLE = "messages.JsonBundle";

  private JsonBundle() {
  }

  public static @Nonnull @Nls String message(@Nonnull @PropertyKey(resourceBundle = BUNDLE) String key, Object ... params) {
    return key;
  }
}