// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes;

import com.fasterxml.jackson.databind.JsonNode;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.RootJsonSchemaObject;
import com.jetbrains.jsonSchema.impl.light.SchemaKeywords;
import com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.X_INTELLIJ_LANGUAGE_INJECTION;
import static com.jetbrains.jsonSchema.impl.light.nodes.JacksonSchemaNodeAccessor.escapeForbiddenJsonPointerSymbols;
import static com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy.computeJsonSchemaVersion;

public class RootJsonSchemaObjectBackedByJackson extends JsonSchemaObjectBackedByJacksonBase
  implements RootJsonSchemaObject<JsonNode, JsonSchemaObjectBackedByJacksonBase> {

  private static final Key<Map<String, String>> IDS_MAP_KEY = Key.create("ids");
  private static final Key<Map<String, String>> DYNAMIC_ANCHORS_MAP_KEY = Key.create("dynamicAnchors");
  private static final Key<Boolean> INJECTIONS_MAP_KEY = Key.create("injections");
  private static final Key<Boolean> DEPRECATIONS_MAP_KEY = Key.create("deprecations");
  private static final Key<String> FILE_URL_MAP_KEY = Key.create("fileUrl");

  private final VirtualFile schemaFile;
  private final JsonSchemaObjectBackedByJacksonFactory schemaObjectFactory;
  private final JsonSchemaInterpretationStrategy schemaInterpretationStrategy;

  public RootJsonSchemaObjectBackedByJackson(@NotNull JsonNode rootNode, @Nullable VirtualFile schemaFile) {
    super(rootNode, SchemaKeywords.SCHEMA_ROOT_POINTER);
    this.schemaFile = schemaFile;
    this.schemaObjectFactory = new JsonSchemaObjectBackedByJacksonFactory(this);
    this.schemaInterpretationStrategy = computeJsonSchemaVersion(getSchema());
  }

  @Nullable
  public VirtualFile getSchemaFile() {
    return schemaFile;
  }

  @Override
  @NotNull
  public JsonSchemaInterpretationStrategy getSchemaInterpretationStrategy() {
    return schemaInterpretationStrategy;
  }

  @Override
  @Nullable
  public JsonSchemaObjectBackedByJacksonBase getChildSchemaObjectByName(@NotNull JsonSchemaObjectBackedByJacksonBase parentSchemaObject,
                                                                         @NotNull String... childNodeRelativePointer) {
    return schemaObjectFactory.getChildSchemaObjectByName(parentSchemaObject, childNodeRelativePointer);
  }

  @Override
  @Nullable
  public JsonSchemaObject getSchemaObjectByAbsoluteJsonPointer(@NotNull String jsonPointer) {
    return schemaObjectFactory.getSchemaObjectByAbsoluteJsonPointer(jsonPointer);
  }

  public boolean checkHasInjections() {
    return getOrComputeValue(INJECTIONS_MAP_KEY, () -> {
      Stream<Boolean> results = indexSchema(getRawSchemaNode(), Collections.emptyList(), (node, parentPointer) -> {
        if (!parentPointer.isEmpty() && parentPointer.get(parentPointer.size() - 1).equals(X_INTELLIJ_LANGUAGE_INJECTION)) {
          return true;
        }
        return null;
      });
      return results.findAny().orElse(false);
    });
  }

  public boolean checkHasDeprecations() {
    String deprecationMarker = schemaInterpretationStrategy.getDeprecationKeyword();
    if (deprecationMarker == null) return false;

    return getOrComputeValue(DEPRECATIONS_MAP_KEY, () -> {
      Stream<Boolean> results = indexSchema(getRawSchemaNode(), Collections.emptyList(), (node, parentPointer) -> {
        if (!parentPointer.isEmpty() && parentPointer.get(parentPointer.size() - 1).equals(deprecationMarker)) {
          return true;
        }
        return null;
      });
      return results.findAny().orElse(false);
    });
  }

  @Override
  @Nullable
  public String getFileUrl() {
    String url = getOrComputeValue(FILE_URL_MAP_KEY, () -> schemaFile != null ? schemaFile.getUrl() : "");
    return url.isEmpty() ? null : url;
  }

  @Override
  @Nullable
  public VirtualFile getRawFile() {
    return schemaFile;
  }

  @Override
  @NotNull
  public RootJsonSchemaObjectBackedByJackson getRootSchemaObject() {
    return this;
  }

  @Override
  @Nullable
  public String resolveId(@NotNull String id) {
    String schemaFeature = schemaInterpretationStrategy.getIdKeyword();
    if (schemaFeature == null) return null;
    return collectValuesWithKey(schemaFeature, IDS_MAP_KEY).get(id);
  }

  @Override
  @Nullable
  public String resolveDynamicAnchor(@NotNull String anchor) {
    String schemaFeature = schemaInterpretationStrategy.getDynamicAnchorKeyword();
    if (schemaFeature == null) return null;
    return collectValuesWithKey(schemaFeature, DYNAMIC_ANCHORS_MAP_KEY).get(anchor);
  }

  @NotNull
  private Map<String, String> collectValuesWithKey(@NotNull String expectedKey, @NotNull Key<Map<String, String>> storeIn) {
    return getOrComputeValue(storeIn, () -> {
      Stream<Map.Entry<String, String>> entries = indexSchema(getRawSchemaNode(), Collections.emptyList(), (node, parentPointer) -> {
        if (!node.isTextual() || parentPointer.isEmpty() || !parentPointer.get(parentPointer.size() - 1).equals(expectedKey)) {
          return null;
        }

        String leafNodeText = node.asText();
        StringBuilder jsonPointer = new StringBuilder("/");
        for (int i = 0; i < parentPointer.size() - 1; i++) {
          if (i > 0) jsonPointer.append("/");
          jsonPointer.append(escapeForbiddenJsonPointerSymbols(parentPointer.get(i)));
        }

        return new AbstractMap.SimpleEntry<>(leafNodeText, jsonPointer.toString());
      });

      Map<String, String> result = new HashMap<>();
      entries.forEach(entry -> result.put(entry.getKey(), entry.getValue()));
      return result;
    });
  }

  @NotNull
  private <T> Stream<T> indexSchema(@NotNull JsonNode root,
                                    @NotNull List<String> parentPointer,
                                    @NotNull java.util.function.BiFunction<JsonNode, List<String>, T> retrieveDataFromNode) {
    if (root.isObject()) {
      return StreamSupport.stream(
        Spliterators.spliteratorUnknownSize(root.fields(), Spliterator.ORDERED),
        false
      ).flatMap(entry -> {
        T retrievedValue = retrieveDataFromNode.apply(root, parentPointer);
        if (retrievedValue != null) {
          return Stream.of(retrievedValue);
        }

        List<String> newPointer = new ArrayList<>(parentPointer);
        newPointer.add(entry.getKey());
        return indexSchema(entry.getValue(), newPointer, retrieveDataFromNode);
      });
    } else if (root.isArray()) {
      T retrievedValue = retrieveDataFromNode.apply(root, parentPointer);
      if (retrievedValue != null) {
        return Stream.of(retrievedValue);
      }

      List<Stream<T>> streams = new ArrayList<>();
      int index = 0;
      for (JsonNode arrayItem : root) {
        List<String> newPointer = new ArrayList<>(parentPointer);
        newPointer.add(String.valueOf(index));
        streams.add(indexSchema(arrayItem, newPointer, retrieveDataFromNode));
        index++;
      }
      return streams.stream().flatMap(s -> s);
    } else if (root.isTextual()) {
      T retrievedValue = retrieveDataFromNode.apply(root, parentPointer);
      return retrievedValue != null ? Stream.of(retrievedValue) : Stream.empty();
    } else {
      return Stream.empty();
    }
  }
}
