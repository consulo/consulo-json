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
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static com.jetbrains.jsonSchema.internal.JsonSchemaVariantsTreeBuilder.andGroup;

class OneOfOperation extends Operation {
    private final JsonSchemaService myService;

    protected OneOfOperation(JsonSchemaObject sourceNode, JsonSchemaService service, @Nullable JsonSchemaNodeExpansionRequest expansionRequest) {
        super(sourceNode, expansionRequest);
        myService = service;
    }

    @Override
    public void map(Set<JsonSchemaObject> visited) {
        List<? extends JsonSchemaObject> oneOf = mySourceNode.getOneOf();
        assert oneOf != null;
        myChildOperations.addAll(ContainerUtil.map(oneOf, sourceNode -> new ProcessDefinitionsOperation(sourceNode, myService, myExpansionRequest)));
    }

    @Override
    public void reduce() {
        List<JsonSchemaObject> oneOf = new SmartList<>();
        for (Operation op : myChildOperations) {
            if (!op.myState.equals(SchemaResolveState.normal)) {
                continue;
            }
            oneOf.addAll(andGroup(mySourceNode, op.myAnyOfGroup));
            oneOf.addAll(andGroup(mySourceNode, mergeOneOf(op)));
        }
        // here it is not a mistake - all children of this node come to oneOf group
        myOneOfGroup.add(oneOf);
    }
}