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
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonPointerReferenceProvider;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.json.localize.JsonLocalize;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.impl.psi.path.WebReference;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import consulo.language.psi.path.FileReference;
import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class JsonSchemaRefReferenceInspection extends JsonSchemaBasedInspectionBase {
    @Override
    public String getShortName() {
        return "JsonSchemaRefReference";
    }

    @Override
    public LocalizeValue getDisplayName() {
        return JsonLocalize.jsonSchemaRefRefsInspectionName();
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
    protected PsiElementVisitor doBuildVisitor(JsonValue root,
                                               @Nullable JsonSchemaObject schema,
                                               JsonSchemaService service,
                                               ProblemsHolder holder,
                                               LocalInspectionToolSession session,
                                               Object state) {
        boolean checkRefs = schema != null && service.isSchemaFile(schema);
        return new JsonElementVisitor() {
            @Override
            @RequiredReadAction
            public void visitElement(PsiElement element) {
                if (element == root) {
                    if (element instanceof JsonObject jsonObject) {
                        JsonProperty schemaProp = jsonObject.findProperty("$schema");
                        if (schemaProp != null) {
                            doCheck(schemaProp.getValue());
                        }
                    }
                }
                super.visitElement(element);
            }

            @Override
            @RequiredReadAction
            public void visitProperty(JsonProperty o) {
                if (!checkRefs) {
                    return;
                }
                if ("$ref".equals(o.getName())) {
                    doCheck(o.getValue());
                }
                super.visitProperty(o);
            }

            @RequiredReadAction
            private void doCheck(@Nullable JsonValue value) {
                if (!(value instanceof JsonStringLiteral)) {
                    return;
                }
                for (PsiReference reference : value.getReferences()) {
                    if (reference instanceof WebReference) {
                        continue;
                    }
                    PsiElement resolved = reference.resolve();
                    if (resolved == null) {
                        holder.newProblem(getReferenceErrorDesc(reference))
                            .rangeByRef(reference)
                            .highlightType(ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                            .create();
                    }
                }
            }

            @RequiredReadAction
            private static LocalizeValue getReferenceErrorDesc(PsiReference reference) {
                String text = reference.getCanonicalText();
                if (reference instanceof FileReference) {
                    int hash = text.indexOf('#');
                    return JsonLocalize.jsonSchemaRefFileNotFound(hash == -1 ? text : text.substring(0, hash));
                }
                if (reference instanceof JsonPointerReferenceProvider.JsonSchemaIdReference) {
                    return JsonLocalize.jsonSchemaRefCannotResolveId(text);
                }
                int lastSlash = text.lastIndexOf('/');
                if (lastSlash == -1) {
                    return JsonLocalize.jsonSchemaRefCannotResolvePath(text);
                }
                String substring = text.substring(text.lastIndexOf('/') + 1);

                try {
                    Integer.parseInt(substring);
                    return JsonLocalize.jsonSchemaRefNoArrayElement(substring);
                }
                catch (Exception e) {
                    return JsonLocalize.jsonSchemaRefNoProperty(substring);
                }
            }
        };
    }
}
