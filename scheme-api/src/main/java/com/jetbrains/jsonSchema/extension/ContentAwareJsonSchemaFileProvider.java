// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiFile;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Provides a JSON schema depending on the contents of the file this schema is requested for.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface ContentAwareJsonSchemaFileProvider {
  ExtensionPointName<ContentAwareJsonSchemaFileProvider> EP_NAME =
    ExtensionPointName.create(ContentAwareJsonSchemaFileProvider.class);

  @Nullable
  VirtualFile getSchemaFile(@Nonnull PsiFile psiFile);
}
