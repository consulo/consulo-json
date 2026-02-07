// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.impl.codeinsight;

import com.intellij.json.psi.JsonElement;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiComment;
import jakarta.annotation.Nonnull;

/**
 * Allows to configure a compliance level for JSON.
 * For example, some tools ignore comments in JSON silently when parsing, so there is no need to warn users about it.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class JsonStandardComplianceProvider {
  public static final ExtensionPointName<JsonStandardComplianceProvider> EP_NAME =
    ExtensionPointName.create(JsonStandardComplianceProvider.class);

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