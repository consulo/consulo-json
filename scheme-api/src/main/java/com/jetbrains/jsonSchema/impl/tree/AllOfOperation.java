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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.jetbrains.jsonSchema.internal.JsonSchemaVariantsTreeBuilder.andGroups;

public final class AllOfOperation extends Operation {
    private final JsonSchemaService myService;

    public AllOfOperation(JsonSchemaObject sourceNode, JsonSchemaService service, @Nullable JsonSchemaNodeExpansionRequest expansionRequest) {
        super(sourceNode, expansionRequest);
        myService = service;
    }

    @Override
    public void map(Set<JsonSchemaObject> visited) {
        List<? extends JsonSchemaObject> allOf = mySourceNode.getAllOf();
        assert allOf != null;
        myChildOperations.addAll(ContainerUtil.map(allOf, sourceNode -> new ProcessDefinitionsOperation(sourceNode, myService, myExpansionRequest)));
    }

    private static <T> int maxSize(List<List<T>> items) {
        if (items.isEmpty()) {
            return 0;
        }
        int maxsize = -1;
        for (List<T> item : items) {
            int size = item.size();
            if (maxsize < size) {
                maxsize = size;
            }
        }
        return maxsize;
    }

    @Override
    public void reduce() {
        myAnyOfGroup.add(mySourceNode);

        for (Operation op : myChildOperations) {
            if (!op.myState.equals(SchemaResolveState.normal)) {
                continue;
            }

            List<JsonSchemaObject> mergedAny = andGroups(op.myAnyOfGroup, myAnyOfGroup);
            if (mergedAny.isEmpty() && !op.myAnyOfGroup.isEmpty()) {
                mergedAny.addAll(op.myAnyOfGroup);
            }

            List<List<JsonSchemaObject>> mergedExclusive =
              new ArrayList<>(op.myAnyOfGroup.size() * maxSize(myOneOfGroup) +
                              myAnyOfGroup.size() * maxSize(op.myOneOfGroup) +
                              maxSize(myOneOfGroup) * maxSize(op.myOneOfGroup));

            for (List<JsonSchemaObject> objects : myOneOfGroup) {
                mergedExclusive.add(andGroups(op.myAnyOfGroup, objects));
            }
            for (List<JsonSchemaObject> objects : op.myOneOfGroup) {
                mergedExclusive.add(andGroups(objects, myAnyOfGroup));
            }
            for (List<JsonSchemaObject> group : op.myOneOfGroup) {
                for (List<JsonSchemaObject> otherGroup : myOneOfGroup) {
                    mergedExclusive.add(andGroups(group, otherGroup));
                }
            }

            myAnyOfGroup.clear();
            myOneOfGroup.clear();
            myAnyOfGroup.addAll(mergedAny);
            myOneOfGroup.addAll(mergedExclusive);
        }
    }
}
