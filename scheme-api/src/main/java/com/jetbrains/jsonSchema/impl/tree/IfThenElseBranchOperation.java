// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema.impl.tree;

import com.jetbrains.jsonSchema.IfThenElse;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.extension.adapter.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaInheritanceUtil;
import com.jetbrains.jsonSchema.internal.JsonSchemaResolver;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class IfThenElseBranchOperation extends Operation {
    private final JsonSchemaService myJsonSchemaService;

    public IfThenElseBranchOperation(JsonSchemaObject schemaObject,
                                     @Nullable JsonSchemaNodeExpansionRequest expansionRequest,
                                     JsonSchemaService jsonSchemaService) {
        super(schemaObject, expansionRequest);
        myJsonSchemaService = jsonSchemaService;
    }

    @Override
    protected void map(Set<JsonSchemaObject> visited) {
        if (visited.contains(mySourceNode)) {
            return;
        }

        List<JsonSchemaObject> branches = computeEffectiveIfThenElseBranches(myExpansionRequest, mySourceNode);
        if (branches == null) {
            return;
        }
        List<JsonSchemaObject> effectiveBranches = new ArrayList<>();
        for (JsonSchemaObject branch : branches) {
            if (!visited.contains(branch)) {
                effectiveBranches.add(JsonSchemaInheritanceUtil.inheritBaseSchemaIfNeeded(mySourceNode, branch));
            }
        }
        for (JsonSchemaObject branch : effectiveBranches) {
            myChildOperations.add(new ProcessDefinitionsOperation(branch, myJsonSchemaService, myExpansionRequest));
        }
    }

    @Override
    public void reduce() {
        if (!myChildOperations.isEmpty()) {
            for (Operation operation : myChildOperations) {
                myAnyOfGroup.addAll(operation.myAnyOfGroup);
                myOneOfGroup.addAll(operation.myOneOfGroup);
            }
        }
        else {
            // if the parent schema is not empty, but there is no valid branch, don't miss properties declared in the parent schema
            myAnyOfGroup.add(mySourceNode);
        }
    }

    private @Nullable List<JsonSchemaObject> computeEffectiveIfThenElseBranches(JsonSchemaNodeExpansionRequest expansionRequest,
                                                                               JsonSchemaObject parent) {
        List<IfThenElse> conditionsList = parent.getIfThenElse();
        if (conditionsList == null) {
            return null;
        }
        JsonValueAdapter effectiveElementAdapter = getContainingObjectAdapterOrSelf(expansionRequest.getInspectedValueAdapter());

        List<JsonSchemaObject> result = new ArrayList<>();
        if (effectiveElementAdapter == null || !expansionRequest.getStrictIfElseBranchChoice()) {
            for (IfThenElse condition : conditionsList) {
                JsonSchemaObject then = condition.getThen();
                if (then != null) {
                    result.add(JsonSchemaInheritanceUtil.inheritBaseSchemaIfNeeded(parent, then));
                }
                JsonSchemaObject otherwise = condition.getElse();
                if (otherwise != null) {
                    result.add(JsonSchemaInheritanceUtil.inheritBaseSchemaIfNeeded(parent, otherwise));
                }
            }
        }
        else {
            for (IfThenElse condition : conditionsList) {
                JsonSchemaObject branch = JsonSchemaResolver.isCorrect(effectiveElementAdapter, condition.getIf())
                    ? condition.getThen()
                    : condition.getElse();
                if (branch != null) {
                    result.add(JsonSchemaInheritanceUtil.inheritBaseSchemaIfNeeded(parent, branch));
                }
            }
        }
        return result;
    }

    private @Nullable JsonValueAdapter getContainingObjectAdapterOrSelf(@Nullable JsonValueAdapter inspectedValueAdapter) {
        JsonValueAdapter inspectedRootElementAdapter;
        if (inspectedValueAdapter != null && inspectedValueAdapter.isObject()) {
            inspectedRootElementAdapter = inspectedValueAdapter;
        }
        else {
            PsiElement inspectedValuePsi = inspectedValueAdapter == null ? null : inspectedValueAdapter.getDelegate();
            JsonLikePsiWalker psiWalker = inspectedValuePsi == null ? null : JsonLikePsiWalker.getWalker(inspectedValuePsi);
            JsonPropertyAdapter parentPropertyAdapter = psiWalker == null || inspectedValuePsi == null ? null : psiWalker.getParentPropertyAdapter(inspectedValuePsi);
            JsonObjectValueAdapter parentObjectAdapter = parentPropertyAdapter == null ? null : parentPropertyAdapter.getParentObject();
            inspectedRootElementAdapter = parentObjectAdapter != null ? parentObjectAdapter : inspectedValueAdapter;
        }
        return inspectedRootElementAdapter;
    }
}
