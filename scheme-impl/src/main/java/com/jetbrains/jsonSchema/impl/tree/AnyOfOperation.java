// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.tree;

import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.fus.JsonSchemaFusCountedFeature;
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.SchemaResolveState;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Set;

import static com.jetbrains.jsonSchema.impl.JsonSchemaVariantsTreeBuilder.andGroup;

public class AnyOfOperation extends Operation {
  private final JsonSchemaService myService;

  public AnyOfOperation(@Nonnull JsonSchemaObject sourceNode, JsonSchemaService service, @Nullable JsonSchemaNodeExpansionRequest expansionRequest) {
    super(sourceNode, expansionRequest);
    myService = service;
  }

  @Override
  public void map(final @Nonnull Set<JsonSchemaObject> visited) {
    JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.AnyOfExpanded);
    var anyOf = mySourceNode.getAnyOf();
    assert anyOf != null;
    myChildOperations.addAll(ContainerUtil.map(anyOf, sourceNode -> new ProcessDefinitionsOperation(sourceNode, myService, myExpansionRequest)));
  }

  @Override
  public void reduce() {
    for (Operation op : myChildOperations) {
      if (!op.myState.equals(SchemaResolveState.normal)) continue;

      myAnyOfGroup.addAll(andGroup(mySourceNode, op.myAnyOfGroup));
      for (var group : op.myOneOfGroup) {
        myOneOfGroup.add(andGroup(mySourceNode, group));
      }
    }
  }
}