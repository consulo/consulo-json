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
package com.jetbrains.jsonSchema.impl.nestedCompletions;

import com.intellij.json.pointer.JsonPointerPosition;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Represents a tree structure of how completions can be nested through a schema.
 *
 * If you request completions in a configuration node that has a corresponding {@link com.jetbrains.jsonSchema.JsonSchemaObject}
 * as well as a corresponding {@link NestedCompletionsNode}, you will see completions for the entire subtree of the NestedCompletionsNode.
 * (subtrees are not expanded below {@link ChildNode.Isolated} nodes)
 *
 * See tests for details
 */
public final class NestedCompletionsNode {
    private final List<ChildNode> myChildren;

    public NestedCompletionsNode(List<ChildNode> children) {
        myChildren = children;
    }

    public List<ChildNode> getChildren() {
        return myChildren;
    }

    public static NestedCompletionsNode buildNestedCompletionsTree(Consumer<NestedCompletionsNodeBuilder> block) {
        List<ChildNode> children = new ArrayList<>();

        NestedCompletionsNodeBuilder builder = new NestedCompletionsNodeBuilder() {
            @Override
            public void isolated(String name, Consumer<NestedCompletionsNodeBuilder> childBuilder) {
                children.add(new ChildNode.Isolated.NamedNode(name, buildNestedCompletionsTree(childBuilder)));
            }

            @Override
            public void isolated(Pattern regex, Consumer<NestedCompletionsNodeBuilder> childBuilder) {
                children.add(new ChildNode.Isolated.RegexNode(regex, buildNestedCompletionsTree(childBuilder)));
            }

            @Override
            public void open(String name, Consumer<NestedCompletionsNodeBuilder> childBuilder) {
                children.add(new ChildNode.OpenNode(name, buildNestedCompletionsTree(childBuilder)));
            }
        };

        block.accept(builder);
        return new NestedCompletionsNode(children);
    }

    public NestedCompletionsNode merge(NestedCompletionsNode other) {
        List<ChildNode> merged = new ArrayList<>(myChildren);
        merged.addAll(other.myChildren);
        return new NestedCompletionsNode(merged);
    }

    public static @Nullable NestedCompletionsNode navigate(@Nullable NestedCompletionsNode node, JsonPointerPosition jsonPointer) {
        if (node == null) {
            return null;
        }
        return node.navigate(0, toPathItems(jsonPointer));
    }

    private static List<String> toPathItems(JsonPointerPosition position) {
        String pointer = position.toJsonPointer();
        if (pointer == null || pointer.equals("/")) {
            return Collections.emptyList();
        }
        String[] parts = pointer.substring(1).split("/");
        return List.of(parts);
    }

    private @Nullable NestedCompletionsNode navigate(int index, List<String> steps) {
        if (index >= steps.size()) {
            return this;
        }

        List<ChildNode> matchingNodes = new ArrayList<>();
        for (ChildNode child : myChildren) {
            if (matches(child, steps.get(index))) {
                matchingNodes.add(child);
            }
        }

        ChildNode preferredChild = getPreferredChild(matchingNodes);
        if (preferredChild == null) {
            return null;
        }

        return preferredChild.getNode().navigate(index + 1, steps);
    }

    // some schemas provide both named and regex nodes for the same name
    // we need to prioritize named options over regex options
    private static @Nullable ChildNode getPreferredChild(Collection<ChildNode> nodes) {
        for (ChildNode node : nodes) {
            if (node instanceof ChildNode.NamedChildNode) {
                return node;
            }
        }
        return nodes.isEmpty() ? null : nodes.iterator().next();
    }

    private static boolean matches(ChildNode node, String name) {
        if (node instanceof ChildNode.Isolated.RegexNode regexNode) {
            return regexNode.getRegex().matcher(name).matches();
        }
        else if (node instanceof ChildNode.NamedChildNode namedChildNode) {
            return namedChildNode.getName().equals(name);
        }
        return false;
    }
}
