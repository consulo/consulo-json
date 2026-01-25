// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.findUsages;

import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonProperty;
import consulo.annotation.component.ExtensionImpl;
import consulo.json.localize.JsonLocalize;
import consulo.language.Language;
import consulo.language.cacheBuilder.WordsScanner;
import consulo.language.findUsage.FindUsagesProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ExtensionImpl
public final class JsonFindUsagesProvider implements FindUsagesProvider {
    @Override
    public @Nullable WordsScanner getWordsScanner() {
        return new JsonWordScanner();
    }

    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement psiElement) {
        return psiElement instanceof PsiNamedElement;
    }

    @Override
    public @NotNull String getType(@NotNull PsiElement element) {
        if (element instanceof JsonProperty) {
            return JsonLocalize.jsonProperty().get();
        }
        return "";
    }

    @Override
    public @NotNull String getDescriptiveName(@NotNull PsiElement element) {
        final String name = element instanceof PsiNamedElement ? ((PsiNamedElement) element).getName() : null;
        return name != null ? name : JsonLocalize.unnamedDesc().get();
    }

    @Override
    public @NotNull String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        return getDescriptiveName(element);
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return JsonLanguage.INSTANCE;
    }
}
