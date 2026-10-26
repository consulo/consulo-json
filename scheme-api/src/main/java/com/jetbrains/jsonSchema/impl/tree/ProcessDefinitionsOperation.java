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

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.internal.SchemaResolveState;
import consulo.application.progress.ProgressManager;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.Set;

import static com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectMerger.getJsonSchemaObjectMerger;

public final class ProcessDefinitionsOperation extends Operation {
    private final JsonSchemaService myService;

    public ProcessDefinitionsOperation(JsonSchemaObject sourceNode,
                                       JsonSchemaService service,
                                       @Nullable JsonSchemaNodeExpansionRequest expansionRequest) {
        super(sourceNode, expansionRequest);
        myService = service;
    }

    @Override
    public void map(Set<JsonSchemaObject> visited) {
        JsonSchemaObject current = mySourceNode;
        while (!StringUtil.isEmptyOrSpaces(current.getRef())) {
            ProgressManager.checkCanceled();
            JsonSchemaObject definition = current.resolveRefSchema(myService);
            if (definition == null) {
                myState = SchemaResolveState.brokenDefinition;
                return;
            }
            // this definition was already expanded; do not cycle
            if (!visited.add(definition)) {
                break;
            }
            current = getJsonSchemaObjectMerger().mergeObjects(current, definition, current);
        }
        Operation expandOperation = createExpandOperation(current, myService, myExpansionRequest);
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
            Operation operation = myChildOperations.get(0);
            myAnyOfGroup.addAll(operation.myAnyOfGroup);
            myOneOfGroup.addAll(operation.myOneOfGroup);
        }
    }
}
