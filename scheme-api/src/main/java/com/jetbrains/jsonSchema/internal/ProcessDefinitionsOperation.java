// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import consulo.application.progress.ProgressManager;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Set;

public final class ProcessDefinitionsOperation extends Operation {
  private final JsonSchemaService myService;

  public ProcessDefinitionsOperation(@Nonnull JsonSchemaObject sourceNode,
                                     @Nonnull JsonSchemaService service,
                                     @Nullable JsonSchemaNodeExpansionRequest expansionRequest) {
    super(sourceNode, expansionRequest);
    myService = service;
  }

  @Override
  public void map(final @Nonnull Set<JsonSchemaObject> visited) {
    //JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.DefinitionsExpanded);
    var current = mySourceNode;
    while (!StringUtil.isEmptyOrSpaces(current.getRef())) {
      ProgressManager.checkCanceled();
      final var definition = current.resolveRefSchema(myService);
      if (definition == null) {
        myState = SchemaResolveState.brokenDefinition;
        return;
      }
      // this definition was already expanded; do not cycle
      if (!visited.add(definition)) break;
      current = JsonSchemaObjectMerger.getJsonSchemaObjectMerger().mergeObjects(current, definition, current);
    }
    final Operation expandOperation = createExpandOperation(current, myService, myExpansionRequest);
    if (expandOperation != null) {
      myChildOperations.add(expandOperation);
    }
    else {
      myAnyOfGroup.add(current);
    }
  }

  @Override
  public void reduce() {
    if (!myChildOperations.isEmpty()) {
      assert myChildOperations.size() == 1;
      final Operation operation = myChildOperations.get(0);
      myAnyOfGroup.addAll(operation.myAnyOfGroup);
      myOneOfGroup.addAll(operation.myOneOfGroup);
    }
  }
}
