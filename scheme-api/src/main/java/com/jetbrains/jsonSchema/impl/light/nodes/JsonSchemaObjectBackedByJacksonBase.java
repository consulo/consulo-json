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
package com.jetbrains.jsonSchema.impl.light.nodes;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import com.intellij.json.pointer.JsonPointerUtil;
import com.jetbrains.jsonSchema.IfThenElse;
import com.jetbrains.jsonSchema.JsonSchemaMetadataEntry;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.light.JsonSchemaNodePointer;
import com.jetbrains.jsonSchema.impl.light.JsonSchemaRefLoader;
import com.jetbrains.jsonSchema.impl.light.JsonSchemaRefLoaderKt;
import com.jetbrains.jsonSchema.impl.light.legacy.SchemaImplOldHacks;
import com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy;
import com.jetbrains.jsonSchema.internal.JsonSchemaReader;
import com.jetbrains.jsonSchema.internal.PatternProperties;
import com.jetbrains.jsonSchema.internal.PropertyNamePattern;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.DEPENDENCIES;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.ENUM;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.INSTANCE_OF;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.LANGUAGE;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.PREFIX;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.SCHEMA_KEYWORD_INVARIANT;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.SUFFIX;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.TYPE_OF;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.X_INTELLIJ_CASE_INSENSITIVE;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.X_INTELLIJ_HTML_DESCRIPTION;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.X_INTELLIJ_LANGUAGE_INJECTION;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.X_INTELLIJ_METADATA;
import static com.jetbrains.jsonSchema.impl.light.nodes.JacksonSchemaNodeAccessor.asDoubleQuotedString;
import static com.jetbrains.jsonSchema.impl.light.nodes.JacksonSchemaNodeAccessor.asUnquotedString;

public abstract class JsonSchemaObjectBackedByJacksonBase extends JsonSchemaObject implements JsonSchemaNodePointer<JsonNode> {
    private static final Key<List<JsonSchemaObject>> ONE_OF_KEY = Key.create("oneOf");
    private static final Key<List<JsonSchemaObject>> ANY_OF_KEY = Key.create("anyOf");
    private static final Key<List<JsonSchemaObject>> ALL_OF_KEY = Key.create("allOf");
    private static final Key<Set<JsonSchemaType>> TYPE_VARIANTS_KEY = Key.create("typeVariants");
    private static final Key<PropertyNamePattern> PATTERN_KEY = Key.create("pattern");
    private static final Key<PatternProperties> PATTERN_PROPERTIES_KEY = Key.create("patternProperties");

    private static final String INVALID_PATTERN_FALLBACK = "__invalid_ij_pattern";

    private final JsonNode myRawSchemaNode;
    private final String myJsonPointer;
    private final AtomicReference<Map<Key<?>, Object>> myCompositeObjectsCache = new AtomicReference<>(Map.of());

    protected JsonSchemaObjectBackedByJacksonBase(JsonNode rawSchemaNode, String jsonPointer) {
        myRawSchemaNode = rawSchemaNode;
        myJsonPointer = jsonPointer;
    }

    @Override
    public JsonNode getRawSchemaNode() {
        return myRawSchemaNode;
    }

    @Override
    public abstract RootJsonSchemaObjectBackedByJackson getRootSchemaObject();

    private JsonSchemaInterpretationStrategy getSchemaInterpretationStrategy() {
        return getRootSchemaObject().getSchemaInterpretationStrategy();
    }

    @SuppressWarnings("unchecked")
    protected <V> V getOrComputeValue(Key<V> key, Supplier<V> computation) {
        Map<Key<?>, Object> cache = myCompositeObjectsCache.updateAndGet(existingMap -> {
            if (existingMap.get(key) != null) {
                return existingMap;
            }
            Map<Key<?>, Object> newMap = new HashMap<>(existingMap);
            newMap.put(key, computation.get());
            return newMap;
        });
        return (V) Objects.requireNonNull(cache.get(key));
    }

