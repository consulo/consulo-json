// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension;

import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * Implement to suppress a JSON schema-related quick fix or intention action for your file
 * Currently supported actions to suppress are:
 * - AddOptionalPropertiesIntention
 */
public interface JsonSchemaQuickFixSuppressor {
  ExtensionPointName<JsonSchemaQuickFixSuppressor> EXTENSION_POINT_NAME =
    ExtensionPointName.create("com.intellij.json.jsonSchemaQuickFixSuppressor");

  boolean shouldSuppressFix(PsiFile file, Class<? extends PsiUpdateModCommandAction<PsiElement>> quickFixClass);
}
