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
package com.jetbrains.jsonSchema.impl.fixes;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaCompletionContributor;
import com.jetbrains.jsonSchema.walker.JsonLikeSyntaxAdapter;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.WriteAction;
import consulo.codeEditor.Editor;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorManager;
import consulo.fileEditor.TextEditor;
import consulo.json.localize.JsonLocalize;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.template.ConstantNode;
import consulo.language.editor.template.ExpressionContext;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public final class SuggestEnumValuesFix implements LocalQuickFix {
    private final JsonLikeSyntaxAdapter myQuickFixAdapter;

    public SuggestEnumValuesFix(JsonLikeSyntaxAdapter quickFixAdapter) {
        myQuickFixAdapter = quickFixAdapter;
    }

    public LocalizeValue getFamilyName() {
        return JsonLocalize.replaceWithAllowedValue();
    }

    @Override
    public LocalizeValue getName() {
        return getFamilyName();
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    @RequiredUIAccess
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement initialElement = descriptor.getStartElement();
        if (initialElement == null) {
            return;
        }
        PsiFile file = Objects.requireNonNull(initialElement.getContainingFile());
        PsiElement element = myQuickFixAdapter.adjustValue(initialElement);
        JsonSchemaService jsonSchemaService = JsonSchemaService.get(project);
        JsonSchemaObject object = jsonSchemaService.getSchemaObject(file);
        if (object == null) {
            return;
        }
        List<LookupElement> variants = JsonSchemaCompletionContributor.getCompletionVariants(object, element, element, CompletionType.BASIC);
        if (variants.isEmpty()) {
            return;
        }
        Editor editor = findEditor(project, file);
        if (editor == null) {
            return;
        }
        LookupElement[] lookupItems = variants.toArray(LookupElement.EMPTY_ARRAY);
        ConstantNode expression = new ConstantNode(variants.get(0).getLookupString()) {
            @Override
            public LookupElement[] calculateLookupItems(ExpressionContext context) {
                return lookupItems;
            }
        };
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
        TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(element);
        builder.replaceElement(element, expression);
        WriteAction.run(() -> builder.run(editor, true));
    }

    @RequiredReadAction
    static @Nullable Editor findEditor(Project project, PsiFile file) {
        VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) {
            return null;
        }
        FileEditor fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(virtualFile);
        return fileEditor instanceof TextEditor textEditor ? textEditor.getEditor() : null;
    }
}