    private @Nullable JsonSchemaObjectBackedByJacksonBase createResolvableChild(String... childNodeRelativePointer) {
        // delegate to the root schema's factory - it is the only entry point for objects instantiation and caching
        return getRootSchemaObject().getChildSchemaObjectByName(this, childNodeRelativePointer);
    }

    @Override
    public Iterable<JsonSchemaValidation> getValidations(@Nullable JsonSchemaType type, JsonValueAdapter value) {
        return getSchemaInterpretationStrategy().getValidations(this, type, value).toList();
    }

    @Override
    public @Nullable String getSchema() {
        return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(myRawSchemaNode, SCHEMA_KEYWORD_INVARIANT);
    }

    @Override
    public String getPointer() {
        return myJsonPointer;
    }

    @Override
    public @Nullable String getFileUrl() {
        return getRootSchemaObject().getFileUrl();
    }

    @Override
    public @Nullable VirtualFile getRawFile() {
        return getRootSchemaObject().getRawFile();
    }

    @Override
    public boolean hasChildFieldsExcept(List<String> namesToSkip) {
        Stream<String> keys = JacksonSchemaNodeAccessor.INSTANCE.readNodeKeys(myRawSchemaNode);
        return keys != null && keys.anyMatch(key -> !namesToSkip.contains(key));
    }

    @Override
    public boolean hasChildNode(String childNodeName) {
        return JacksonSchemaNodeAccessor.INSTANCE.hasChildNode(myRawSchemaNode, childNodeName);
    }

    @Override
    public @Nullable String readChildNodeValue(String childNodeName) {
        return JacksonSchemaNodeAccessor.INSTANCE.readUntypedNodeValueAsText(myRawSchemaNode, childNodeName);
    }

    @Override
    public @Nullable Boolean getConstantSchema() {
        return JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(myRawSchemaNode);
    }

    @Override
    public boolean isValidByExclusion() {
        return true;
    }

    @Override
    public @Nullable String getDeprecationMessage() {
        String schemaFeature = getSchemaInterpretationStrategy().getDeprecationKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(myRawSchemaNode, schemaFeature);
    }

    @Override
    public @Nullable Set<JsonSchemaType> getTypeVariants() {
        String schemaFeature = getSchemaInterpretationStrategy().getTypeKeyword();
        if (schemaFeature == null) {
            return null;
        }
        Set<JsonSchemaType> typeVariants = getOrComputeValue(TYPE_VARIANTS_KEY, () -> {
            Stream<Object> items = JacksonSchemaNodeAccessor.INSTANCE.readUntypedNodesCollection(myRawSchemaNode, schemaFeature);
            if (items == null) {
                return Collections.emptySet();
            }
            return items
                .filter(String.class::isInstance)
                .map(item -> asUnquotedString((String) item))
                .map(JsonSchemaReader::parseType)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        });
        return typeVariants.isEmpty() ? null : typeVariants;
    }

    @Override
    public @Nullable JsonSchemaType getType() {
        String schemaFeature = getSchemaInterpretationStrategy().getTypeKeyword();
        if (schemaFeature == null) {
            return null;
        }
        String typeText = JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(myRawSchemaNode, schemaFeature);
        return typeText == null ? null : JsonSchemaReader.parseType(typeText);
    }

    @Override
    public @Nullable Number getMultipleOf() {
        String schemaFeature = getSchemaInterpretationStrategy().getMultipleOfKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(myRawSchemaNode, schemaFeature);
    }

    @Override
    public @Nullable Number getMaximum() {
        String schemaFeature = getSchemaInterpretationStrategy().getMaximumKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(myRawSchemaNode, schemaFeature);
    }

    @Override
    public boolean isExclusiveMaximum() {
        String schemaFeature = getSchemaInterpretationStrategy().getExclusiveMaximumKeyword();
        if (schemaFeature == null) {
            return false;
        }
        return Boolean.TRUE.equals(JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(myRawSchemaNode, schemaFeature));
    }

