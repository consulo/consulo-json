// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jetbrains.jsonSchema.JsonPointerUtil;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.IfThenElse;
import com.jetbrains.jsonSchema.JsonSchemaMetadataEntry;
import com.jetbrains.jsonSchema.impl.JsonSchemaReader;
import com.jetbrains.jsonSchema.PropertyNamePattern;
import com.jetbrains.jsonSchema.impl.light.JsonSchemaNodePointer;
import com.jetbrains.jsonSchema.impl.light.JsonSchemaRefResolver;
import com.jetbrains.jsonSchema.impl.light.JsonSchemaRefResolverKt;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectLegacyAdapter;
import com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy;
import com.jetbrains.jsonSchema.internal.PatternProperties;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.internal.keyFMap.KeyFMap;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.*;
import static com.jetbrains.jsonSchema.impl.light.legacy.SchemaImplOldHacks.isOldParserAwareOfFieldName;
import static com.jetbrains.jsonSchema.impl.light.legacy.SchemaImplOldHacks.tryReadEnumMetadata;
import static com.jetbrains.jsonSchema.impl.light.nodes.JacksonSchemaNodeAccessor.asDoubleQuotedString;
import static com.jetbrains.jsonSchema.impl.light.nodes.JacksonSchemaNodeAccessor.asUnquotedString;
import static com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectRendering.renderSchemaNode;

