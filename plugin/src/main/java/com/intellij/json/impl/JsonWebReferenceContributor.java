// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl;

import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import consulo.document.util.TextRange;
import consulo.language.impl.psi.path.WebReference;
import consulo.language.psi.*;
import consulo.language.util.ProcessingContext;
import jakarta.annotation.Nonnull;

import static consulo.language.pattern.PlatformPatterns.psiElement;

final class JsonWebReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(@Nonnull PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(
      psiElement(JsonStringLiteral.class),
      new PsiReferenceProvider() {
        @Override
        public boolean acceptsTarget(@Nonnull PsiElement target) {
          return false; // web references do not point to any real PsiElement
        }

        @Override
        public PsiReference @Nonnull [] getReferencesByElement(@Nonnull PsiElement element, @Nonnull ProcessingContext context) {
          if (!(element instanceof JsonStringLiteral stringLiteral)) return PsiReference.EMPTY_ARRAY;

          PsiElement parent = stringLiteral.getParent();
          if (!(parent instanceof JsonProperty)) return PsiReference.EMPTY_ARRAY;

          JsonValue jsonValueElement = ((JsonProperty)parent).getValue();
          if (element != jsonValueElement) return PsiReference.EMPTY_ARRAY;

          if (element.getTextLength() > 1000) return PsiReference.EMPTY_ARRAY; // JSON may be used as data format for huge strings
          if (!element.textContains(':')) return PsiReference.EMPTY_ARRAY;

          String textValue = stringLiteral.getValue();

          if (GlobalPathReferenceProvider.isWebReferenceUrl(textValue)) {
            TextRange valueTextRange = ElementManipulators.getValueTextRange(stringLiteral);
            if (valueTextRange.isEmpty()) return PsiReference.EMPTY_ARRAY;

            return new PsiReference[]{new WebReference(element, valueTextRange, textValue)};
          }

          return PsiReference.EMPTY_ARRAY;
        }
      },
      PsiReferenceRegistrar.LOWER_PRIORITY
    );
  }
}
