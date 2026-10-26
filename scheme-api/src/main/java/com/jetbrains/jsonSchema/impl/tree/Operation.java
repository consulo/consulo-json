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
import consulo.util.collection.SmartList;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.ALL_OF;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.ANY_OF;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.IF;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.ONE_OF;

public abstract class Operation {
    public final JsonSchemaNodeExpansionRequest myExpansionRequest;
    public final List<JsonSchemaObject> myAnyOfGroup = new SmartList<>();
    public final List<List<JsonSchemaObject>> myOneOfGroup = new SmartList<>();
    public final List<Operation> myChildOperations;
    public final JsonSchemaObject mySourceNode;
    public SchemaResolveState myState = SchemaResolveState.normal;

    protected Operation(JsonSchemaObject sourceNode, @Nullable JsonSchemaNodeExpansionRequest expansionRequest) {
        mySourceNode = sourceNode;
        myChildOperations = new ArrayList<>();
        myExpansionRequest = expansionRequest == null ?
                             new JsonSchemaNodeExpansionRequest(null, true) : expansionRequest;
    }

    protected abstract void map(Set<JsonSchemaObject> visited);
    protected abstract void reduce();

    public void doMap(Set<JsonSchemaObject> visited) {
        map(visited);
        for (Operation operation : myChildOperations) {
            ProgressManager.checkCanceled();
            operation.doMap(visited);
        }
    }

    public void doReduce() {
        if (!SchemaResolveState.normal.equals(myState)) {
            myChildOperations.clear();
            myAnyOfGroup.clear();
            myOneOfGroup.clear();
            return;
        }

        for (Operation myChildOperation : myChildOperations) {
            ProgressManager.checkCanceled();
            myChildOperation.doReduce();
        }
        reduce();
        myChildOperations.clear();
    }

    protected @Nullable Operation createExpandOperation(JsonSchemaObject schema,
                                                        JsonSchemaService service,
                                                        @Nullable JsonSchemaNodeExpansionRequest expansionRequest) {
        Operation forConflict = getOperationForConflict(schema, service, expansionRequest);
        if (forConflict != null) {
            return forConflict;
        }
        if (schema.hasChildNode(ANY_OF)) {
            return new AnyOfOperation(schema, service, expansionRequest);
        }
        if (schema.hasChildNode(ONE_OF)) {
            return new OneOfOperation(schema, service, expansionRequest);
        }
        if (schema.hasChildNode(ALL_OF)) {
            return new AllOfOperation(schema, service, expansionRequest);
        }
        if (schema.hasChildNode(IF)) {
            return new IfThenElseBranchOperation(schema, expansionRequest, service);
        }
        return null;
    }

    private static @Nullable Operation getOperationForConflict(JsonSchemaObject schema,
                                                               JsonSchemaService service,
                                                               @Nullable JsonSchemaNodeExpansionRequest expansionRequest) {
        // in case of several incompatible operations, choose the most permissive one
        boolean anyOf = schema.hasChildNode(ANY_OF);
        boolean oneOf = schema.hasChildNode(ONE_OF);
        boolean allOf = schema.hasChildNode(ALL_OF);

        if (anyOf && (oneOf || allOf)) {
            return new AnyOfOperation(schema, service, expansionRequest) {{
                  myState = SchemaResolveState.conflict;
              }};
        }
        else if (oneOf && allOf) {
            return new OneOfOperation(schema, service, expansionRequest) {{
                  myState = SchemaResolveState.conflict;
              }};
        }
        return null;
    }

    protected static List<JsonSchemaObject> mergeOneOf(Operation op) {
        return op.myOneOfGroup.stream().flatMap(List::stream).collect(Collectors.toList());
    }
}
