// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.json.JsonLanguage;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.event.RefactoringElementListenerProvider;
import consulo.language.editor.refactoring.event.UndoRefactoringElementAdapter;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.file.LanguageFileType;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public final class JsonSchemaRefactoringListenerProvider implements RefactoringElementListenerProvider {
  @Override
  public @Nullable RefactoringElementListener getListener(PsiElement element) {
    if (element == null) {
      return null;
    }
    final VirtualFile oldFile = PsiUtilBase.asVirtualFile(element);
    if (oldFile == null || !(oldFile.getFileType() instanceof LanguageFileType) ||
      !(((LanguageFileType)oldFile.getFileType()).getLanguage().isKindOf(JsonLanguage.INSTANCE))) {
      return null;
    }
    final Project project = element.getProject();
    if (project.getBaseDir() == null) return null;

    final String oldRelativePath = VirtualFileUtil.getRelativePath(oldFile, project.getBaseDir());
    if (oldRelativePath != null) {
      final JsonSchemaMappingsProjectConfiguration configuration = JsonSchemaMappingsProjectConfiguration.getInstance(project);
      return new UndoRefactoringElementAdapter() {
        @Override
        protected void refactored(@Nonnull PsiElement element, @Nullable String oldQualifiedName) {
          final VirtualFile newFile = PsiUtilBase.asVirtualFile(element);
          if (newFile != null) {
            final String newRelativePath = VirtualFileUtil.getRelativePath(newFile, project.getBaseDir());
            if (newRelativePath != null) {
              configuration.schemaFileMoved(project, oldRelativePath, newRelativePath);
            }
          }
        }
      };
    }
    return null;
  }
}
