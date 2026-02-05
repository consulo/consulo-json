// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes;

import com.jetbrains.jsonSchema.*;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.MergedJsonSchemaObject;
import com.jetbrains.jsonSchema.impl.light.legacy.LegacyJsonSchemaObjectMerger;
import com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

import static com.jetbrains.jsonSchema.impl.light.nodes.LightweightJsonSchemaObjectMerger.*;

public class InheritedJsonSchemaObjectView extends JsonSchemaObject implements MergedJsonSchemaObject {
  private final JsonSchemaObject base;
  private final JsonSchemaObject other;

  public InheritedJsonSchemaObjectView(@Nonnull JsonSchemaObject base, @Nonnull JsonSchemaObject other) {
    this.base = base;
    this.other = other;
  }

  @Override
  @Nonnull
  public JsonSchemaObject getBase() {
    return base;
  }

  @Override
  @Nonnull
  public JsonSchemaObject getOther() {
    return other;
  }

  @Nullable
  private JsonSchemaInterpretationStrategy getMergedSchemaInterpretationStrategy() {
    JsonSchemaObject rootSchemaObject = other.getRootSchemaObject();
    if (rootSchemaObject instanceof RootJsonSchemaObjectBackedByJackson) {
      return ((RootJsonSchemaObjectBackedByJackson) rootSchemaObject).getSchemaInterpretationStrategy();
    }
    return null;
  }

  @Override
  @Nonnull
  public String getPointer() {
    return other.getPointer();
  }

  @Override
  @Nullable
  public String getFileUrl() {
    return other.getFileUrl();
  }

  @Override
  @Nullable
  public VirtualFile getRawFile() {
    return other.getRawFile();
  }

  // important to see the following two methods together - they must resolve other's ref according to the resolver logic
  @Override
  @Nullable
  public String getRef() {
    return other.getRef();
  }

  @Override
  @Nullable
  public String readChildNodeValue(@Nonnull String childNodeName) {
    return baseIfConditionOrOtherWithArgument(other, base,
                                              (schema, name) -> schema.readChildNodeValue(name),
                                              childNodeName,
                                              LightweightJsonSchemaObjectMerger::isNotBlank);
  }

  @Override
  public boolean hasChildNode(@Nonnull String childNodeName) {
    return other.hasChildNode(childNodeName);
  }

  @Override
  public boolean hasChildFieldsExcept(@Nonnull List<String> namesToSkip) {
    return booleanOrWithArgument(other, base,
                                 (schema, names) -> schema.hasChildFieldsExcept(names),
                                 namesToSkip);
  }

  @Override
  @Nonnull
  public Iterable<JsonSchemaValidation> getValidations(@Nullable JsonSchemaType type, @Nullable JsonValueAdapter value) {
    JsonSchemaInterpretationStrategy strategy = getMergedSchemaInterpretationStrategy();
    if (strategy == null) return Collections.emptyList();

    Iterable<JsonSchemaValidation> validations = strategy.getValidations(this, type, value);
    return validations != null ? validations : Collections.emptyList();
  }

  @Override
  @Nonnull
  public JsonSchemaObject getRootSchemaObject() {
    return base.getRootSchemaObject();
  }

  @Override
  @Nullable
  public Boolean getConstantSchema() {
    return booleanAndNullable(other, base, JsonSchemaObject::getConstantSchema);
  }

  @Override
  public boolean isValidByExclusion() {
    return other.isValidByExclusion();
  }

  @Override
  @Nonnull
  public Iterator<String> getDefinitionNames() {
    Set<String> names = new LinkedHashSet<>();
    base.getDefinitionNames().forEachRemaining(names::add);
    other.getDefinitionNames().forEachRemaining(names::add);
    return names.iterator();
  }

  @Override
  @Nullable
  public JsonSchemaObject getDefinitionByName(@Nonnull String name) {
    JsonSchemaObject baseDef = base.getDefinitionByName(name);
    if (baseDef == null) return other.getDefinitionByName(name);

    JsonSchemaObject otherDef = other.getDefinitionByName(name);
    if (otherDef == null) return baseDef;

    return LightweightJsonSchemaObjectMerger.INSTANCE.mergeObjects(baseDef, otherDef, otherDef);
  }

  @Override
  @Nonnull
  public Iterator<String> getPropertyNames() {
    Set<String> names = new LinkedHashSet<>();
    base.getPropertyNames().forEachRemaining(names::add);
    other.getPropertyNames().forEachRemaining(names::add);
    return names.iterator();
  }

  @Override
  @Nullable
  public JsonSchemaObject getPropertyByName(@Nonnull String name) {
    JsonSchemaObject baseProp = base.getPropertyByName(name);
    if (baseProp == null) return other.getPropertyByName(name);

    JsonSchemaObject otherProp = other.getPropertyByName(name);
    if (otherProp == null) return baseProp;

    return LightweightJsonSchemaObjectMerger.INSTANCE.mergeObjects(baseProp, otherProp, otherProp);
  }

