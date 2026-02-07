// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import consulo.logging.Logger;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nullable;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class JacksonSchemaNodeAccessor implements RawJsonSchemaNodeAccessor<JsonNode> {
  public static final JacksonSchemaNodeAccessor INSTANCE = new JacksonSchemaNodeAccessor();
  private static final Logger LOG = Logger.getInstance(JacksonSchemaNodeAccessor.class);

  private JacksonSchemaNodeAccessor() {
  }

  @Override
  @Nullable
  public JsonNode resolveNode(JsonNode rootNode, String absoluteNodeJsonPointer) {
    JsonPointer compiledPointer = escapeAndCompileJsonPointer(absoluteNodeJsonPointer);
    if (compiledPointer == null) return null;
    JsonNode result = rootNode.at(compiledPointer);
    return (result != null && !(result instanceof MissingNode)) ? result : null;
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
  @Nullable
  public String readUntypedNodeValueAsText(JsonNode node, @Nullable String relativeChildPath) {
    if (!node.isObject() && relativeChildPath != null) return null;
    JsonNode child = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
    return (child instanceof MissingNode) ? null : child.toPrettyString();
  }

  @Override
  @Nullable
  public String readTextNodeValue(JsonNode node, @Nullable String relativeChildPath) {
    if (!node.isObject() && relativeChildPath != null) return null;
    JsonNode maybeString = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
    return maybeString.isTextual() ? maybeString.asText() : null;
  }

  @Override
  @Nullable
  public Boolean readBooleanNodeValue(JsonNode node, @Nullable String relativeChildPath) {
    if (!(node.isObject() || (node.isBoolean() && relativeChildPath == null))) return null;
    JsonNode maybeBoolean = (relativeChildPath == null) ? node : getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
    return maybeBoolean.isBoolean() ? maybeBoolean.asBoolean() : null;
  }

  @Override
  @Nullable
  public Number readNumberNodeValue(JsonNode node, @Nullable String relativeChildPath) {
    if (!node.isObject() && relativeChildPath != null) return null;
    JsonNode maybeNumber = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
    if (maybeNumber.isInt()) return maybeNumber.asInt();
    if (maybeNumber.isDouble()) return maybeNumber.asDouble();
    if (maybeNumber.isLong()) return maybeNumber.asLong();
    return null;
  }

  @Override
  @Nullable
  public Stream<Object> readUntypedNodesCollection(JsonNode node, @Nullable String relativeChildPath) {
    Stream<JsonNode> items = getChildArrayItems(node, relativeChildPath);
    return items != null ? items.map(this::readAnything).filter(Objects::nonNull) : null;
  }

  @Override
  @Nullable
  public Stream<consulo.util.lang.Pair<String, JsonNode>> readNodeAsMapEntries(JsonNode node, @Nullable String relativeChildPath) {
    if (!node.isObject() && relativeChildPath != null) return null;
    JsonNode expandedNode = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
    if (!expandedNode.isObject()) return null;

    return StreamSupport.stream(
      Spliterators.spliteratorUnknownSize(expandedNode.fields(), Spliterator.ORDERED),
      false
    ).map(entry -> consulo.util.lang.Pair.create(entry.getKey(), entry.getValue()));
  }

  @Override
  @Nullable
  public Stream<consulo.util.lang.Pair<String, List<String>>> readNodeAsMultiMapEntries(JsonNode node, @Nullable String relativeChildPath) {
    Stream<consulo.util.lang.Pair<String, JsonNode>> entries = readNodeAsMapEntries(node, relativeChildPath);
    if (entries == null) return null;

    return entries.map(pair -> {
      String key = pair.getFirst();
      JsonNode arrayValue = pair.getSecond();
      if (!arrayValue.isArray()) return null;

      List<String> values = StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(arrayValue.elements(), Spliterator.ORDERED),
        false
      ).filter(JsonNode::isTextual)
       .map(JsonNode::asText)
       .collect(Collectors.toList());

      return consulo.util.lang.Pair.create(key, values);
    }).filter(Objects::nonNull);
  }

  @Override
  @Nullable
  public Stream<String> readNodeKeys(JsonNode node, @Nullable String relativeChildPath) {
    JsonNode expandedNode = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
    Iterator<String> fieldNames = expandedNode.fieldNames();
    return fieldNames.hasNext() ?
      StreamSupport.stream(Spliterators.spliteratorUnknownSize(fieldNames, Spliterator.ORDERED), false) : null;
  }

  private JsonNode getExistingChildByNonEmptyPathOrSelf(JsonNode node, @Nullable String directChildName) {
    if (directChildName == null) return node;
    JsonNode child = node.get(directChildName);
    return child != null ? child : MissingNode.getInstance();
  }

  @Nullable
  private Stream<JsonNode> getChildArrayItems(JsonNode node, @Nullable String relativeChildPath) {
    if (!node.isObject() && relativeChildPath != null) return null;
    JsonNode child = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
    if (!child.isArray()) return null;
    if (!(child instanceof ArrayNode)) return null;

    return StreamSupport.stream(
      Spliterators.spliteratorUnknownSize(child.elements(), Spliterator.ORDERED),
      false
    );
  }

  @Nullable
  private Object readAnything(JsonNode node) {
    if (node.isTextual()) return asDoubleQuotedTextOrNull(node);
    if (node.isNull()) return node.asText();
    if (node.isInt()) return node.asInt();
    if (node.isLong()) return node.asLong();
    if (node.isDouble()) return node.asDouble();
    if (node.isBoolean()) return node.asBoolean();
    if (node.isObject()) {
      Map<String, Object> map = StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(node.fields(), Spliterator.ORDERED),
        false
      ).map(entry -> {
        Object value = readAnything(entry.getValue());
        return value != null ? consulo.util.lang.Pair.create(entry.getKey(), value) : null;
      })
      .filter(Objects::nonNull)
      .collect(Collectors.toMap(consulo.util.lang.Pair::getFirst, consulo.util.lang.Pair::getSecond));
      return new EnumObjectValueWrapper(map);
    }
    if (node.isArray()) {
      Object[] array = StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(node.elements(), Spliterator.ORDERED),
        false
      ).map(this::readAnything)
       .filter(Objects::nonNull)
       .toArray();
      return new EnumArrayValueWrapper(array);
    }
    return null;
  }

  @Nullable
  private String asDoubleQuotedTextOrNull(JsonNode jsonNode) {
    return jsonNode.isTextual() ? asDoubleQuotedString(jsonNode.asText()) : null;
  }

  @Nullable
  private JsonPointer escapeAndCompileJsonPointer(String unescapedPointer) {
    if (!fastCheckIfCorrectPointer(unescapedPointer)) return null;
    try {
      return JsonPointer.compile(adaptJsonPointerToJacksonImplementation(unescapedPointer));
    }
    catch (IllegalArgumentException exception) {
      LOG.warn("Unable to compile json pointer. Resolve aborted.", exception);
      return null;
    }
  }

  private boolean fastCheckIfCorrectPointer(String maybeIncorrectPointer) {
    return maybeIncorrectPointer.startsWith("/");
  }

  private String adaptJsonPointerToJacksonImplementation(String oldPointer) {
    if (oldPointer.equals("/")) return "";
    return URLDecoder.decode(oldPointer, StandardCharsets.UTF_8);
  }

  // Extension function equivalents as static methods
  public static String asUnquotedString(String str) {
    return StringUtil.unquoteString(str);
  }

  public static String asDoubleQuotedString(String str) {
    return "\"" + asUnquotedString(str) + "\"";
  }

  public static String escapeForbiddenJsonPointerSymbols(String pointerSegment) {
    return pointerSegment.replace("~", "~0").replace("/", "~1");
  }
}
