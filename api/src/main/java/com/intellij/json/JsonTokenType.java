// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import consulo.language.ast.IElementType;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;

public final class JsonTokenType extends IElementType {
  public JsonTokenType(@Nonnull @NonNls String debugName) {
    super(debugName, JsonLanguage.INSTANCE);
  }
}
