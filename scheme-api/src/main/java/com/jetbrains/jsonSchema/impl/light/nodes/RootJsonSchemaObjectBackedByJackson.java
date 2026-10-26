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

import tools.jackson.databind.JsonNode;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy;
import com.jetbrains.jsonSchema.internal.RootJsonSchemaObject;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.SCHEMA_ROOT_POINTER;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.X_INTELLIJ_LANGUAGE_INJECTION;
import static com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy.computeJsonSchemaVersion;

public class RootJsonSchemaObjectBackedByJackson extends JsonSchemaObjectBackedByJacksonBase
    implements RootJsonSchemaObject<JsonNode, JsonSchemaObjectBackedByJacksonBase> {

    private static final Key<Map<String, String>> IDS_MAP_KEY = Key.create("ids");
    private static final Key<Map<String, String>> DYNAMIC_ANCHORS_MAP_KEY = Key.create("dynamicAnchors");
    private static final Key<Boolean> INJECTIONS_MAP_KEY = Key.create("injections");
    private static final Key<Boolean> DEPRECATIONS_MAP_KEY = Key.create("deprecations");
    private static final Key<String> FILE_URL_MAP_KEY = Key.create("fileUrl");

    private final @Nullable VirtualFile mySchemaFile;
    private final JsonSchemaObjectBackedByJacksonFactory mySchemaObjectFactory;
    private final JsonSchemaInterpretationStrategy mySchemaInterpretationStrategy;

    public RootJsonSchemaObjectBackedByJackson(JsonNode rootNode, @Nullable VirtualFile schemaFile) {
        super(rootNode, SCHEMA_ROOT_POINTER);
        mySchemaFile = schemaFile;
        mySchemaObjectFactory = new JsonSchemaObjectBackedByJacksonFactory(this);
        mySchemaInterpretationStrategy = computeJsonSchemaVersion(getSchema());
    }

    public @Nullable VirtualFile getSchemaFile() {
        return mySchemaFile;
    }

    @Override
    public JsonSchemaInterpretationStrategy getSchemaInterpretationStrategy() {
        return mySchemaInterpretationStrategy;
    }

    @Override
    public @Nullable JsonSchemaObjectBackedByJacksonBase getChildSchemaObjectByName(JsonSchemaObjectBackedByJacksonBase parentSchemaObject,
                                                                                   String... childNodeRelativePointer) {
        return mySchemaObjectFactory.getChildSchemaObjectByName(parentSchemaObject, childNodeRelativePointer);
    }

    @Override
    public @Nullable JsonSchemaObject getSchemaObjectByAbsoluteJsonPointer(String jsonPointer) {
        return mySchemaObjectFactory.getSchemaObjectByAbsoluteJsonPointer(jsonPointer);
    }

    public boolean checkHasInjections() {
        return getOrComputeValue(INJECTIONS_MAP_KEY, () ->
            indexSchema(getRawSchemaNode(), List.of(), (node, parentPointer) ->
                X_INTELLIJ_LANGUAGE_INJECTION.equals(lastOrNull(parentPointer)) ? Boolean.TRUE : null
            ).anyMatch(Boolean::booleanValue)
        );
    }

    public boolean checkHasDeprecations() {
        String deprecationMarker = mySchemaInterpretationStrategy.getDeprecationKeyword();
        if (deprecationMarker == null) {
            return false;
        }
        return getOrComputeValue(DEPRECATIONS_MAP_KEY, () ->
            indexSchema(getRawSchemaNode(), List.of(), (node, parentPointer) ->
                deprecationMarker.equals(lastOrNull(parentPointer)) ? Boolean.TRUE : null
            ).anyMatch(Boolean::booleanValue)
        );
    }

    @Override
    public @Nullable String getFileUrl() {
        String fileUrl = getOrComputeValue(FILE_URL_MAP_KEY, () -> mySchemaFile != null ? mySchemaFile.getUrl() : "");
        return fileUrl.isEmpty() ? null : fileUrl;
    }

    @Override
    public @Nullable VirtualFile getRawFile() {
        return mySchemaFile;
    }

    @Override
    public RootJsonSchemaObjectBackedByJackson getRootSchemaObject() {
        return this;
    }

    @Override
    public @Nullable String resolveId(String id) {
        String schemaFeature = mySchemaInterpretationStrategy.getIdKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return collectValuesWithKey(schemaFeature, IDS_MAP_KEY).get(id);
    }

    @Override
    public @Nullable String resolveDynamicAnchor(String anchor) {
        String schemaFeature = mySchemaInterpretationStrategy.getDynamicAnchorKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return collectValuesWithKey(schemaFeature, DYNAMIC_ANCHORS_MAP_KEY).get(anchor);
    }

    private Map<String, String> collectValuesWithKey(String expectedKey, Key<Map<String, String>> storeIn) {
        return getOrComputeValue(storeIn, () -> {
            Map<String, String> result = new LinkedHashMap<>();
            indexSchema(getRawSchemaNode(), List.of(), (node, parentPointer) -> {
                if (!node.isString() || !expectedKey.equals(lastOrNull(parentPointer))) {
                    return null;
                }

                String leafNodeText = node.stringValue();
                String jsonPointer = parentPointer.subList(0, parentPointer.size() - 1).stream()
                    .map(JacksonSchemaNodeAccessor::escapeForbiddenJsonPointerSymbols)
                    .collect(Collectors.joining("/", "/", ""));

                return Pair.create(leafNodeText, jsonPointer);
            }).forEach(pair -> result.put(pair.getFirst(), pair.getSecond()));
            return result;
        });
    }

    private static <T> Stream<T> indexSchema(JsonNode root,
                                             List<String> parentPointer,
                                             BiFunction<JsonNode, List<String>, @Nullable T> retrieveDataFromNode) {
        if (root.isObject()) {
            return root.properties().stream().flatMap(property -> {
                T retrievedValue = retrieveDataFromNode.apply(root, parentPointer);
                if (retrievedValue != null) {
                    return Stream.of(retrievedValue);
                }

                return indexSchema(property.getValue(), append(parentPointer, property.getKey()), retrieveDataFromNode);
            });
        }
        else if (root.isArray()) {
            T retrievedValue = retrieveDataFromNode.apply(root, parentPointer);
            if (retrievedValue != null) {
                return Stream.of(retrievedValue);
            }

            List<JsonNode> arrayItems = new ArrayList<>();
            arrayItems.addAll(root.values());
            return IntStream.range(0, arrayItems.size()).boxed().flatMap(index ->
                indexSchema(arrayItems.get(index), append(parentPointer, index.toString()), retrieveDataFromNode)
            );
        }
        else if (root.isString()) {
            T retrievedValue = retrieveDataFromNode.apply(root, parentPointer);
            return retrievedValue != null ? Stream.of(retrievedValue) : Stream.empty();
        }
        else {
            return Stream.empty();
        }
    }

    private static List<String> append(List<String> list, String element) {
        List<String> result = new ArrayList<>(list.size() + 1);
        result.addAll(list);
        result.add(element);
        return result;
    }

    private static @Nullable String lastOrNull(List<String> list) {
        return list.isEmpty() ? null : list.get(list.size() - 1);
    }
}