    @Override
    public @Nullable Number getExclusiveMaximumNumber() {
        String schemaFeature = getSchemaInterpretationStrategy().getExclusiveMaximumKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(myRawSchemaNode, schemaFeature);
    }

    @Override
    public @Nullable Number getExclusiveMinimumNumber() {
        String schemaFeature = getSchemaInterpretationStrategy().getExclusiveMinimumKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(myRawSchemaNode, schemaFeature);
    }

    @Override
    public @Nullable Number getMinimum() {
        String schemaFeature = getSchemaInterpretationStrategy().getMinimumKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(myRawSchemaNode, schemaFeature);
    }

    @Override
    public boolean isExclusiveMinimum() {
        String schemaFeature = getSchemaInterpretationStrategy().getExclusiveMaximumKeyword();
        if (schemaFeature == null) {
            return false;
        }
        return Boolean.TRUE.equals(JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(myRawSchemaNode, schemaFeature));
    }

    @Override
    public @Nullable Integer getMaxLength() {
        String schemaFeature = getSchemaInterpretationStrategy().getMaxLengthKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return asInteger(JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(myRawSchemaNode, schemaFeature));
    }

    @Override
    public @Nullable Integer getMinLength() {
        String schemaFeature = getSchemaInterpretationStrategy().getMinLengthKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return asInteger(JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(myRawSchemaNode, schemaFeature));
    }

    @Override
    public @Nullable String getPattern() {
        String schemaFeature = getSchemaInterpretationStrategy().getPatternKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(myRawSchemaNode, schemaFeature);
    }

    private PropertyNamePattern getOrComputeCompiledPattern() {
        return getOrComputeValue(PATTERN_KEY, () -> {
            String pattern = getPattern();
            String effectivePattern = pattern != null ? pattern : INVALID_PATTERN_FALLBACK;
            return new PropertyNamePattern(effectivePattern);
        });
    }

    @Override
    public @Nullable String getPatternError() {
        return getOrComputeCompiledPattern().getPatternError();
    }

    @Override
    public @Nullable JsonSchemaObject findRelativeDefinition(String ref) {
        return JsonSchemaRefLoaderKt.resolveLocalSchemaNode(ref, this);
    }

    @Override
    public boolean getAdditionalPropertiesAllowed() {
        String schemaFeature = getSchemaInterpretationStrategy().getAdditionalPropertiesKeyword();
        if (schemaFeature == null) {
            return true;
        }
        return !Boolean.FALSE.equals(JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(myRawSchemaNode, schemaFeature));
    }

    @Override
    public boolean hasOwnExtraPropertyProhibition() {
        return !getAdditionalPropertiesAllowed();
    }

    @Override
    public @Nullable JsonSchemaObject getAdditionalPropertiesSchema() {
        String schemaFeature = getSchemaInterpretationStrategy().getAdditionalPropertiesKeyword();
        if (schemaFeature == null) {
            return null;
        }
        JsonSchemaObjectBackedByJacksonBase child = createResolvableChild(schemaFeature);
        return child != null && child.getRawSchemaNode().isObject() ? child : null;
    }

    @Override
    public @Nullable JsonSchemaObject getUnevaluatedPropertiesSchema() {
        String additionalPropertiesKeyword = getSchemaInterpretationStrategy().getAdditionalPropertiesKeyword();
        if (additionalPropertiesKeyword == null) {
            return null;
        }
        if (JacksonSchemaNodeAccessor.INSTANCE.hasChildNode(myRawSchemaNode, additionalPropertiesKeyword)) {
            return null;
        }

        String schemaFeature = getSchemaInterpretationStrategy().getUnevaluatedPropertiesKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return createResolvableChild(schemaFeature);
    }

