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
package com.jetbrains.jsonSchema.impl.inspections;

import com.intellij.json.psi.JsonElementVisitor;
import com.intellij.json.psi.JsonValue;
import com.jetbrains.jsonSchema.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaComplianceChecker;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.json.localize.JsonLocalize;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class JsonSchemaComplianceInspection extends JsonSchemaBasedInspectionBase {
    @Override
    public String getShortName() {
        return "JsonSchemaCompliance";
    }

    @Override
    public LocalizeValue getDisplayName() {
        return JsonLocalize.jsonSchemaInspectionComplianceName();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WARNING;
    }

    @Override
    public boolean isEnabledByDefault() {
        return true;
    }

    @Override
    public InspectionToolState<?> createStateProvider() {
        return new JsonSchemaComplianceInspectionState();
    }

    @Override
    protected PsiElementVisitor doBuildVisitor(JsonValue root,
                                               @Nullable JsonSchemaObject schema,
                                               JsonSchemaService service,
                                               ProblemsHolder holder,
                                               LocalInspectionToolSession session,
                                               Object state) {
        if (schema == null) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        JsonSchemaComplianceInspectionState inspectionState = (JsonSchemaComplianceInspectionState) state;
        JsonComplianceCheckerOptions options = new JsonComplianceCheckerOptions(inspectionState.myCaseInsensitiveEnum);

        return new JsonElementVisitor() {
            @Override
            @RequiredReadAction
            public void visitElement(PsiElement element) {
                if (element == root) {
                    // perform this only for the root element, because the checker traverses the hierarchy itself
                    annotate(element, schema, holder, session, options);
                }
                super.visitElement(element);
            }
        };
    }

    @RequiredReadAction
    private static void annotate(PsiElement element,
                                 JsonSchemaObject rootSchema,
                                 ProblemsHolder holder,
                                 LocalInspectionToolSession session,
                                 JsonComplianceCheckerOptions options) {
        JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element, rootSchema);
        if (walker == null) {
            return;
        }
        new JsonSchemaComplianceChecker(rootSchema, holder, walker, session, options).annotate(element);
    }
}
