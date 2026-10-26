// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema.impl.light.nodes;

import tools.jackson.core.JsonPointer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.MissingNode;
import com.jetbrains.jsonSchema.impl.light.RawJsonSchemaNodeAccessor;
import com.jetbrains.jsonSchema.internal.EnumArrayValueWrapper;
import com.jetbrains.jsonSchema.internal.EnumObjectValueWrapper;
import consulo.logging.Logger;
import consulo.util.io.URLUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public final class JacksonSchemaNodeAccessor implements RawJsonSchemaNodeAccessor<JsonNode> {
    public static final JacksonSchemaNodeAccessor INSTANCE = new JacksonSchemaNodeAccessor();

    private static final Logger LOG = Logger.getInstance(JacksonSchemaNodeAccessor.class);

    private JacksonSchemaNodeAccessor() {
    }

    @Override
    public @Nullable JsonNode resolveNode(JsonNode rootNode, String absoluteNodeJsonPointer) {
        JsonPointer compiledPointer = escapeAndCompileJsonPointer(absoluteNodeJsonPointer);
        if (compiledPointer == null) {
            return null;
        }
        JsonNode resolved = rootNode.at(compiledPointer);
        return resolved instanceof MissingNode ? null : resolved;
    }

    @Override
    public JsonNode resolveRelativeNode(JsonNode node, @Nullable String relativeChildPath) {
        return getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
    }

    @Override
    public boolean hasChildNode(JsonNode node, String relativeChildPath) {
        return node.isObject() && !getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath).isMissingNode();
    }

    @Override
    public @Nullable String readUntypedNodeValueAsText(JsonNode node, @Nullable String relativeChildPath) {
        if (!node.isObject() && relativeChildPath != null) {
            return null;
        }
        JsonNode child = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
        return child instanceof MissingNode ? null : child.toPrettyString();
    }

    @Override
    public @Nullable String readTextNodeValue(JsonNode node, @Nullable String relativeChildPath) {
        if (!node.isObject() && relativeChildPath != null) {
            return null;
        }
        JsonNode maybeString = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
        return maybeString.isString() ? maybeString.stringValue() : null;
    }

    @Override
    public @Nullable Boolean readBooleanNodeValue(JsonNode node, @Nullable String relativeChildPath) {
        if (!(node.isObject() || node.isBoolean() && relativeChildPath == null)) {
            return null;
        }
        JsonNode maybeBoolean = relativeChildPath == null ? node : getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
        return maybeBoolean.isBoolean() ? maybeBoolean.asBoolean() : null;
    }

    @Override
    public @Nullable Number readNumberNodeValue(JsonNode node, @Nullable String relativeChildPath) {
        if (!node.isObject() && relativeChildPath != null) {
            return null;
        }
        JsonNode maybeNumber = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
        if (maybeNumber.isInt()) {
            return maybeNumber.asInt();
        }
        if (maybeNumber.isDouble()) {
            return maybeNumber.asDouble();
        }
        if (maybeNumber.isLong()) {
            return maybeNumber.asLong();
        }
        return null;
    }

    @Override
    public @Nullable Stream<Object> readUntypedNodesCollection(JsonNode node, @Nullable String relativeChildPath) {
        Stream<JsonNode> items = getChildArrayItems(node, relativeChildPath);
        if (items == null) {
            return null;
        }
        return items.map(JacksonSchemaNodeAccessor::readAnything).filter(Objects::nonNull);
    }

    @Override
    public @Nullable Stream<Pair<String, JsonNode>> readNodeAsMapEntries(JsonNode node, @Nullable String relativeChildPath) {
        if (!node.isObject() && relativeChildPath != null) {
            return null;
        }
        JsonNode expandedNode = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
        if (!expandedNode.isObject()) {
            return null;
        }
        return expandedNode.properties().stream().map(entry -> Pair.create(entry.getKey(), entry.getValue()));
    }

    @Override
    public @Nullable Stream<Pair<String, List<String>>> readNodeAsMultiMapEntries(JsonNode node, @Nullable String relativeChildPath) {
        Stream<Pair<String, JsonNode>> entries = readNodeAsMapEntries(node, relativeChildPath);
        if (entries == null) {
            return null;
        }
        return entries
            .filter(entry -> entry.getSecond().isArray())
            .map(entry -> Pair.create(
                entry.getFirst(),
                entry.getSecond().values().stream().filter(JsonNode::isString).map(JsonNode::stringValue).toList()
            ));
    }

    @Override
    public @Nullable Stream<String> readNodeKeys(JsonNode node, @Nullable String relativeChildPath) {
        JsonNode expandedNode = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
        Collection<String> propertyNames = expandedNode.propertyNames();
        return propertyNames.isEmpty() ? null : propertyNames.stream();
    }

    private static JsonNode getExistingChildByNonEmptyPathOrSelf(JsonNode node, @Nullable String directChildName) {
        if (directChildName == null) {
            return node;
        }
        JsonNode child = node.get(directChildName);
        return child != null ? child : MissingNode.getInstance();
    }

    private static @Nullable Stream<JsonNode> getChildArrayItems(JsonNode node, @Nullable String relativeChildPath) {
        if (!node.isObject() && relativeChildPath != null) {
            return null;
        }
        JsonNode child = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
        if (!child.isArray() || !(child instanceof ArrayNode arrayNode)) {
            return null;
        }
        return arrayNode.values().stream();
    }

    private static @Nullable Object readAnything(JsonNode node) {
        if (node.isString()) {
            return asDoubleQuotedTextOrNull(node);
        }
        if (node.isNull()) {
            return "null";
        }
        if (node.isInt()) {
            return node.asInt();
        }
        if (node.isLong()) {
            return node.asLong();
        }
        if (node.isDouble()) {
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isObject()) {
            Map<String, @Nullable Object> values = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                values.put(entry.getKey(), readAnything(entry.getValue()));
            }
            return new EnumObjectValueWrapper(values);
        }
        if (node.isArray()) {
            return new EnumArrayValueWrapper(node.values().stream().map(JacksonSchemaNodeAccessor::readAnything)
                .filter(Objects::nonNull)
                .toArray());
        }
        return null;
    }

    private static @Nullable String asDoubleQuotedTextOrNull(JsonNode jsonNode) {
        if (!jsonNode.isString()) {
            return null;
        }
        return asDoubleQuotedString(jsonNode.stringValue());
    }

    private static @Nullable JsonPointer escapeAndCompileJsonPointer(String unescapedPointer) {
        if (!fastCheckIfCorrectPointer(unescapedPointer)) {
            return null;
        }
        try {
            return JsonPointer.compile(adaptJsonPointerToJacksonImplementation(unescapedPointer));
        }
        catch (IllegalArgumentException exception) {
            LOG.warn("Unable to compile json pointer. Resolve aborted.", exception);
            return null;
        }
    }

    private static boolean fastCheckIfCorrectPointer(String maybeIncorrectPointer) {
        return maybeIncorrectPointer.startsWith("/");
    }

    private static String adaptJsonPointerToJacksonImplementation(String oldPointer) {
        if (oldPointer.equals("/")) {
            return "";
        }
        return URLUtil.unescapePercentSequences(oldPointer);
    }

    public static String asUnquotedString(String value) {
        return StringUtil.unquoteString(value);
    }

    public static String asDoubleQuotedString(String value) {
        return "\"" + asUnquotedString(value) + "\"";
    }

    public static String escapeForbiddenJsonPointerSymbols(String pointerSegment) {
        return pointerSegment.replace("~", "~0").replace("/", "~1");
    }
}
