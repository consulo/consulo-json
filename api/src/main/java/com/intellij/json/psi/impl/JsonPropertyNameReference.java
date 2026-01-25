// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.psi.impl;

import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import consulo.document.util.TextRange;
import consulo.language.psi.ElementManipulators;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.util.IncorrectOperationException;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Mikhail Golubev
 */
public class JsonPropertyNameReference implements PsiReference {
  private final JsonProperty myProperty;

  public JsonPropertyNameReference(@Nonnull JsonProperty property) {
    myProperty = property;
  }

  @Override
  public @Nonnull PsiElement getElement() {
    return myProperty;
  }

  @Override
  public @Nonnull TextRange getRangeInElement() {
    final JsonValue nameElement = myProperty.getNameElement();
    // Either value of string with quotes stripped or element's text as is
    return ElementManipulators.getValueTextRange(nameElement);
  }

  @Override
  public @Nullable PsiElement resolve() {
    return myProperty;
  }

  @Override
  public @Nonnull String getCanonicalText() {
    return myProperty.getName();
  }

  @Override
  public PsiElement handleElementRename(@Nonnull String newElementName) throws IncorrectOperationException {
    return myProperty.setName(newElementName);
  }

  @Override
  public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
    return null;
  }

  @Override
  public boolean isReferenceTo(@Nonnull PsiElement element) {
    if (!(element instanceof JsonProperty otherProperty)) {
      return false;
    }
    // May reference to the property with the same name for compatibility with JavaScript JSON support
    final PsiElement selfResolve = resolve();
    return otherProperty.getName().equals(getCanonicalText()) && selfResolve != otherProperty;
  }

  @Override
  public boolean isSoft() {
    return true;
  }
}
