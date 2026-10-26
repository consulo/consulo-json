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

import com.intellij.json.JsonElementTypes;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.extension.JsonSchemaGotoDeclarationSuppressor;
import com.jetbrains.jsonSchema.internal.JsonSchemaResolver;
import com.jetbrains.jsonSchema.walker.JsonOriginalPsiWalker;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.codeEditor.Editor;
import consulo.language.ast.IElementType;
import consulo.language.editor.navigation.GotoDeclarationHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.path.FileReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@ExtensionImpl(id = "JsonSchemaGotoDeclarationHandler")
public final class JsonSchemaGotoDeclarationHandler implements GotoDeclarationHandler {
    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, Editor editor) {
        boolean shouldSuppressNavigation = Application.get().getExtensionPoint(JsonSchemaGotoDeclarationSuppressor.class)
            .anyMatchSafe(it -> sourceElement != null && it.shouldSuppressGtd(sourceElement));
        if (shouldSuppressNavigation) {
            return null;
        }

        IElementType elementType = PsiUtilCore.getElementType(sourceElement);
        if (elementType != JsonElementTypes.STRING_LITERAL
            && elementType != JsonElementTypes.DOUBLE_QUOTED_STRING
            && elementType != JsonElementTypes.SINGLE_QUOTED_STRING) {
            return null;
        }

        JsonStringLiteral literal = PsiTreeUtil.getParentOfType(sourceElement, JsonStringLiteral.class, false);
        if (literal == null) {
            return null;
        }

        PsiElement parent = literal.getParent();
        if (literal.getReferences().length == 0
            && parent instanceof JsonProperty parentProperty
            && parentProperty.getNameElement() == literal
            && canNavigateToSchema(parentProperty)) {
            PsiFile containingFile = literal.getContainingFile();
            JsonSchemaService service = JsonSchemaService.get(literal.getProject());

            if (containingFile == null) {
                return null;
            }
            VirtualFile file = containingFile.getVirtualFile();
            if (file == null || !service.isApplicableToFile(file)) {
                return null;
            }

            JsonPointerPosition steps = JsonOriginalPsiWalker.INSTANCE.findPosition(literal, true);
            if (steps == null) {
                return null;
            }

            JsonSchemaObject schemaObject = service.getSchemaObject(containingFile);
            if (schemaObject != null) {
                PsiElement target = new JsonSchemaResolver(
                    literal.getProject(),
                    schemaObject,
                    steps,
                    JsonOriginalPsiWalker.INSTANCE.createValueAdapter(Objects.requireNonNull(parentProperty.getParent()))
                ).findNavigationTarget(parentProperty.getValue());
                if (target != null) {
                    return new PsiElement[]{target};
                }
            }
        }
        return null;
    }

    private static boolean canNavigateToSchema(JsonProperty parent) {
        return !ContainerUtil.exists(parent.getReferences(), r -> r instanceof FileReference);
    }
}
