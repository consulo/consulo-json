// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.psi.impl;

import com.intellij.json.psi.JsonElementVisitor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiRecursiveVisitor;
import jakarta.annotation.Nonnull;

/**
 * @author Mikhail Golubev
 */
public class JsonRecursiveElementVisitor extends JsonElementVisitor implements PsiRecursiveVisitor {

  @Override
  public void visitElement(final @Nonnull PsiElement element) {
    element.acceptChildren(this);
  }
}
