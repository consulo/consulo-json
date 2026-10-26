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
package com.jetbrains.jsonSchema.impl.light;

import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

/**
 * An interface to encapsulate all schema traversal methods to be able to replace the implementation without changing an existing API
 */
public interface RawJsonSchemaNodeAccessor<T> {
    /**
     * Resolve raw schema node from the given schema root by the given node's json pointer
     */
    @Nullable
    T resolveNode(T rootNode, String absoluteNodeJsonPointer);

    /**
     * Resolve raw schema node from the given schema node by the given node's name
     */
    @Nullable
    T resolveRelativeNode(T node, @Nullable String relativeChildPath);

    @Nullable
    default T resolveRelativeNode(T node) {
        return resolveRelativeNode(node, null);
    }

    boolean hasChildNode(T node, String relativeChildPath);

    @Nullable
    String readTextNodeValue(T node, @Nullable String relativeChildPath);

    @Nullable
    default String readTextNodeValue(T node) {
        return readTextNodeValue(node, null);
    }

    @Nullable
    Boolean readBooleanNodeValue(T node, @Nullable String relativeChildPath);

    @Nullable
    default Boolean readBooleanNodeValue(T node) {
        return readBooleanNodeValue(node, null);
    }

    @Nullable
    Number readNumberNodeValue(T node, @Nullable String relativeChildPath);

    @Nullable
    default Number readNumberNodeValue(T node) {
        return readNumberNodeValue(node, null);
    }

    @Nullable
    String readUntypedNodeValueAsText(T node, @Nullable String relativeChildPath);

    @Nullable
    default String readUntypedNodeValueAsText(T node) {
        return readUntypedNodeValueAsText(node, null);
    }

    @Nullable
    Stream<String> readNodeKeys(T node, @Nullable String relativeChildPath);

    @Nullable
    default Stream<String> readNodeKeys(T node) {
        return readNodeKeys(node, null);
    }

    @Nullable
    Stream<Object> readUntypedNodesCollection(T node, @Nullable String relativeChildPath);

    @Nullable
    default Stream<Object> readUntypedNodesCollection(T node) {
        return readUntypedNodesCollection(node, null);
    }

    @Nullable
    Stream<Pair<String, T>> readNodeAsMapEntries(T node, @Nullable String relativeChildPath);

    @Nullable
    default Stream<Pair<String, T>> readNodeAsMapEntries(T node) {
        return readNodeAsMapEntries(node, null);
    }

    @Nullable
    Stream<Pair<String, List<String>>> readNodeAsMultiMapEntries(T node, @Nullable String relativeChildPath);

    @Nullable
    default Stream<Pair<String, List<String>>> readNodeAsMultiMapEntries(T node) {
        return readNodeAsMultiMapEntries(node, null);
    }
}
