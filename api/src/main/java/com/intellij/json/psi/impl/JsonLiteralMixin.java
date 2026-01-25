/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.json.psi.impl;

import com.intellij.json.psi.JsonLiteral;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ReferenceProvidersRegistry;
import jakarta.annotation.Nonnull;

abstract class JsonLiteralMixin extends JsonElementImpl implements JsonLiteral {
  protected JsonLiteralMixin(@Nonnull ASTNode node) {
    super(node);
  }

  @Override
  public PsiReference @Nonnull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }
}
