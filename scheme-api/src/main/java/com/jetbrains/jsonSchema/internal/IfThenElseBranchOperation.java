// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import com.jetbrains.jsonSchema.IfThenElse;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import consulo.language.psi.PsiElement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class IfThenElseBranchOperation extends Operation {
    private final JsonSchemaService jsonSchemaService;

    IfThenElseBranchOperation(JsonSchemaObject schemaObject, JsonSchemaNodeExpansionRequest expansionRequest, JsonSchemaService jsonSchemaService) {
        super(schemaObject, expansionRequest);
        this.jsonSchemaService = jsonSchemaService;
    }

    @Override
    protected void map(Set<JsonSchemaObject> visited) {
        if (visited.contains(mySourceNode)) {
            return;
        }
        //JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.IfElseExpanded);

        List<JsonSchemaObject> effectiveBranches = computeEffectiveIfThenElseBranches(myExpansionRequest, mySourceNode);
        if (effectiveBranches != null) {
            List<JsonSchemaObject> filteredBranches = new ArrayList<>();
            for (JsonSchemaObject branch : effectiveBranches) {
                if (!visited.contains(branch)) {
                    filteredBranches.add(JsonSchemaObjectRendering.inheritBaseSchemaIfNeeded(mySourceNode, branch));
                }
            }

            if (!filteredBranches.isEmpty()) {
                for (JsonSchemaObject branch : filteredBranches) {
                    myChildOperations.add(new ProcessDefinitionsOperation(branch, jsonSchemaService, myExpansionRequest));
                }
            }
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

    private List<JsonSchemaObject> computeEffectiveIfThenElseBranches(JsonSchemaNodeExpansionRequest expansionRequest, JsonSchemaObject parent) {
        List<IfThenElse> conditionsList = parent.getIfThenElse();
        if (conditionsList == null) {
            return null;
        }

        JsonValueAdapter effectiveElementAdapter = getContainingObjectAdapterOrSelf(expansionRequest.getInspectedValueAdapter());

        List<JsonSchemaObject> result = new ArrayList<>();

        if (effectiveElementAdapter == null || !expansionRequest.isStrictIfElseBranchChoice()) {
            for (IfThenElse condition : conditionsList) {
                if (condition.getThen() != null) {
                    result.add(JsonSchemaObjectRendering.inheritBaseSchemaIfNeeded(parent, condition.getThen()));
                }
                if (condition.getElse() != null) {
                    result.add(JsonSchemaObjectRendering.inheritBaseSchemaIfNeeded(parent, condition.getElse()));
                }
            }
        }
        else {
            for (IfThenElse condition : conditionsList) {
                JsonSchemaObject branch;
                if (JsonSchemaResolver.isCorrect(effectiveElementAdapter, condition.getIf())) {
                    branch = condition.getThen();
                }
                else {
                    branch = condition.getElse();
                }
                if (branch != null) {
                    result.add(JsonSchemaObjectRendering.inheritBaseSchemaIfNeeded(parent, branch));
                }
            }
        }

        return result;
    }

    private JsonValueAdapter getContainingObjectAdapterOrSelf(JsonValueAdapter inspectedValueAdapter) {
        JsonValueAdapter myInspectedRootElementAdapter;
        if (inspectedValueAdapter != null && inspectedValueAdapter.isObject()) {
            myInspectedRootElementAdapter = inspectedValueAdapter;
        }
        else {
            PsiElement inspectedValuePsi = inspectedValueAdapter != null ? inspectedValueAdapter.getDelegate() : null;
            JsonLikePsiWalker psiWalker = inspectedValuePsi == null ? null : JsonLikePsiWalker.getWalker(inspectedValuePsi);
            JsonValueAdapter parentPropertyAdapter = psiWalker != null ? psiWalker.getParentPropertyAdapter(inspectedValuePsi) : null;
            JsonValueAdapter parentObjectAdapter = parentPropertyAdapter != null ? parentPropertyAdapter.getParentObject() : null;
            myInspectedRootElementAdapter = parentObjectAdapter != null ? parentObjectAdapter : inspectedValueAdapter;
        }
        return myInspectedRootElementAdapter;
    }
}
