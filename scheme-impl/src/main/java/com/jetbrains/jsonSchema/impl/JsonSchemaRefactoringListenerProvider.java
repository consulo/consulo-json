// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.JsonLanguage;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.event.RefactoringElementListenerProvider;
import consulo.language.editor.refactoring.event.UndoRefactoringElementAdapter;
import consulo.language.editor.util.PsiUtilBase;
import consulo.language.file.LanguageFileType;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class JsonSchemaRefactoringListenerProvider implements RefactoringElementListenerProvider {
    @Override
    public @Nullable RefactoringElementListener getListener(@Nullable PsiElement element) {
        if (element == null) {
            return null;
        }
        VirtualFile oldFile = PsiUtilBase.asVirtualFile(element);
        if (oldFile == null || !(oldFile.getFileType() instanceof LanguageFileType languageFileType) ||
            !(languageFileType.getLanguage().isKindOf(JsonLanguage.INSTANCE))) {
            return null;
        }
        Project project = element.getProject();
        VirtualFile baseDir = project.getBaseDir();
        if (baseDir == null) {
            return null;
        }

        String oldRelativePath = VirtualFileUtil.getRelativePath(oldFile, baseDir);
        if (oldRelativePath != null) {
            JsonSchemaMappingsProjectConfiguration configuration = JsonSchemaMappingsProjectConfiguration.getInstance(project);
            return new UndoRefactoringElementAdapter() {
                @Override
                protected void refactored(PsiElement element, @Nullable String oldQualifiedName) {
                    VirtualFile newFile = PsiUtilBase.asVirtualFile(element);
                    VirtualFile newBaseDir = project.getBaseDir();
                    if (newFile != null && newBaseDir != null) {
                        String newRelativePath = VirtualFileUtil.getRelativePath(newFile, newBaseDir);
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