public abstract class JsonSchemaObjectBackedByJacksonBase extends JsonSchemaObjectLegacyAdapter
  implements JsonSchemaNodePointer<JsonNode> {

  private static final Key<List<JsonSchemaObject>> ONE_OF_KEY = Key.create("oneOf");
  private static final Key<List<JsonSchemaObject>> ANY_OF_KEY = Key.create("anyOf");
  private static final Key<List<JsonSchemaObject>> ALL_OF_KEY = Key.create("allOf");
  private static final Key<Set<JsonSchemaType>> TYPE_VARIANTS_KEY = Key.create("typeVariants");
  private static final Key<PropertyNamePattern> PATTERN_KEY = Key.create("pattern");
  private static final Key<PatternProperties> PATTERN_PROPERTIES_KEY = Key.create("patternProperties");

  private static final String INVALID_PATTERN_FALLBACK = "__invalid_ij_pattern";

  private final JsonNode rawSchemaNode;
  private final String jsonPointer;
  private final AtomicReference<KeyFMap> myCompositeObjectsCache = new AtomicReference<>(KeyFMap.EMPTY_MAP);

  public JsonSchemaObjectBackedByJacksonBase(@Nonnull JsonNode rawSchemaNode, @Nonnull String jsonPointer) {
    this.rawSchemaNode = rawSchemaNode;
    this.jsonPointer = jsonPointer;
  }

  @Override
  @Nonnull
  public JsonNode getRawSchemaNode() {
    return rawSchemaNode;
  }

  @Nonnull
  public String getPointer() {
    return jsonPointer;
  }

  @Override
  @Nonnull
  public abstract RootJsonSchemaObjectBackedByJackson getRootSchemaObject();

  @Nonnull
  private JsonSchemaInterpretationStrategy getSchemaInterpretationStrategy() {
    return getRootSchemaObject().getSchemaInterpretationStrategy();
  }

  protected <V> V getOrComputeValue(@Nonnull Key<V> key, @Nonnull Supplier<V> computation) {
    return myCompositeObjectsCache.updateAndGet(existingMap -> {
      if (existingMap.get(key) != null) return existingMap;
      return existingMap.plus(key, computation.get());
    }).get(key);
  }

  @Nullable
  private JsonSchemaObjectBackedByJacksonBase createResolvableChild(@Nonnull String... childNodeRelativePointer) {
    // delegate to the root schema's factory - it is the only entry point for objects instantiation and caching
    return getRootSchemaObject().getChildSchemaObjectByName(this, childNodeRelativePointer);
  }

  @Override
  @Nonnull
  public Iterable<JsonSchemaValidation> getValidations(@Nullable JsonSchemaType type, @Nullable JsonValueAdapter value) {
    Iterable<JsonSchemaValidation> validations = getSchemaInterpretationStrategy().getValidations(this, type, value);
    return validations != null ? validations : Collections.emptyList();
  }

  @Override
  @Nullable
  public String getSchema() {
    return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(rawSchemaNode, SCHEMA_KEYWORD_INVARIANT);
  }

  @Override
  @Nullable
  public String getFileUrl() {
    return getRootSchemaObject().getFileUrl();
  }

  @Override
  @Nullable
  public VirtualFile getRawFile() {
    return getRootSchemaObject().getRawFile();
  }

  @Override
  public boolean hasChildFieldsExcept(@Nonnull List<String> namesToSkip) {
    Iterable<String> keys = JacksonSchemaNodeAccessor.INSTANCE.readNodeKeys(rawSchemaNode, null);
    if (keys == null) return false;

    for (String key : keys) {
      if (!namesToSkip.contains(key)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasChildNode(@Nonnull String childNodeName) {
    return JacksonSchemaNodeAccessor.INSTANCE.hasChildNode(rawSchemaNode, childNodeName);
  }

  @Override
  @Nullable
  public String readChildNodeValue(@Nonnull String childNodeName) {
    return JacksonSchemaNodeAccessor.INSTANCE.readUntypedNodeValueAsText(rawSchemaNode, childNodeName);
  }

  @Override
  @Nullable
  public Boolean getConstantSchema() {
    return JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(rawSchemaNode, null);
  }

  @Override
  public boolean isValidByExclusion() {
    return true;
  }

  @Override
  @Nullable
  public String getDeprecationMessage() {
    String schemaFeature = getSchemaInterpretationStrategy().getDeprecationKeyword();
    if (schemaFeature == null) return null;
    return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(rawSchemaNode, schemaFeature);
  }

  @Override
  @Nullable
  public Set<JsonSchemaType> getTypeVariants() {
    String schemaFeature = getSchemaInterpretationStrategy().getTypeKeyword();
    if (schemaFeature == null) return null;

    Set<JsonSchemaType> result = getOrComputeValue(TYPE_VARIANTS_KEY, () -> {
      Iterable<Object> collection = JacksonSchemaNodeAccessor.INSTANCE.readUntypedNodesCollection(rawSchemaNode, schemaFeature);
      if (collection == null) return Collections.emptySet();

      Set<JsonSchemaType> types = new HashSet<>();
      for (Object item : collection) {
        if (item instanceof String) {
          String unquoted = asUnquotedString((String) item);
          JsonSchemaType type = JsonSchemaReader.parseType(unquoted);
          if (type != null) {
            types.add(type);
          }
        }
      }
      return types;
    });

    return result.isEmpty() ? null : result;
  }

  @Override
  @Nullable
  public JsonSchemaType getType() {
    String schemaFeature = getSchemaInterpretationStrategy().getTypeKeyword();
    if (schemaFeature == null) return null;

    String typeText = JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(rawSchemaNode, schemaFeature);
    return typeText != null ? JsonSchemaReader.parseType(typeText) : null;
  }

  @Override
  @Nullable
  public Number getMultipleOf() {
    String schemaFeature = getSchemaInterpretationStrategy().getMultipleOfKeyword();
    if (schemaFeature == null) return null;
    return JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(rawSchemaNode, schemaFeature);
  }

  @Override
  @Nullable
  public Number getMaximum() {
    String schemaFeature = getSchemaInterpretationStrategy().getMaximumKeyword();
    if (schemaFeature == null) return null;
    return JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(rawSchemaNode, schemaFeature);
  }

  @Override
  public boolean isExclusiveMaximum() {
    String schemaFeature = getSchemaInterpretationStrategy().getExclusiveMaximumKeyword();
    if (schemaFeature == null) return false;
    Boolean result = JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(rawSchemaNode, schemaFeature);
    return result != null && result;
  }

  @Override
  @Nullable
  public Number getExclusiveMaximumNumber() {
    String schemaFeature = getSchemaInterpretationStrategy().getExclusiveMaximumKeyword();
    if (schemaFeature == null) return null;
    return JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(rawSchemaNode, schemaFeature);
  }

  @Override
  @Nullable
  public Number getExclusiveMinimumNumber() {
    String schemaFeature = getSchemaInterpretationStrategy().getExclusiveMinimumKeyword();
    if (schemaFeature == null) return null;
    return JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(rawSchemaNode, schemaFeature);
  }

  @Override
  @Nullable
  public Number getMinimum() {
    String schemaFeature = getSchemaInterpretationStrategy().getMinimumKeyword();
    if (schemaFeature == null) return null;
    return JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(rawSchemaNode, schemaFeature);
  }

  @Override
  public boolean isExclusiveMinimum() {
    String schemaFeature = getSchemaInterpretationStrategy().getExclusiveMaximumKeyword();
    if (schemaFeature == null) return false;
    Boolean result = JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(rawSchemaNode, schemaFeature);
    return result != null && result;
  }

  @Override
  @Nullable
  public Integer getMaxLength() {
    String schemaFeature = getSchemaInterpretationStrategy().getMaxLengthKeyword();
    if (schemaFeature == null) return null;
    Number number = JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(rawSchemaNode, schemaFeature);
    return number instanceof Integer ? (Integer) number : null;
  }

  @Override
  @Nullable
  public Integer getMinLength() {
    String schemaFeature = getSchemaInterpretationStrategy().getMinLengthKeyword();
    if (schemaFeature == null) return null;
    Number number = JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(rawSchemaNode, schemaFeature);
    return number instanceof Integer ? (Integer) number : null;
  }

  @Override
  @Nullable
  public String getPattern() {
    String schemaFeature = getSchemaInterpretationStrategy().getPatternKeyword();
    if (schemaFeature == null) return null;
    return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(rawSchemaNode, schemaFeature);
  }

  @Nonnull
  private PropertyNamePattern getOrComputeCompiledPattern() {
    return getOrComputeValue(PATTERN_KEY, () -> {
      String effectivePattern = getPattern();
      if (effectivePattern == null) {
        effectivePattern = INVALID_PATTERN_FALLBACK;
      }
      return new PropertyNamePattern(effectivePattern);
    });
  }

  @Override
  @Nullable
  public String getPatternError() {
    return getOrComputeCompiledPattern().getPatternError();
  }

  @Override
  @Nullable
  public JsonSchemaObject findRelativeDefinition(@Nonnull String ref) {
    return JsonSchemaRefResolverKt.resolveLocalSchemaNode(ref, this);
  }

  @Override
  public boolean getAdditionalPropertiesAllowed() {
    String schemaFeature = getSchemaInterpretationStrategy().getAdditionalPropertiesKeyword();
    if (schemaFeature == null) return true;
    Boolean result = JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(rawSchemaNode, schemaFeature);
    return result == null || result;
  }

  @Override
  public boolean hasOwnExtraPropertyProhibition() {
    return !getAdditionalPropertiesAllowed();
  }

  @Override
  @Nullable
  public JsonSchemaObject getAdditionalPropertiesSchema() {
    String schemaFeature = getSchemaInterpretationStrategy().getAdditionalPropertiesKeyword();
    if (schemaFeature == null) return null;

    JsonSchemaObjectBackedByJacksonBase child = createResolvableChild(schemaFeature);
    if (child == null || child.getRawSchemaNode() == null) return null;
    return child.getRawSchemaNode().isObject() ? child : null;
  }

  @Override
  @Nullable
  public JsonSchemaObject getUnevaluatedPropertiesSchema() {
    String additionalPropertiesKeyword = getSchemaInterpretationStrategy().getAdditionalPropertiesKeyword();
    if (additionalPropertiesKeyword != null
        && JacksonSchemaNodeAccessor.INSTANCE.hasChildNode(rawSchemaNode, additionalPropertiesKeyword)) {
      return null;
    }

    String schemaFeature = getSchemaInterpretationStrategy().getUnevaluatedPropertiesKeyword();
    if (schemaFeature == null) return null;
    return createResolvableChild(schemaFeature);
  }

  @Override
  @Nullable
  public JsonSchemaObject getPropertyNamesSchema() {
    String schemaFeature = getSchemaInterpretationStrategy().getPropertyNamesKeyword();
    if (schemaFeature == null) return null;
    return createResolvableChild(schemaFeature);
  }

  @Override
  @Nullable
  public Boolean getAdditionalItemsAllowed() {
    String schemaFeature = getSchemaInterpretationStrategy().getNonPositionalItemsKeyword();
    if (schemaFeature == null) return true;
    Boolean result = JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(rawSchemaNode, schemaFeature);
    return result == null || result;
  }

  @Override
  @Nullable
  public JsonSchemaObject getAdditionalItemsSchema() {
    String schemaFeature = getSchemaInterpretationStrategy().getNonPositionalItemsKeyword();
    if (schemaFeature == null) return null;
    return createResolvableChild(schemaFeature);
  }

  @Override
  @Nullable
  public JsonSchemaObject getItemsSchema() {
    String schemaFeature = getSchemaInterpretationStrategy().getItemsSchemaKeyword();
    if (schemaFeature == null) return null;
    return createResolvableChild(schemaFeature);
  }

  @Override
  @Nullable
  public List<JsonSchemaObject> getItemsSchemaList() {
    String schemaFeature = getSchemaInterpretationStrategy().getPositionalItemsKeyword();
    if (schemaFeature == null) return null;
    List<JsonSchemaObject> result = createIndexedItemsSequence(schemaFeature);
    return result.isEmpty() ? null : result;
  }

  @Override
  @Nullable
  public JsonSchemaObject getUnevaluatedItemsSchema() {
    String nonPositionalItemsKeyword = getSchemaInterpretationStrategy().getNonPositionalItemsKeyword();
    if (nonPositionalItemsKeyword != null
        && JacksonSchemaNodeAccessor.INSTANCE.hasChildNode(rawSchemaNode, nonPositionalItemsKeyword)) {
      return null;
    }

    String schemaFeature = getSchemaInterpretationStrategy().getUnevaluatedItemsKeyword();
    if (schemaFeature == null) return null;
    return createResolvableChild(schemaFeature);
  }

  @Override
  @Nullable
  public JsonSchemaObject getContainsSchema() {
    String schemaFeature = getSchemaInterpretationStrategy().getContainsKeyword();
    if (schemaFeature == null) return null;
    return createResolvableChild(schemaFeature);
  }

  @Override
  @Nullable
  public Integer getMaxItems() {
    String schemaFeature = getSchemaInterpretationStrategy().getMaxItemsKeyword();
    if (schemaFeature == null) return null;
    Number number = JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(rawSchemaNode, schemaFeature);
    return number instanceof Integer ? (Integer) number : null;
  }

  @Override
  @Nullable
  public Integer getMinItems() {
    String schemaFeature = getSchemaInterpretationStrategy().getMinItemsKeyword();
    if (schemaFeature == null) return null;
    Number number = JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(rawSchemaNode, schemaFeature);
    return number instanceof Integer ? (Integer) number : null;
  }

  @Override
  public boolean isUniqueItems() {
    String schemaFeature = getSchemaInterpretationStrategy().getUniqueItemsKeyword();
    if (schemaFeature == null) return false;
    Boolean result = JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(rawSchemaNode, schemaFeature);
    return result != null && result;
  }

  @Override
  @Nullable
  public Integer getMaxProperties() {
    String schemaFeature = getSchemaInterpretationStrategy().getMaxPropertiesKeyword();
    if (schemaFeature == null) return null;
    Number number = JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(rawSchemaNode, schemaFeature);
    return number instanceof Integer ? (Integer) number : null;
  }

  @Override
  @Nullable
  public Integer getMinProperties() {
    String schemaFeature = getSchemaInterpretationStrategy().getMinPropertiesKeyword();
    if (schemaFeature == null) return null;
    Number number = JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(rawSchemaNode, schemaFeature);
    return number instanceof Integer ? (Integer) number : null;
  }

  @Override
  @Nullable
  public Set<String> getRequired() {
    String schemaFeature = getSchemaInterpretationStrategy().getRequiredKeyword();
    if (schemaFeature == null) return null;

    Iterable<Object> collection = JacksonSchemaNodeAccessor.INSTANCE.readUntypedNodesCollection(rawSchemaNode, schemaFeature);
    if (collection == null) return null;

    Set<String> required = new HashSet<>();
    for (Object item : collection) {
      if (item instanceof String) {
        required.add(asUnquotedString((String) item));
      }
    }
    return required.isEmpty() ? null : required;
  }

  @Override
  @Nullable
  public String getRef() {
    String ordinaryReferenceFeature = getSchemaInterpretationStrategy().getReferenceKeyword();
    String dynamicReferenceFeature = getSchemaInterpretationStrategy().getDynamicReferenceKeyword();

    for (String referenceFeature : Arrays.asList(ordinaryReferenceFeature, dynamicReferenceFeature)) {
      if (referenceFeature != null) {
        String ref = JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(rawSchemaNode, referenceFeature);
        if (ref != null) return ref;
      }
    }
    return null;
  }

  @Override
  public boolean isRefRecursive() {
    String schemaFeature = getSchemaInterpretationStrategy().getDynamicReferenceKeyword();
    if (schemaFeature == null) return false;
    Boolean result = JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(rawSchemaNode, schemaFeature);
    return result != null && result;
  }

  @Override
  public boolean isRecursiveAnchor() {
    String schemaFeature = getSchemaInterpretationStrategy().getDynamicAnchorKeyword();
    if (schemaFeature == null) return false;
    Boolean result = JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(rawSchemaNode, schemaFeature);
    return result != null && result;
  }

  @Override
  @Nullable
  public Object getDefault() {
    String schemaFeature = getSchemaInterpretationStrategy().getDefaultKeyword();
    if (schemaFeature == null) return null;

    Number number = JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(rawSchemaNode, schemaFeature);
    if (number != null) return number;

    Boolean bool = JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(rawSchemaNode, schemaFeature);
    if (bool != null) return bool;

    String text = JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(rawSchemaNode, schemaFeature);
    if (text != null) return text;

    return createResolvableChild(schemaFeature);
  }

  @Override
  @Nullable
  public JsonSchemaObject getExampleByName(@Nonnull String name) {
    String schemaFeature = getSchemaInterpretationStrategy().getExampleKeyword();
    if (schemaFeature == null) return null;
    return createResolvableChild(schemaFeature, name);
  }

  @Override
  @Nullable
  public String getFormat() {
    String schemaFeature = getSchemaInterpretationStrategy().getFormatKeyword();
    if (schemaFeature == null) return null;
    return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(rawSchemaNode, schemaFeature);
  }

  @Override
  @Nullable
  public String getId() {
    String idFeature = getSchemaInterpretationStrategy().getIdKeyword();
    String anchorFeature = getSchemaInterpretationStrategy().getAnchorKeyword();

    for (String schemaFeature : Arrays.asList(idFeature, anchorFeature)) {
      if (schemaFeature != null) {
        String rawId = JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(rawSchemaNode, schemaFeature);
        if (rawId != null) {
          return JsonPointerUtil.normalizeId(rawId);
        }
      }
    }
    return null;
  }

  @Override
  @Nullable
  public String getDescription() {
    String schemaFeature = getSchemaInterpretationStrategy().getDescriptionKeyword();
    if (schemaFeature == null) return null;
    return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(rawSchemaNode, schemaFeature);
  }

  @Override
  @Nullable
  public String getTitle() {
    String schemaFeature = getSchemaInterpretationStrategy().getTitleKeyword();
    if (schemaFeature == null) return null;
    return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(rawSchemaNode, schemaFeature);
  }

  @Override
  @Nullable
  public JsonSchemaObject getMatchingPatternPropertySchema(@Nonnull String name) {
    String schemaFeature = getSchemaInterpretationStrategy().getPatternPropertiesKeyword();
    if (schemaFeature == null) return null;

    return getOrComputeValue(PATTERN_PROPERTIES_KEY, () -> {
      Map<String, JsonSchemaObject> childMap = createChildMap(schemaFeature);
      return new PatternProperties(childMap != null ? childMap : Collections.emptyMap());
    }).getPatternPropertySchema(name);
  }

  @Override
  public boolean checkByPattern(@Nonnull String value) {
    return getOrComputeCompiledPattern().checkByPattern(value);
  }

  @Override
  @Nullable
  public Map<String, List<String>> getPropertyDependencies() {
    String schemaFeature = getSchemaInterpretationStrategy().getPropertyDependenciesKeyword();
    if (schemaFeature == null) return null;

    Iterable<Map.Entry<String, List<String>>> entries =
      JacksonSchemaNodeAccessor.INSTANCE.readNodeAsMultiMapEntries(rawSchemaNode, schemaFeature);
    if (entries == null) return null;

    Map<String, List<String>> result = new HashMap<>();
    for (Map.Entry<String, List<String>> entry : entries) {
      result.put(entry.getKey(), entry.getValue());
    }
    return result;
  }

  @Override
  @Nullable
  public List<Object> getEnum() {
    String enumFeature = getSchemaInterpretationStrategy().getEnumKeyword();
    if (enumFeature != null) {
      Iterable<Object> enumCollection = JacksonSchemaNodeAccessor.INSTANCE.readUntypedNodesCollection(rawSchemaNode, ENUM);
      if (enumCollection != null) {
        List<Object> enumList = new ArrayList<>();
        for (Object item : enumCollection) {
          enumList.add(item);
        }
        return enumList;
      }
    }

    String constKeyword = getSchemaInterpretationStrategy().getConstKeyword();
    if (constKeyword == null) return null;

    Number number = JacksonSchemaNodeAccessor.INSTANCE.readNumberNodeValue(rawSchemaNode, constKeyword);
    if (number != null) return Collections.singletonList(number);

    Boolean bool = JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(rawSchemaNode, constKeyword);
    if (bool != null) return Collections.singletonList(bool);

    String text = JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(rawSchemaNode, constKeyword);
    if (text != null) return Collections.singletonList(asDoubleQuotedString(text));

    return null;
  }

  @Override
  @Nullable
  public List<JsonSchemaObject> getAllOf() {
    String schemaFeature = getSchemaInterpretationStrategy().getAllOfKeyword();
    if (schemaFeature == null) return null;

    List<JsonSchemaObject> result = getOrComputeValue(ALL_OF_KEY, () -> createIndexedItemsSequence(schemaFeature));
    return result.isEmpty() ? null : result;
  }

  @Override
  @Nullable
  public List<JsonSchemaObject> getAnyOf() {
    String schemaFeature = getSchemaInterpretationStrategy().getAnyOfKeyword();
    if (schemaFeature == null) return null;

    List<JsonSchemaObject> result = getOrComputeValue(ANY_OF_KEY, () -> createIndexedItemsSequence(schemaFeature));
    return result.isEmpty() ? null : result;
  }

  @Override
  @Nullable
  public List<JsonSchemaObject> getOneOf() {
    String schemaFeature = getSchemaInterpretationStrategy().getOneOfKeyword();
    if (schemaFeature == null) return null;

    List<JsonSchemaObject> result = getOrComputeValue(ONE_OF_KEY, () -> createIndexedItemsSequence(schemaFeature));
    return result.isEmpty() ? null : result;
  }

  @Override
  @Nullable
  public JsonSchemaObject getNot() {
    String schemaFeature = getSchemaInterpretationStrategy().getNotKeyword();
    if (schemaFeature == null) return null;
    return createResolvableChild(schemaFeature);
  }

  @Override
  @Nullable
  public List<IfThenElse> getIfThenElse() {
    String ifFeature = getSchemaInterpretationStrategy().getIfKeyword();
    String thenFeature = getSchemaInterpretationStrategy().getThenKeyword();
    String elseFeature = getSchemaInterpretationStrategy().getElseKeyword();

    if (ifFeature == null || thenFeature == null || elseFeature == null) return null;
    if (!JacksonSchemaNodeAccessor.INSTANCE.hasChildNode(rawSchemaNode, ifFeature)) return null;

    return Collections.singletonList(new IfThenElse(
      createResolvableChild(ifFeature),
      createResolvableChild(thenFeature),
      createResolvableChild(elseFeature)
    ));
  }

  @Override
  @Nullable
  public JsonSchemaObject getDefinitionByName(@Nonnull String name) {
    String schemaFeature = getSchemaInterpretationStrategy().getDefinitionsKeyword();
    if (schemaFeature == null) return null;
    return createResolvableChild(schemaFeature, name);
  }

  @Override
  @Nonnull
  public Iterator<String> getDefinitionNames() {
    Supplier<Iterator<String>> defaultValue = () -> {
      Iterable<String> keys = JacksonSchemaNodeAccessor.INSTANCE.readNodeKeys(rawSchemaNode, null);
      if (keys == null) return Collections.emptyIterator();

      List<String> filtered = new ArrayList<>();
      for (String key : keys) {
        if (!isOldParserAwareOfFieldName(key)) {
          filtered.add(key);
        }
      }
      return filtered.iterator();
    };

    String schemaFeature = getSchemaInterpretationStrategy().getDefinitionsKeyword();
    if (schemaFeature == null) return defaultValue.get();

    Iterable<String> keys = JacksonSchemaNodeAccessor.INSTANCE.readNodeKeys(rawSchemaNode, schemaFeature);
    if (keys == null) return defaultValue.get();

    List<String> keyList = new ArrayList<>();
    for (String key : keys) {
      keyList.add(key);
    }
    return keyList.iterator();
  }

  @Override
  @Nullable
  public JsonSchemaObject getPropertyByName(@Nonnull String name) {
    String schemaFeature = getSchemaInterpretationStrategy().getPropertiesKeyword();
    if (schemaFeature == null) return null;
    return createResolvableChild(schemaFeature, name);
  }

  @Override
  @Nonnull
  public Iterator<String> getPropertyNames() {
    String schemaFeature = getSchemaInterpretationStrategy().getPropertiesKeyword();
    if (schemaFeature == null) return Collections.emptyIterator();

    Iterable<String> keys = JacksonSchemaNodeAccessor.INSTANCE.readNodeKeys(rawSchemaNode, schemaFeature);
    if (keys == null) return Collections.emptyIterator();

    List<String> keyList = new ArrayList<>();
    for (String key : keys) {
      keyList.add(key);
    }
    return keyList.iterator();
  }

  @Override
  public boolean hasPatternProperties() {
    String schemaFeature = getSchemaInterpretationStrategy().getPatternPropertiesKeyword();
    if (schemaFeature == null) return false;
    return JacksonSchemaNodeAccessor.INSTANCE.hasChildNode(rawSchemaNode, schemaFeature);
  }

  @Override
  @Nullable
  public Map<String, Map<String, String>> getEnumMetadata() {
    return tryReadEnumMetadata(this);
  }

  @Override
  @Nonnull
  public String toString() {
    // for debug purposes
    return renderSchemaNode(this, JsonSchemaObjectRendering.JsonSchemaObjectRenderingLanguage.JSON);
  }

  @Override
  @Nonnull
  public Iterator<String> getSchemaDependencyNames() {
    String schemaFeature = getSchemaInterpretationStrategy().getDependencySchemasKeyword();
    if (schemaFeature == null) return Collections.emptyIterator();

    Iterable<String> keys = JacksonSchemaNodeAccessor.INSTANCE.readNodeKeys(rawSchemaNode, schemaFeature);
    if (keys == null) return Collections.emptyIterator();

    List<String> keyList = new ArrayList<>();
    for (String key : keys) {
      keyList.add(key);
    }
    return keyList.iterator();
  }

  @Override
  @Nullable
  public JsonSchemaObject getSchemaDependencyByName(@Nonnull String name) {
    String schemaFeature = getSchemaInterpretationStrategy().getDependencySchemasKeyword();
    if (schemaFeature == null) return null;
    return createResolvableChild(schemaFeature, name);
  }

  @Nonnull
  private List<JsonSchemaObject> createIndexedItemsSequence(@Nonnull String containingNodeName) {
    List<JsonSchemaObject> result = new ArrayList<>();
    int index = 0;
    while (true) {
      JsonSchemaObjectBackedByJacksonBase child = createResolvableChild(containingNodeName, String.valueOf(index));
      if (child == null) break;
      result.add(child);
      index++;
    }
    return result;
  }

  @Nullable
  private Map<String, JsonSchemaObject> createChildMap(@Nonnull String childMapName) {
    Iterable<Map.Entry<String, JsonNode>> entries =
      JacksonSchemaNodeAccessor.INSTANCE.readNodeAsMapEntries(rawSchemaNode, childMapName);
    if (entries == null) return null;

    Map<String, JsonSchemaObject> result = new HashMap<>();
    for (Map.Entry<String, JsonNode> entry : entries) {
      if (!entry.getValue().isObject()) continue;

      JsonSchemaObjectBackedByJacksonBase childObject = createResolvableChild(childMapName, entry.getKey());
      if (childObject == null) continue;

      result.put(entry.getKey(), childObject);
    }
    return result;
  }

  /// candidates for removal
  @Override
  @Nullable
  public Map<String, JsonSchemaObject> getSchemaDependencies() {
    return createChildMap(DEPENDENCIES);
  }

  @Override
  public boolean isForceCaseInsensitive() {
    Boolean result = JacksonSchemaNodeAccessor.INSTANCE.readBooleanNodeValue(rawSchemaNode, X_INTELLIJ_CASE_INSENSITIVE);
    return result != null && result;
  }

  @Override
  @Nullable
  public String getHtmlDescription() {
    return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(rawSchemaNode, X_INTELLIJ_HTML_DESCRIPTION);
  }

  @Override
  @Nullable
  public List<JsonSchemaMetadataEntry> getMetadata() {
    Iterable<Map.Entry<String, JsonNode>> entries =
      JacksonSchemaNodeAccessor.INSTANCE.readNodeAsMapEntries(rawSchemaNode, X_INTELLIJ_METADATA);
    if (entries == null) return null;

    List<JsonSchemaMetadataEntry> metadata = new ArrayList<>();
    for (Map.Entry<String, JsonNode> entry : entries) {
      List<String> values = null;

      if (entry.getValue() instanceof ArrayNode) {
        ArrayNode arrayNode = (ArrayNode) entry.getValue();
        values = new ArrayList<>();
        for (JsonNode element : arrayNode) {
          if (element.isTextual()) {
            values.add(element.asText());
          }
        }
      } else if (entry.getValue().isTextual()) {
        values = Collections.singletonList(entry.getValue().asText());
      }

      if (values != null && !values.isEmpty()) {
        metadata.add(new JsonSchemaMetadataEntry(entry.getKey(), values));
      }
    }
    return metadata.isEmpty() ? null : metadata;
  }

  @Override
  @Nullable
  public String getLanguageInjection() {
    String directChild = JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(rawSchemaNode, X_INTELLIJ_LANGUAGE_INJECTION);
    if (directChild != null) return directChild;

    JsonNode intermediateNode = JacksonSchemaNodeAccessor.INSTANCE.resolveRelativeNode(rawSchemaNode, X_INTELLIJ_LANGUAGE_INJECTION);
    if (intermediateNode == null) return null;

    return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(intermediateNode, LANGUAGE);
  }

  @Override
  @Nullable
  public String getLanguageInjectionPrefix() {
    JsonNode intermediateNode = JacksonSchemaNodeAccessor.INSTANCE.resolveRelativeNode(rawSchemaNode, X_INTELLIJ_LANGUAGE_INJECTION);
    if (intermediateNode == null) return null;

    return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(intermediateNode, PREFIX);
  }

  @Override
  @Nullable
  public String getLanguageInjectionPostfix() {
    JsonNode intermediateNode = JacksonSchemaNodeAccessor.INSTANCE.resolveRelativeNode(rawSchemaNode, X_INTELLIJ_LANGUAGE_INJECTION);
    if (intermediateNode == null) return null;

    return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(intermediateNode, SUFFIX);
  }

  @Override
  public boolean isShouldValidateAgainstJSType() {
    return JacksonSchemaNodeAccessor.INSTANCE.hasChildNode(rawSchemaNode, INSTANCE_OF)
           || JacksonSchemaNodeAccessor.INSTANCE.hasChildNode(rawSchemaNode, TYPE_OF);
  }

  @Override
  @Nullable
  public JsonSchemaObject resolveRefSchema(@Nonnull JsonSchemaService service) {
    String effectiveReference = getRef();
    if (effectiveReference == null) return null;

    Iterable<JsonSchemaRefResolver> resolvers = getSchemaInterpretationStrategy().getReferenceResolvers();
    for (JsonSchemaRefResolver resolver : resolvers) {
      JsonSchemaObject resolved = resolver.resolve(effectiveReference, this, service);
      if (resolved != null && !(resolved instanceof MissingJsonSchemaObject)) {
        return resolved;
      }
    }
    return null;
  }
}
