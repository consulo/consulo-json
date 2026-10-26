// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema.internal;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import consulo.util.collection.primitive.ints.IntMaps;
import consulo.util.collection.primitive.ints.IntObjectMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public final class MatchResult {
    public final List<JsonSchemaObject> mySchemas;
    public final List<Collection<? extends JsonSchemaObject>> myExcludingSchemas;

    private MatchResult(List<JsonSchemaObject> schemas, List<Collection<? extends JsonSchemaObject>> excludingSchemas) {
        mySchemas = Collections.unmodifiableList(schemas);
        myExcludingSchemas = Collections.unmodifiableList(excludingSchemas);
    }

    public static MatchResult create(JsonSchemaTreeNode root) {
        List<JsonSchemaObject> schemas = new ArrayList<>();
        IntObjectMap<List<JsonSchemaObject>> oneOfGroups = IntMaps.newIntObjectHashMap();
        iterateTree(root, node -> {
            if (node.isAny()) {
                return true;
            }
            int groupNumber = node.getExcludingGroupNumber();
            if (groupNumber < 0) {
                schemas.add(node.getSchema());
            }
            else {
                List<JsonSchemaObject> group = oneOfGroups.get(groupNumber);
                if (group == null) {
                    group = new ArrayList<>();
                    oneOfGroups.put(groupNumber, group);
                }
                group.add(node.getSchema());
            }
            return true;
        });
        List<Collection<? extends JsonSchemaObject>> result;
        if (oneOfGroups.isEmpty()) {
            result = Collections.emptyList();
        }
        else {
            result = new ArrayList<>(oneOfGroups.values());
        }
        return new MatchResult(schemas, result);
    }

    public static void iterateTree(JsonSchemaTreeNode root,
                                   Predicate<? super JsonSchemaTreeNode> processor) {
        ArrayDeque<JsonSchemaTreeNode> queue = new ArrayDeque<>(root.getChildren());
        while (!queue.isEmpty()) {
            JsonSchemaTreeNode node = queue.removeFirst();
            if (node.getChildren().isEmpty()) {
                if (!node.isNothing() && SchemaResolveState.normal.equals(node.getResolveState()) && !processor.test(node)) {
                    break;
                }
            }
            else {
                queue.addAll(node.getChildren());
            }
        }
    }
}