  @Override
  @Nonnull
  public Iterator<String> getSchemaDependencyNames() {
    Set<String> names = new LinkedHashSet<>();
    base.getSchemaDependencyNames().forEachRemaining(names::add);
    other.getSchemaDependencyNames().forEachRemaining(names::add);
    return names.iterator();
  }

  @Override
  @Nullable
  public JsonSchemaObject getSchemaDependencyByName(@Nonnull String name) {
    JsonSchemaObject baseDef = base.getSchemaDependencyByName(name);
    if (baseDef == null) return other.getSchemaDependencyByName(name);

    JsonSchemaObject otherDef = other.getSchemaDependencyByName(name);
    if (otherDef == null) return baseDef;

    return LightweightJsonSchemaObjectMerger.INSTANCE.mergeObjects(baseDef, otherDef, otherDef);
  }

  @Override
  @Nullable
  public List<JsonSchemaMetadataEntry> getMetadata() {
    return other.getMetadata();
  }

  @Override
  public boolean hasPatternProperties() {
    return other.hasPatternProperties();
  }

  @Override
  @Nullable
  public JsonSchemaType getType() {
    return other.getType();
  }

  @Override
  @Nullable
  public Number getMultipleOf() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getMultipleOf, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public Number getMaximum() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getMaximum, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  public boolean isExclusiveMaximum() {
    return booleanOr(other, base, JsonSchemaObject::isExclusiveMaximum);
  }

  @Override
  @Nullable
  public Number getExclusiveMaximumNumber() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getExclusiveMaximumNumber, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public Number getExclusiveMinimumNumber() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getExclusiveMinimumNumber, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public Number getMinimum() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getMinimum, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  public boolean isExclusiveMinimum() {
    return booleanOr(other, base, JsonSchemaObject::isExclusiveMinimum);
  }

  @Override
  @Nullable
  public Integer getMaxLength() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getMaxLength, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public Integer getMinLength() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getMinLength, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public String getPattern() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getPattern, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  public boolean getAdditionalPropertiesAllowed() {
    return booleanAnd(other, base, JsonSchemaObject::getAdditionalPropertiesAllowed);
  }

  @Override
  public boolean hasOwnExtraPropertyProhibition() {
    return !other.getAdditionalPropertiesAllowed();
  }

  @Override
  @Nullable
  public JsonSchemaObject getPropertyNamesSchema() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getPropertyNamesSchema, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public JsonSchemaObject getAdditionalPropertiesSchema() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getAdditionalPropertiesSchema, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public JsonSchemaObject getUnevaluatedPropertiesSchema() {
    return other.getUnevaluatedPropertiesSchema();
  }