    @Override
    public @Nullable JsonSchemaObject getPropertyNamesSchema() {
        String schemaFeature = getSchemaInterpretationStrategy().getPropertyNamesKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return createResolvableChild(schemaFeature);
    }

    @Override
    public Boolean getAdditionalItemsAllowed() {
        String schemaFeature = getSchemaInterpretationStrategy().getNonPositionalItemsKeyword();
        if (schemaFeature == null) {
            return true;
        }
        return !Boolean.FALSE.equals(JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(myRawSchemaNode, schemaFeature));
    }

    @Override
    public @Nullable JsonSchemaObject getAdditionalItemsSchema() {
        String schemaFeature = getSchemaInterpretationStrategy().getNonPositionalItemsKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return createResolvableChild(schemaFeature);
    }

    @Override
    public @Nullable JsonSchemaObject getItemsSchema() {
        String schemaFeature = getSchemaInterpretationStrategy().getItemsSchemaKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return createResolvableChild(schemaFeature);
    }

    @Override
    public @Nullable List<JsonSchemaObject> getItemsSchemaList() {
        String schemaFeature = getSchemaInterpretationStrategy().getPositionalItemsKeyword();
        if (schemaFeature == null) {
            return null;
        }
        List<JsonSchemaObject> items = createIndexedItemsSequence(schemaFeature);
        return items.isEmpty() ? null : items;
    }

    @Override
    public @Nullable JsonSchemaObject getUnevaluatedItemsSchema() {
        String nonPositionalItemsKeyword = getSchemaInterpretationStrategy().getNonPositionalItemsKeyword();
        if (nonPositionalItemsKeyword == null) {
            return null;
        }
        if (JacksonSchemaNodeAccessor.INSTANCE.hasChildNode(myRawSchemaNode, nonPositionalItemsKeyword)) {
            return null;
        }

        String schemaFeature = getSchemaInterpretationStrategy().getUnevaluatedItemsKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return createResolvableChild(schemaFeature);
    }

    @Override
    public @Nullable JsonSchemaObject getContainsSchema() {
        String schemaFeature = getSchemaInterpretationStrategy().getContainsKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return createResolvableChild(schemaFeature);
    }

    @Override
    public @Nullable Integer getMaxItems() {
        String schemaFeature = getSchemaInterpretationStrategy().getMaxItemsKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return asInteger(JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(myRawSchemaNode, schemaFeature));
    }

    @Override
    public @Nullable Integer getMinItems() {
        String schemaFeature = getSchemaInterpretationStrategy().getMinItemsKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return asInteger(JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(myRawSchemaNode, schemaFeature));
    }

    @Override
    public boolean isUniqueItems() {
        String schemaFeature = getSchemaInterpretationStrategy().getUniqueItemsKeyword();
        if (schemaFeature == null) {
            return false;
        }
        return Boolean.TRUE.equals(JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(myRawSchemaNode, schemaFeature));
    }

    @Override
    public @Nullable Integer getMaxProperties() {
        String schemaFeature = getSchemaInterpretationStrategy().getMaxPropertiesKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return asInteger(JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(myRawSchemaNode, schemaFeature));
    }

    @Override
    public @Nullable Integer getMinProperties() {
        String schemaFeature = getSchemaInterpretationStrategy().getMinPropertiesKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return asInteger(JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(myRawSchemaNode, schemaFeature));
    }

