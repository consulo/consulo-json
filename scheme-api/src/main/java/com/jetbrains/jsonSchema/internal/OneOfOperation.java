// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Set;

import static com.jetbrains.jsonSchema.internal.JsonSchemaVariantsTreeBuilder.andGroup;

class OneOfOperation extends Operation {
  private final JsonSchemaService myService;

  protected OneOfOperation(@Nonnull JsonSchemaObject sourceNode, JsonSchemaService service, @Nullable JsonSchemaNodeExpansionRequest expansionRequest) {
    super(sourceNode, expansionRequest);
    myService = service;
  }

  @Override
  public void map(final @Nonnull Set<JsonSchemaObject> visited) {
    //JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.OneOfExpanded);
    var oneOf = mySourceNode.getOneOf();
    assert oneOf != null;
    myChildOperations.addAll(ContainerUtil.map(oneOf, sourceNode -> new ProcessDefinitionsOperation(sourceNode, myService, myExpansionRequest)));
  }

  @Override
  public void reduce() {
    final List<JsonSchemaObject> oneOf = new SmartList<>();
    for (Operation op : myChildOperations) {
      if (!op.myState.equals(SchemaResolveState.normal)) continue;
      oneOf.addAll(andGroup(mySourceNode, op.myAnyOfGroup));
      oneOf.addAll(andGroup(mySourceNode, mergeOneOf(op)));
    }
    // here it is not a mistake - all children of this node come to oneOf group
    myOneOfGroup.add(oneOf);
  }
}