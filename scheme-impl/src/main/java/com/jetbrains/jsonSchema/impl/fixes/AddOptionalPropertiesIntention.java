// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import com.intellij.json.psi.JsonObject;
import com.jetbrains.jsonSchema.JsonValidationError.MissingMultiplePropsIssueData;
import com.jetbrains.jsonSchema.extension.JsonSchemaQuickFixSuppressor;
import com.jetbrains.jsonSchema.internal.JsonCachedValues;
import com.jetbrains.jsonSchema.walker.JsonLikeSyntaxAdapter;
import com.jetbrains.jsonSchema.walker.JsonOriginalPsiWalker;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.json.localize.JsonLocalize;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.lang.ref.SimpleReference;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

@ExtensionImpl
@IntentionMetaData(ignoreId = "json.AddOptionalPropertiesIntention", fileExtensions = "json", categories = "JSON")
public class AddOptionalPropertiesIntention extends PsiElementBaseIntentionAction {
    public LocalizeValue getFamilyName() {
        return JsonLocalize.intentionAddNotRequiredPropertiesFamilyName();
    }

    @Override
    public LocalizeValue getText() {
        return JsonLocalize.intentionAddNotRequiredPropertiesText();
    }

    @Override
    @RequiredReadAction
    public boolean isAvailable(Project project, Editor editor, PsiElement element) {
        PsiElement obj = findContainingObjectNode(editor, element);
        if (obj == null) {
            return false;
        }
        PsiFile file = Objects.requireNonNull(element.getContainingFile());
        if (Application.get().getExtensionPoint(JsonSchemaQuickFixSuppressor.class).anyMatchSafe(
            it -> it.shouldSuppressFix(file, AddOptionalPropertiesIntention.class)
        )) {
            return false;
        }
        if (!JsonCachedValues.hasComputedSchemaObjectForFile(Objects.requireNonNull(obj.getContainingFile()))) {
            return false;
        }

        JsonSchemaPropertiesInfo info = MissingOptionalPropertiesSearch.collectMissingPropertiesFromSchema(
            SmartPointerManager.createPointer(obj),
            project
        );
        MissingMultiplePropsIssueData missing = info == null ? null : info.getMissingKnownProperties();
        return missing != null && !missing.myMissingPropertyIssues.isEmpty();
    }

    @Override
    @RequiredWriteAction
    public void invoke(Project project, Editor editor, PsiElement element) {
        PsiElement objCopy = findContainingObjectNode(editor, element);
        if (objCopy == null) {
            return;
        }
        PsiElement physObj = findPhysicalObjectNode(editor, element);
        if (physObj == null) {
            return;
        }

        JsonSchemaPropertiesInfo info = MissingOptionalPropertiesSearch.collectMissingPropertiesFromSchema(
            SmartPointerManager.createPointer(physObj),
            project
        );
        if (info == null) {
            return;
        }
        MissingMultiplePropsIssueData missing = info.getMissingKnownProperties();

        new AddMissingPropertyFix(missing, getSyntaxAdapter(project)).performFixInner(objCopy, SimpleReference.create());

        CodeStyleManager.getInstance(project).reformatText(objCopy.getContainingFile(), Set.of(objCopy.getTextRange()));
    }

    protected JsonLikeSyntaxAdapter getSyntaxAdapter(Project project) {
        return JsonOriginalPsiWalker.INSTANCE.getSyntaxAdapter(project);
    }

    @RequiredReadAction
    protected @Nullable PsiElement findPhysicalObjectNode(Editor editor, PsiElement element) {
        PsiElement physLeaf = findLeaf(editor, Objects.requireNonNull(element.getContainingFile()));
        if (physLeaf == null) {
            return null;
        }
        return PsiTreeUtil.getParentOfType(physLeaf, JsonObject.class);
    }

    @RequiredReadAction
    protected @Nullable PsiElement findContainingObjectNode(Editor editor, PsiElement element) {
        JsonObject containingObject = PsiTreeUtil.getParentOfType(element, JsonObject.class);
        if (containingObject != null) {
            return containingObject;
        }
        return PsiTreeUtil.getParentOfType(findLeaf(editor, Objects.requireNonNull(element.getContainingFile())), JsonObject.class);
    }

    @RequiredReadAction
    private static @Nullable PsiElement findLeaf(Editor editor, PsiFile file) {
        return file.findElementAt(editor.getCaretModel().getOffset());
    }
}
