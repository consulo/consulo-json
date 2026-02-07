// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.jsonc;

import com.intellij.json.impl.codeinsight.JsonStandardComplianceProvider;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiUtilCore;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class JsoncComplianceProvider extends JsonStandardComplianceProvider {
  private static final String JSONC_DEFAULT_EXTENSION = "jsonc";

  @Override
  public boolean isCommentAllowed(@Nonnull PsiComment comment) {
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(comment);

    if (virtualFile != null && JSONC_DEFAULT_EXTENSION.equals(virtualFile.getExtension())) {
      return true;
    }

    return super.isCommentAllowed(comment);
  }
}
