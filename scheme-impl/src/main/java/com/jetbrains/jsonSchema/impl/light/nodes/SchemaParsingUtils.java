// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.io.URLUtil;
import com.jetbrains.jsonSchema.impl.EnumArrayValueWrapper;
import com.jetbrains.jsonSchema.impl.EnumObjectValueWrapper;
import com.jetbrains.jsonSchema.impl.light.RawJsonSchemaNodeAccessor;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

class JacksonSchemaNodeAccessor implements RawJsonSchemaNodeAccessor<JsonNode> {
  private static final Logger LOG = Logger.getInstance(JacksonSchemaNodeAccessor.class);
  public static final JacksonSchemaNodeAccessor INSTANCE = new JacksonSchemaNodeAccessor();

  private JacksonSchemaNodeAccessor() {
  }

  @Override
  @Nullable
  public JsonNode resolveNode(@Nonnull JsonNode rootNode, @Nonnull String absoluteNodeJsonPointer) {
    JsonPointer compiledPointer = escapeAndCompileJsonPointer(absoluteNodeJsonPointer);
    if (compiledPointer == null) return null;

    JsonNode result = rootNode.at(compiledPointer);
    return (result instanceof MissingNode) ? null : result;
  }

  @Override
  @Nonnull
  public JsonNode resolveRelativeNode(@Nonnull JsonNode node, @Nullable String relativeChildPath) {
    return getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
  }

