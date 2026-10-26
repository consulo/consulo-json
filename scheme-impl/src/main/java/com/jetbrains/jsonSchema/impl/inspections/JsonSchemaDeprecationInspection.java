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

import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonElementVisitor;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.extension.adapter.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.light.nodes.InheritedJsonSchemaObjectView;
import com.jetbrains.jsonSchema.internal.JsonSchemaResolver;
import com.jetbrains.jsonSchema.internal.MatchResult;
import com.jetbrains.jsonSchema.internal.MergedJsonSchemaObject;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.json.localize.JsonLocalize;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

@ExtensionImpl
public final class JsonSchemaDeprecationInspection extends JsonSchemaBasedInspectionBase {
    @Override
    public String getShortName() {
        return "JsonSchemaDeprecation";
    }

    @Override
    public LocalizeValue getDisplayName() {
        return JsonLocalize.jsonSchemaInspectionDeprecationName();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
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
        if (schema == null) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(root, schema);
        if (walker == null) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        Project project = root.getProject();
        return new JsonElementVisitor() {
            @Override
            @RequiredReadAction
            public void visitProperty(JsonProperty o) {
                annotate(o);
                super.visitProperty(o);
            }

            @RequiredReadAction
            private void annotate(JsonProperty o) {
                JsonPointerPosition position = walker.findPosition(o, true);
                if (position == null) {
                    return;
                }

                JsonPropertyAdapter parentPropertyAdapter = walker.getParentPropertyAdapter(o);
                MatchResult result = new JsonSchemaResolver(
                    project,
                    schema,
                    position,
                    parentPropertyAdapter == null ? null : parentPropertyAdapter.getNameValueAdapter()
                ).detailedResolve();
                Iterable<JsonSchemaObject> iterable;
                if (result.myExcludingSchemas.size() == 1) {
                    iterable = ContainerUtil.concat(result.mySchemas, result.myExcludingSchemas.getFirst());
                }
                else {
                    iterable = result.mySchemas;
                }

                JsonValueAdapter valueAdapter = o.getValue() == null ? null : walker.createValueAdapter(o.getValue());
                String message = getDeprecationMessage(iterable, valueAdapter);
                if (message != null) {
                    holder.newProblem(JsonLocalize.property0IsDeprecated1(o.getName(), message))
                        .range(o.getNameElement())
                        .create();
                }
            }
        };
    }

    public static @Nullable String getDeprecationMessage(Iterable<? extends JsonSchemaObject> schemas,
                                                         @Nullable JsonValueAdapter inspectedValueAdapter) {
        for (JsonSchemaObject schema : schemas) {
            String message = schema.getDeprecationMessage();
            if (message == null) {
                continue;
            }
            JsonSchemaObject alternativeBranch = findDeprecatedAlternativeBranch(schema);
            if (alternativeBranch != null &&
                (inspectedValueAdapter == null || !JsonSchemaResolver.isCorrect(inspectedValueAdapter, alternativeBranch))) {
                continue;
            }
            return message;
        }
        return null;
    }

    private static @Nullable JsonSchemaObject findDeprecatedAlternativeBranch(JsonSchemaObject schema) {
        if (!(schema instanceof MergedJsonSchemaObject mergedSchema) || schema instanceof InheritedJsonSchemaObjectView) {
            return null;
        }

        JsonSchemaObject baseSchema = mergedSchema.getBase();
        JsonSchemaObject alternativeSchema = mergedSchema.getOther();
        boolean isDeprecatedAlternative =
            baseSchema.getDeprecationMessage() == null &&
                alternativeSchema.getDeprecationMessage() != null &&
                (isBuiltFromBranch(alternativeSchema, baseSchema.getAnyOf()) || isBuiltFromBranch(alternativeSchema, baseSchema.getOneOf()));
        return isDeprecatedAlternative ? alternativeSchema : null;
    }

    private static boolean isBuiltFromBranch(JsonSchemaObject schema, @Nullable List<? extends JsonSchemaObject> branches) {
        if (branches == null) {
            return false;
        }
        if (branches.contains(schema)) {
            return true;
        }
        return schema instanceof MergedJsonSchemaObject mergedSchema &&
            (isBuiltFromBranch(mergedSchema.getBase(), branches) || isBuiltFromBranch(mergedSchema.getOther(), branches));
    }
}
