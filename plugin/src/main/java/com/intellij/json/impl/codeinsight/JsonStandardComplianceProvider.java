// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.impl.codeinsight;

import com.intellij.json.psi.JsonElement;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiComment;
import jakarta.annotation.Nonnull;

/**
 * Allows to configure a compliance level for JSON.
 * For example, some tools ignore comments in JSON silently when parsing, so there is no need to warn users about it.
 */
public abstract class JsonStandardComplianceProvider {
  public static final ExtensionPointName<JsonStandardComplianceProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.json.jsonStandardComplianceProvider");

  public static boolean shouldWarnAboutComment(@Nonnull PsiComment comment) {
    return EP_NAME.findFirstSafe(provider -> provider.isCommentAllowed(comment)) == null;
  }

  public static boolean shouldWarnAboutTrailingComma(@Nonnull JsonElement el) {
    return EP_NAME.findFirstSafe(provider -> provider.isTrailingCommaAllowed(el)) == null;
  }
  
  public boolean isCommentAllowed(@Nonnull PsiComment comment) {
    return false;
  }
  
  public boolean isTrailingCommaAllowed(@Nonnull JsonElement el) {
    return false;
  }
}