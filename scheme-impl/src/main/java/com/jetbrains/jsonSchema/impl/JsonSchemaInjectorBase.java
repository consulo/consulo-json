// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.lang.injection.general.Injection;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collections;
import java.util.List;

public abstract class JsonSchemaInjectorBase implements MultiHostInjector {
  public static final class InjectedLanguageData implements Injection {
    InjectedLanguageData(@Nonnull Language language, @Nullable String prefix, @Nullable String postfix) {
      this.language = language;
      this.prefix = prefix;
      this.postfix = postfix;
    }

    public @Nonnull Language language;
    public @Nullable String prefix;
    public @Nullable String postfix;

    @Override
    public @Nonnull String getInjectedLanguageId() {
      return language.getID();
    }

    @Override
    public @Nonnull Language getInjectedLanguage() {
      return language;
    }

    @Override
    public @Nonnull String getPrefix() {
      return StringUtil.notNullize(prefix) ;
    }

    @Override
    public @Nonnull String getSuffix() {
      return StringUtil.notNullize(postfix) ;
    }

    @Override
    public @Nullable String getSupportId() {
      return null;
    }
  }

  protected static void injectForHost(@Nonnull MultiHostRegistrar registrar, @Nonnull JsonStringLiteral host, @SuppressWarnings("SameParameterValue") @Nonnull Language language) {
    injectForHost(registrar, host, new InjectedLanguageData(language, null, null));
  }

  protected static void injectForHost(@Nonnull MultiHostRegistrar registrar, @Nonnull JsonStringLiteral host, @Nonnull InjectedLanguageData language) {
    List<Pair<TextRange, String>> fragments = host.getTextFragments();
    if (fragments.isEmpty()) return;
    registrar.startInjecting(language.language);
    for (Pair<TextRange, String> fragment : fragments) {
      registrar.addPlace(language.prefix, language.postfix, (PsiLanguageInjectionHost)host, fragment.first);
    }
    registrar.doneInjecting();
  }

  @Override
  public @Nonnull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
    return Collections.singletonList(JsonStringLiteral.class);
  }
}
