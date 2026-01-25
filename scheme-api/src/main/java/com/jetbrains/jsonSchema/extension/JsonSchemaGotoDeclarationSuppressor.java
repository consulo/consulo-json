// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;

/**
 * Extension point aimed to suppress navigation to json schema element which corresponds to given PsiElement
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface JsonSchemaGotoDeclarationSuppressor {
  ExtensionPointName<JsonSchemaGotoDeclarationSuppressor> EP_NAME = ExtensionPointName.create(JsonSchemaGotoDeclarationSuppressor.class);

  boolean shouldSuppressGtd(PsiElement psiElement);
}
