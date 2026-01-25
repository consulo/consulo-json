// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.json5;

import com.intellij.json.JsonDialectUtil;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalkerFactory;
import com.jetbrains.jsonSchema.impl.JsonOriginalPsiWalker;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public final class Json5PsiWalkerFactory implements JsonLikePsiWalkerFactory {
  public static final JsonLikePsiWalker WALKER_INSTANCE = new JsonOriginalPsiWalker() {
    @Override
    public boolean requiresNameQuotes() {
      return false;
    }

    @Override
    public boolean allowsSingleQuotes() {
      return true;
    }
  };

  @Override
  public boolean handles(@Nonnull PsiElement element) {
    PsiElement parent = element.getParent();
    return parent != null && JsonDialectUtil.getLanguageOrDefaultJson(parent) == Json5Language.INSTANCE;
  }

  @Override
  public @Nonnull JsonLikePsiWalker create(@Nullable JsonSchemaObject schemaObject) {
    return WALKER_INSTANCE;
  }
}