  @Override
  @Nullable
  public Boolean getAdditionalItemsAllowed() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getAdditionalItemsAllowed, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public String getDeprecationMessage() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getDeprecationMessage, LightweightJsonSchemaObjectMerger::isNotBlank);
  }

  @Override
  @Nullable
  public JsonSchemaObject getAdditionalItemsSchema() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getAdditionalItemsSchema, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public JsonSchemaObject getItemsSchema() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getItemsSchema, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public JsonSchemaObject getUnevaluatedItemsSchema() {
    return other.getUnevaluatedItemsSchema();
  }

  @Override
  @Nullable
  public JsonSchemaObject getContainsSchema() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getContainsSchema, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public List<JsonSchemaObject> getItemsSchemaList() {
    return mergeLists(this, JsonSchemaObject::getItemsSchemaList);
  }

  @Override
  @Nullable
  public Integer getMaxItems() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getMaxItems, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public Integer getMinItems() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getMinItems, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  public boolean isUniqueItems() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::isUniqueItems, Objects::nonNull);
  }

  @Override
  @Nullable
  public Integer getMaxProperties() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getMaxProperties, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public Integer getMinProperties() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getMinProperties, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public Set<String> getRequired() {
    return other.getRequired();
  }

  @Override
  @Nullable
  public Map<String, List<String>> getPropertyDependencies() {
    return mergeMaps(this, JsonSchemaObject::getPropertyDependencies);
  }

  @Override
  @Nullable
  public Map<String, JsonSchemaObject> getSchemaDependencies() {
    return mergeMaps(this, JsonSchemaObject::getSchemaDependencies);
  }

  @Override
  @Nullable
  public List<Object> getEnum() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getEnum, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public List<? extends JsonSchemaObject> getAllOf() {
    return other.getAllOf();
  }

  @Override
  @Nullable
  public List<? extends JsonSchemaObject> getAnyOf() {
    return other.getAnyOf();
  }

  @Override
  @Nullable
  public List<? extends JsonSchemaObject> getOneOf() {
    return other.getOneOf();
  }

  @Override
  @Nullable
  public JsonSchemaObject getNot() {
    return other.getNot();
  }

  @Override
  @Nullable
  public List<IfThenElse> getIfThenElse() {
    return other.getIfThenElse();
  }

  @Override
  @Nullable
  public Set<JsonSchemaType> getTypeVariants() {
    LegacyJsonSchemaObjectMerger.ExclusionAndTypesInfo result =
      LegacyJsonSchemaObjectMerger.mergeTypeVariantSets(base.getTypeVariants(), other.getTypeVariants());
    return result != null ? result.types : null;
  }

  @Override
  public boolean isRefRecursive() {
    return booleanOr(other, base, JsonSchemaObject::isRefRecursive);
  }

  @Override
  public boolean isRecursiveAnchor() {
    return booleanOr(other, base, JsonSchemaObject::isRecursiveAnchor);
  }

  @Override
  @Nullable
  public Object getDefault() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getDefault, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public JsonSchemaObject getExampleByName(@Nonnull String name) {
    return baseIfConditionOrOtherWithArgument(other, base,
                                              (schema, n) -> schema.getExampleByName(n),
                                              name,
                                              LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public String getFormat() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getFormat, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public String getId() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getId, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public String getSchema() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getSchema, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public String getDescription() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getDescription, LightweightJsonSchemaObjectMerger::isNotBlank);
  }

  @Override
  @Nullable
  public String getTitle() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getTitle, LightweightJsonSchemaObjectMerger::isNotBlank);
  }

  @Override
  @Nullable
  public JsonSchemaObject getMatchingPatternPropertySchema(@Nonnull String name) {
    return baseIfConditionOrOtherWithArgument(other, base,
                                              (schema, n) -> schema.getMatchingPatternPropertySchema(n),
                                              name,
                                              LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  public boolean checkByPattern(@Nonnull String value) {
    return booleanOrWithArgument(other, base,
                                 (schema, v) -> schema.checkByPattern(v),
                                 value);
  }

  @Override
  @Nullable
  public String getPatternError() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getPatternError, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public JsonSchemaObject findRelativeDefinition(@Nonnull String ref) {
    return baseIfConditionOrOtherWithArgument(other, base,
                                              (schema, r) -> schema.findRelativeDefinition(r),
                                              ref,
                                              LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public Map<String, Map<String, String>> getEnumMetadata() {
    return mergeMaps(this, JsonSchemaObject::getEnumMetadata);
  }

  @Override
  @Nullable
  public String getTypeDescription(boolean shortDesc) {
    return baseIfConditionOrOtherWithArgument(other, base,
                                              (schema, s) -> schema.getTypeDescription(s),
                                              shortDesc,
                                              LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public String getHtmlDescription() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getHtmlDescription, LightweightJsonSchemaObjectMerger::isNotBlank);
  }

  @Override
  @Nullable
  public Map<String, Object> getExample() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getExample, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public JsonSchemaObject getBackReference() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getBackReference, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  public boolean isForceCaseInsensitive() {
    return booleanOr(other, base, JsonSchemaObject::isForceCaseInsensitive);
  }

  @Override
  @Nullable
  public String getLanguageInjection() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getLanguageInjection, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public String getLanguageInjectionPrefix() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getLanguageInjectionPrefix, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  @Nullable
  public String getLanguageInjectionPostfix() {
    return baseIfConditionOrOther(other, base, JsonSchemaObject::getLanguageInjectionPostfix, LightweightJsonSchemaObjectMerger::isNotNull);
  }

  @Override
  public boolean isShouldValidateAgainstJSType() {
    return booleanOr(other, base, JsonSchemaObject::isShouldValidateAgainstJSType);
  }

  @Override
  @Nullable
  public JsonSchemaObject resolveRefSchema(@Nonnull JsonSchemaService service) {
    JsonSchemaObject otherResult = other.resolveRefSchema(service);
    return otherResult != null ? otherResult : base.resolveRefSchema(service);
  }

  @Override
  @Nullable
  public JsonSchemaType mergeTypes(@Nullable JsonSchemaType selfType,
                                   @Nullable JsonSchemaType otherType,
                                   @Nullable Set<JsonSchemaType> otherTypeVariants) {
    throw new UnsupportedOperationException("Must not call mergeTypes on light aggregated object");
  }

  @Override
  @Nonnull
  public Set<JsonSchemaType> mergeTypeVariantSets(@Nullable Set<JsonSchemaType> self,
                                                   @Nullable Set<JsonSchemaType> other) {
    throw new UnsupportedOperationException("Must not call mergeTypeVariantSets on light aggregated object");
  }

  @Override
  public void mergeValues(@Nonnull JsonSchemaObject other) {
    throw new UnsupportedOperationException("Must not call mergeValues on light aggregated object");
  }

  @Override
  @Nonnull
  public Map<String, JsonSchemaObject> getProperties() {
    throw new UnsupportedOperationException("Must not call propertiesMap on light aggregated object");
  }

  @Override
  @Nullable
  public Map<String, JsonSchemaObject> getDefinitionsMap() {
    throw new UnsupportedOperationException("Must not call definitionsMap on light aggregated object");
  }
}