  @Override
  public boolean hasChildNode(@Nonnull JsonNode node, @Nonnull String relativeChildPath) {
    return node.isObject() && !getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath).isMissingNode();
  }

  @Override
  @Nullable
  public String readUntypedNodeValueAsText(@Nonnull JsonNode node, @Nullable String relativeChildPath) {
    if (!node.isObject() && relativeChildPath != null) return null;

    JsonNode targetNode = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
    if (targetNode instanceof MissingNode) return null;

    return targetNode.toPrettyString();
  }

  @Override
  @Nullable
  public String readTextNodeValue(@Nonnull JsonNode node, @Nullable String relativeChildPath) {
    if (!node.isObject() && relativeChildPath != null) return null;

    JsonNode maybeString = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
    return maybeString.isTextual() ? maybeString.asText() : null;
  }

  @Override
  @Nullable
  public Boolean readBooleanNodeValue(@Nonnull JsonNode node, @Nullable String relativeChildPath) {
    if (!(node.isObject() || (node.isBoolean() && relativeChildPath == null))) return null;

    JsonNode maybeBoolean = relativeChildPath == null ? node : getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
    return maybeBoolean.isBoolean() ? maybeBoolean.asBoolean() : null;
  }

  @Override
  @Nullable
  public Number readNumberNodeValue(@Nonnull JsonNode node, @Nullable String relativeChildPath) {
    if (!node.isObject() && relativeChildPath != null) return null;

    JsonNode maybeNumber = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
    if (maybeNumber.isInt()) return maybeNumber.asInt();
    if (maybeNumber.isDouble()) return maybeNumber.asDouble();
    if (maybeNumber.isLong()) return maybeNumber.asLong();
    return null;
  }

  @Override
  @Nullable
  public Iterable<Object> readUntypedNodesCollection(@Nonnull JsonNode node, @Nullable String relativeChildPath) {
    Iterable<JsonNode> childArrayItems = getChildArrayItems(node, relativeChildPath);
    if (childArrayItems == null) return null;

    List<Object> result = new ArrayList<>();
    for (JsonNode item : childArrayItems) {
      Object value = readAnything(item);
      if (value != null) {
        result.add(value);
      }
    }
    return result;
  }

  @Override
  @Nullable
  public Iterable<Map.Entry<String, JsonNode>> readNodeAsMapEntries(@Nonnull JsonNode node, @Nullable String relativeChildPath) {
    if (!node.isObject() && relativeChildPath != null) return null;

    JsonNode targetNode = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
    if (!targetNode.isObject()) return null;

    List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
    Iterator<Map.Entry<String, JsonNode>> fields = targetNode.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      entries.add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
    }
    return entries;
  }

  @Override
  @Nullable
  public Iterable<Map.Entry<String, List<String>>> readNodeAsMultiMapEntries(@Nonnull JsonNode node, @Nullable String relativeChildPath) {
    Iterable<Map.Entry<String, JsonNode>> mapEntries = readNodeAsMapEntries(node, relativeChildPath);
    if (mapEntries == null) return null;

    List<Map.Entry<String, List<String>>> result = new ArrayList<>();
    for (Map.Entry<String, JsonNode> entry : mapEntries) {
      JsonNode arrayValue = entry.getValue();
      if (!arrayValue.isArray()) continue;

      List<String> values = new ArrayList<>();
      for (JsonNode element : arrayValue) {
        if (element.isTextual()) {
          values.add(element.asText());
        }
      }
      result.add(new AbstractMap.SimpleEntry<>(entry.getKey(), values));
    }
    return result;
  }

  @Override
  @Nullable
  public Iterable<String> readNodeKeys(@Nonnull JsonNode node, @Nullable String relativeChildPath) {
    JsonNode expandedNode = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
    Iterator<String> fieldNames = expandedNode.fieldNames();
    if (!fieldNames.hasNext()) return null;

    List<String> keys = new ArrayList<>();
    fieldNames.forEachRemaining(keys::add);
    return keys;
  }

  @Nonnull
  private JsonNode getExistingChildByNonEmptyPathOrSelf(@Nonnull JsonNode node, @Nullable String directChildName) {
    if (directChildName == null) return node;

    JsonNode child = node.get(directChildName);
    return child != null ? child : MissingNode.getInstance();
  }

  @Nullable
  private Iterable<JsonNode> getChildArrayItems(@Nonnull JsonNode node, @Nullable String relativeChildPath) {
    if (!node.isObject() && relativeChildPath != null) return null;

    JsonNode targetNode = getExistingChildByNonEmptyPathOrSelf(node, relativeChildPath);
    if (!targetNode.isArray()) return null;

    if (targetNode instanceof ArrayNode) {
      ArrayNode arrayNode = (ArrayNode) targetNode;
      List<JsonNode> elements = new ArrayList<>();
      arrayNode.elements().forEachRemaining(elements::add);
      return elements;
    }
    return null;
  }

  @Nullable
  private Object readAnything(@Nonnull JsonNode node) {
    if (node.isTextual()) return asDoubleQuotedTextOrNull(node);
    if (node.isNull()) return node.asText();
    if (node.isInt()) return node.asInt();
    if (node.isLong()) return node.asLong();
    if (node.isDouble()) return node.asDouble();
    if (node.isBoolean()) return node.asBoolean();
    if (node.isObject()) {
      Map<String, Object> map = new HashMap<>();
      Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        Object value = readAnything(entry.getValue());
        if (value != null) {
          map.put(entry.getKey(), value);
        }
      }
      return new EnumObjectValueWrapper(map);
    }
    if (node.isArray()) {
      List<Object> list = new ArrayList<>();
      for (JsonNode element : node) {
        Object value = readAnything(element);
        if (value != null) {
          list.add(value);
        }
      }
      return new EnumArrayValueWrapper(list.toArray());
    }
    return null;
  }

  @Nullable
  private String asDoubleQuotedTextOrNull(@Nonnull JsonNode jsonNode) {
    if (!jsonNode.isTextual()) return null;
    return asDoubleQuotedString(jsonNode.asText());
  }

  @Nullable
  private JsonPointer escapeAndCompileJsonPointer(@Nonnull String unescapedPointer) {
    if (!fastCheckIfCorrectPointer(unescapedPointer)) return null;

    try {
      return JsonPointer.compile(adaptJsonPointerToJacksonImplementation(unescapedPointer));
    } catch (IllegalArgumentException exception) {
      LOG.warn("Unable to compile json pointer. Resolve aborted.", exception);
      return null;
    }
  }

  private boolean fastCheckIfCorrectPointer(@Nonnull String maybeIncorrectPointer) {
    return maybeIncorrectPointer.startsWith("/");
  }

  @Nonnull
  private String adaptJsonPointerToJacksonImplementation(@Nonnull String oldPointer) {
    if (oldPointer.equals("/")) return "";
    return URLUtil.unescapePercentSequences(oldPointer);
  }

  @Nonnull
  public static String asUnquotedString(@Nonnull String str) {
    return StringUtil.unquoteString(str);
  }

  @Nonnull
  public static String asDoubleQuotedString(@Nonnull String str) {
    String unquoted = asUnquotedString(str);
    return "\"" + unquoted + "\"";
  }

  @Nonnull
  public static String escapeForbiddenJsonPointerSymbols(@Nonnull String pointerSegment) {
    return pointerSegment.replace("~", "~0").replace("/", "~1");
  }
}