    @Override
    public @Nullable Set<String> getRequired() {
        String schemaFeature = getSchemaInterpretationStrategy().getRequiredKeyword();
        if (schemaFeature == null) {
            return null;
        }
        Stream<Object> items = JacksonSchemaNodeAccessor.INSTANCE.readUntypedNodesCollection(myRawSchemaNode, schemaFeature);
        if (items == null) {
            return null;
        }
        return items
            .filter(String.class::isInstance)
            .map(item -> asUnquotedString((String) item))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Override
    public @Nullable String getRef() {
        String ordinaryReferenceFeature = getSchemaInterpretationStrategy().getReferenceKeyword();
        String dynamicReferenceFeature = getSchemaInterpretationStrategy().getDynamicReferenceKeyword();

        return readFirstTextNodeValue(ordinaryReferenceFeature, dynamicReferenceFeature);
    }

    @Override
    public boolean isRefRecursive() {
        String schemaFeature = getSchemaInterpretationStrategy().getDynamicReferenceKeyword();
        if (schemaFeature == null) {
            return false;
        }
        return Boolean.TRUE.equals(JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(myRawSchemaNode, schemaFeature));
    }

    @Override
    public boolean isRecursiveAnchor() {
        String schemaFeature = getSchemaInterpretationStrategy().getDynamicAnchorKeyword();
        if (schemaFeature == null) {
            return false;
        }
        return Boolean.TRUE.equals(JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(myRawSchemaNode, schemaFeature));
    }

    @Override
    public @Nullable Object getDefault() {
        String schemaFeature = getSchemaInterpretationStrategy().getDefaultKeyword();
        if (schemaFeature == null) {
            return null;
        }
        Number number = JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(myRawSchemaNode, schemaFeature);
        if (number != null) {
            return number;
        }
        Boolean bool = JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(myRawSchemaNode, schemaFeature);
        if (bool != null) {
            return bool;
        }
        String text = JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(myRawSchemaNode, schemaFeature);
        if (text != null) {
            return text;
        }
        return createResolvableChild(schemaFeature);
    }

    @Override
    public @Nullable JsonSchemaObject getExampleByName(String name) {
        String schemaFeature = getSchemaInterpretationStrategy().getExampleKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return createResolvableChild(schemaFeature, name);
    }

    @Override
    public @Nullable String getFormat() {
        String schemaFeature = getSchemaInterpretationStrategy().getFormatKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(myRawSchemaNode, schemaFeature);
    }

    @Override
    public @Nullable String getId() {
        String idFeature = getSchemaInterpretationStrategy().getIdKeyword();
        String anchorFeature = getSchemaInterpretationStrategy().getAnchorKeyword();

        String rawId = readFirstTextNodeValue(idFeature, anchorFeature);
        if (rawId == null) {
            return null;
        }
        return JsonPointerUtil.normalizeId(rawId);
    }

    @Override
    public @Nullable String getDescription() {
        String schemaFeature = getSchemaInterpretationStrategy().getDescriptionKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(myRawSchemaNode, schemaFeature);
    }

    @Override
    public @Nullable String getTitle() {
        String schemaFeature = getSchemaInterpretationStrategy().getTitleKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(myRawSchemaNode, schemaFeature);
    }

    @Override
    public @Nullable JsonSchemaObject getMatchingPatternPropertySchema(String name) {
        String schemaFeature = getSchemaInterpretationStrategy().getPatternPropertiesKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return getOrComputeValue(PATTERN_PROPERTIES_KEY, () -> {
            Map<String, JsonSchemaObject> childMap = createChildMap(schemaFeature);
            return new PatternProperties(childMap != null ? childMap : Map.of());
        }).getPatternPropertySchema(name);
    }

    @Override
    public boolean checkByPattern(String value) {
        return getOrComputeCompiledPattern().checkByPattern(value);
    }

    @Override
    public @Nullable Map<String, List<String>> getPropertyDependencies() {
        String schemaFeature = getSchemaInterpretationStrategy().getPropertyDependenciesKeyword();
        if (schemaFeature == null) {
            return null;
        }
        Stream<Pair<String, List<String>>> entries =
            JacksonSchemaNodeAccessor.INSTANCE.readNodeAsMultiMapEntries(myRawSchemaNode, schemaFeature);
        if (entries == null) {
            return null;
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        entries.forEach(entry -> result.put(entry.getFirst(), entry.getSecond()));
        return result;
    }

    @Override
    public @Nullable List<Object> getEnum() {
        String enumFeature = getSchemaInterpretationStrategy().getEnumKeyword();
        if (enumFeature != null) {
            Stream<Object> anEnum = JacksonSchemaNodeAccessor.INSTANCE.readUntypedNodesCollection(myRawSchemaNode, ENUM);
            if (anEnum != null) {
                return anEnum.toList();
            }
        }

        String constKeyword = getSchemaInterpretationStrategy().getConstKeyword();
        if (constKeyword == null) {
            return null;
        }
        Number number = JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(myRawSchemaNode, constKeyword);
        if (number != null) {
            return List.of(number);
        }
        Boolean bool = JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(myRawSchemaNode, constKeyword);
        if (bool != null) {
            return List.of(bool);
        }
        String text = JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(myRawSchemaNode, constKeyword);
        if (text != null) {
            return List.of(asDoubleQuotedString(text));
        }
        return null;
    }

    @Override
    public @Nullable List<JsonSchemaObject> getAllOf() {
        String schemaFeature = getSchemaInterpretationStrategy().getAllOfKeyword();
        if (schemaFeature == null) {
            return null;
        }
        List<JsonSchemaObject> allOf = getOrComputeValue(ALL_OF_KEY, () -> createIndexedItemsSequence(schemaFeature));
        return allOf.isEmpty() ? null : allOf;
    }

    @Override
    public @Nullable List<JsonSchemaObject> getAnyOf() {
        String schemaFeature = getSchemaInterpretationStrategy().getAnyOfKeyword();
        if (schemaFeature == null) {
            return null;
        }
        List<JsonSchemaObject> anyOf = getOrComputeValue(ANY_OF_KEY, () -> createIndexedItemsSequence(schemaFeature));
        return anyOf.isEmpty() ? null : anyOf;
    }

    @Override
    public @Nullable List<JsonSchemaObject> getOneOf() {
        String schemaFeature = getSchemaInterpretationStrategy().getOneOfKeyword();
        if (schemaFeature == null) {
            return null;
        }
        List<JsonSchemaObject> oneOf = getOrComputeValue(ONE_OF_KEY, () -> createIndexedItemsSequence(schemaFeature));
        return oneOf.isEmpty() ? null : oneOf;
    }

    @Override
    public @Nullable JsonSchemaObject getNot() {
        String schemaFeature = getSchemaInterpretationStrategy().getNotKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return createResolvableChild(schemaFeature);
    }

    @Override
    public @Nullable List<IfThenElse> getIfThenElse() {
        String ifFeature = getSchemaInterpretationStrategy().getIfKeyword();
        if (ifFeature == null) {
            return null;
        }
        String thenFeature = getSchemaInterpretationStrategy().getThenKeyword();
        if (thenFeature == null) {
            return null;
        }
        String elseFeature = getSchemaInterpretationStrategy().getElseKeyword();
        if (elseFeature == null) {
            return null;
        }

        if (!JacksonSchemaNodeAccessor.INSTANCE.hasChildNode(myRawSchemaNode, ifFeature)) {
            return null;
        }
        JsonSchemaObject ifSchema = createResolvableChild(ifFeature);
        assert ifSchema != null;
        return List.of(new IfThenElse(ifSchema, createResolvableChild(thenFeature), createResolvableChild(elseFeature)));
    }

    @Override
    public @Nullable JsonSchemaObject getDefinitionByName(String name) {
        String schemaFeature = getSchemaInterpretationStrategy().getDefinitionsKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return createResolvableChild(schemaFeature, name);
    }

    @Override
    public Iterator<String> getDefinitionNames() {
        String schemaFeature = getSchemaInterpretationStrategy().getDefinitionsKeyword();
        if (schemaFeature == null) {
            return getDefaultDefinitionNames();
        }
        Stream<String> definitionNames = JacksonSchemaNodeAccessor.INSTANCE.readNodeKeys(myRawSchemaNode, schemaFeature);
        return definitionNames != null ? definitionNames.iterator() : getDefaultDefinitionNames();
    }

    //todo really need it? ugly old hack
    private Iterator<String> getDefaultDefinitionNames() {
        Stream<String> keys = JacksonSchemaNodeAccessor.INSTANCE.readNodeKeys(myRawSchemaNode);
        if (keys == null) {
            return Collections.emptyIterator();
        }
        return keys.filter(key -> !SchemaImplOldHacks.isOldParserAwareOfFieldName(key)).iterator();
    }

    @Override
    public @Nullable JsonSchemaObject getPropertyByName(String name) {
        String schemaFeature = getSchemaInterpretationStrategy().getPropertiesKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return createResolvableChild(schemaFeature, name);
    }

    @Override
    public Iterator<String> getPropertyNames() {
        String schemaFeature = getSchemaInterpretationStrategy().getPropertiesKeyword();
        if (schemaFeature == null) {
            return Collections.emptyIterator();
        }
        Stream<String> propertyNames = JacksonSchemaNodeAccessor.INSTANCE.readNodeKeys(myRawSchemaNode, schemaFeature);
        return propertyNames != null ? propertyNames.iterator() : Collections.emptyIterator();
    }

    @Override
    public boolean hasPatternProperties() {
        String schemaFeature = getSchemaInterpretationStrategy().getPatternPropertiesKeyword();
        if (schemaFeature == null) {
            return false;
        }
        return JacksonSchemaNodeAccessor.INSTANCE.hasChildNode(myRawSchemaNode, schemaFeature);
    }

    @Override
    public @Nullable Map<String, Map<String, String>> getEnumMetadata() {
        return SchemaImplOldHacks.tryReadEnumMetadata(this);
    }

    @Override
    public String toString() {
        // for debug purposes
        return JsonSchemaObjectRendering.renderSchemaNode(this, JsonSchemaObjectRenderingLanguage.JSON);
    }

    @Override
    public Iterator<String> getSchemaDependencyNames() {
        String schemaFeature = getSchemaInterpretationStrategy().getDependencySchemasKeyword();
        if (schemaFeature == null) {
            return Collections.emptyIterator();
        }
        Stream<String> dependencyNames = JacksonSchemaNodeAccessor.INSTANCE.readNodeKeys(myRawSchemaNode, schemaFeature);
        return dependencyNames != null ? dependencyNames.iterator() : Collections.emptyIterator();
    }

    @Override
    public @Nullable JsonSchemaObject getSchemaDependencyByName(String name) {
        String schemaFeature = getSchemaInterpretationStrategy().getDependencySchemasKeyword();
        if (schemaFeature == null) {
            return null;
        }
        return createResolvableChild(schemaFeature, name);
    }

    private List<JsonSchemaObject> createIndexedItemsSequence(String containingNodeName) {
        List<JsonSchemaObject> result = new ArrayList<>();
        for (int grandChildId = 0; ; grandChildId++) {
            JsonSchemaObjectBackedByJacksonBase child = createResolvableChild(containingNodeName, String.valueOf(grandChildId));
            if (child == null) {
                break;
            }
            result.add(child);
        }
        return result;
    }

    private @Nullable Map<String, JsonSchemaObject> createChildMap(String childMapName) {
        Stream<Pair<String, JsonNode>> entries = JacksonSchemaNodeAccessor.INSTANCE.readNodeAsMapEntries(myRawSchemaNode, childMapName);
        if (entries == null) {
            return null;
        }
        Map<String, JsonSchemaObject> result = new LinkedHashMap<>();
        entries.forEach(entry -> {
            if (!entry.getSecond().isObject()) {
                return;
            }
            JsonSchemaObjectBackedByJacksonBase childObject = createResolvableChild(childMapName, entry.getFirst());
            if (childObject != null) {
                result.put(entry.getFirst(), childObject);
            }
        });
        return result;
    }

    /// candidates for removal
    @Override
    public @Nullable Map<String, JsonSchemaObject> getSchemaDependencies() {
        return createChildMap(DEPENDENCIES);
    }

    @Override
    public boolean isForceCaseInsensitive() {
        return Boolean.TRUE.equals(JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(myRawSchemaNode, X_INTELLIJ_CASE_INSENSITIVE));
    }

    @Override
    public @Nullable String getHtmlDescription() {
        return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(myRawSchemaNode, X_INTELLIJ_HTML_DESCRIPTION);
    }

    @Override
    public @Nullable List<JsonSchemaMetadataEntry> getMetadata() {
        Stream<Pair<String, JsonNode>> entries = JacksonSchemaNodeAccessor.INSTANCE.readNodeAsMapEntries(myRawSchemaNode, X_INTELLIJ_METADATA);
        if (entries == null) {
            return null;
        }
        List<JsonSchemaMetadataEntry> result = new ArrayList<>();
        entries.forEach(entry -> {
            JsonNode valueNode = entry.getSecond();
            List<String> values = null;
            if (valueNode instanceof ArrayNode arrayNode) {
                values = new ArrayList<>();
                for (JsonNode item : arrayNode) {
                    if (item.isString()) {
                        values.add(item.stringValue());
                    }
                }
            }
            else if (valueNode.isString()) {
                values = List.of(valueNode.stringValue());
            }
            if (values != null && !values.isEmpty()) {
                result.add(new JsonSchemaMetadataEntry(entry.getFirst(), values));
            }
        });
        return result;
    }

    @Override
    public @Nullable String getLanguageInjection() {
        String directChild = JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(myRawSchemaNode, X_INTELLIJ_LANGUAGE_INJECTION);
        if (directChild != null) {
            return directChild;
        }

        JsonNode intermediateNode = JacksonSchemaNodeAccessor.INSTANCE.resolveRelativeNode(myRawSchemaNode, X_INTELLIJ_LANGUAGE_INJECTION);
        return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(intermediateNode, LANGUAGE);
    }

    @Override
    public @Nullable String getLanguageInjectionPrefix() {
        JsonNode intermediateNode = JacksonSchemaNodeAccessor.INSTANCE.resolveRelativeNode(myRawSchemaNode, X_INTELLIJ_LANGUAGE_INJECTION);
        return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(intermediateNode, PREFIX);
    }

    @Override
    public @Nullable String getLanguageInjectionPostfix() {
        JsonNode intermediateNode = JacksonSchemaNodeAccessor.INSTANCE.resolveRelativeNode(myRawSchemaNode, X_INTELLIJ_LANGUAGE_INJECTION);
        return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(intermediateNode, SUFFIX);
    }

    @Override
    public boolean isShouldValidateAgainstJSType() {
        return JacksonSchemaNodeAccessor.INSTANCE.hasChildNode(myRawSchemaNode, INSTANCE_OF)
            || JacksonSchemaNodeAccessor.INSTANCE.hasChildNode(myRawSchemaNode, TYPE_OF);
    }

    @Override
    public @Nullable JsonSchemaObject resolveRefSchema(JsonSchemaService service) {
        String effectiveReference = getRef();
        if (effectiveReference == null) {
            return null;
        }
        for (JsonSchemaRefLoader loader : getSchemaInterpretationStrategy().getRefLoaders()) {
            JsonSchemaObject loaded = loader.load(effectiveReference, this, service);
            if (loaded != null) {
                return loaded instanceof MissingJsonSchemaObject ? null : loaded;
            }
        }
        return null;
    }

    private @Nullable String readFirstTextNodeValue(@Nullable String... schemaFeatures) {
        for (String schemaFeature : schemaFeatures) {
            if (schemaFeature == null) {
                continue;
            }
            String value = JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(myRawSchemaNode, schemaFeature);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static @Nullable Integer asInteger(@Nullable Number number) {
        return number instanceof Integer integer ? integer : null;
    }
}
