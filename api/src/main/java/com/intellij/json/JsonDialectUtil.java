// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.util.lang.ObjectUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JsonDialectUtil {
  public static boolean isStandardJson(@NotNull PsiElement element) {
    return isStandardJson(getLanguageOrDefaultJson(element));
  }

  public static @NotNull Language getLanguageOrDefaultJson(@NotNull PsiElement element) {
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
