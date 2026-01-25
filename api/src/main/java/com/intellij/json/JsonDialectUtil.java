// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public final class JsonDialectUtil {
  public static boolean isStandardJson(@Nonnull PsiElement element) {
    return isStandardJson(getLanguageOrDefaultJson(element));
  }

  public static @Nonnull Language getLanguageOrDefaultJson(@Nonnull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file != null) {
      Language language = file.getLanguage();
      if (language instanceof JsonLanguage) return language;
    }
    return ObjectUtil.coalesce(ObjectUtil.tryCast(element.getLanguage(), JsonLanguage.class), JsonLanguage.INSTANCE);
  }

  public static boolean isStandardJson(@Nullable Language language) {
    return language == JsonLanguage.INSTANCE;
  }
}
