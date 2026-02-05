// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.tree;

import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.fus.JsonSchemaFusCountedFeature;
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectRenderingKt;
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
  void map(Set<JsonSchemaObject> visited) {
    if (visited.contains(mySourceNode)) return;
    JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.IfElseExpanded);

    List<JsonSchemaObject> effectiveBranches = computeEffectiveIfThenElseBranches(myExpansionRequest, mySourceNode);
    if (effectiveBranches != null) {
      List<JsonSchemaObject> filteredBranches = new ArrayList<>();
      for (JsonSchemaObject branch : effectiveBranches) {
        if (!visited.contains(branch)) {
          filteredBranches.add(JsonSchemaObjectRenderingKt.inheritBaseSchemaIfNeeded(mySourceNode, branch));
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
    List<JsonSchemaObject.IfThenElse> conditionsList = parent.getIfThenElse();
    if (conditionsList == null) return null;

    JsonValueAdapter effectiveElementAdapter = getContainingObjectAdapterOrSelf(expansionRequest.inspectedValueAdapter);

    List<JsonSchemaObject> result = new ArrayList<>();

    if (effectiveElementAdapter == null || !expansionRequest.strictIfElseBranchChoice) {
      for (JsonSchemaObject.IfThenElse condition : conditionsList) {
        if (condition.getThen() != null) {
          result.add(JsonSchemaObjectRenderingKt.inheritBaseSchemaIfNeeded(parent, condition.getThen()));
        }
        if (condition.getElse() != null) {
          result.add(JsonSchemaObjectRenderingKt.inheritBaseSchemaIfNeeded(parent, condition.getElse()));
        }
      }
    }
    else {
      for (JsonSchemaObject.IfThenElse condition : conditionsList) {
        JsonSchemaObject branch;
        if (JsonSchemaResolver.isCorrect(effectiveElementAdapter, condition.getIf())) {
          branch = condition.getThen();
        }
        else {
          branch = condition.getElse();
        }
        if (branch != null) {
          result.add(JsonSchemaObjectRenderingKt.inheritBaseSchemaIfNeeded(parent, branch));
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
