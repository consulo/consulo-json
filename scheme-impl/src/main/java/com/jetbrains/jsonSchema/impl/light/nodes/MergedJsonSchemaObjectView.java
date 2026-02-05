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

class MergedJsonSchemaObjectView extends JsonSchemaObject implements MergedJsonSchemaObject {
    private final JsonSchemaObject base;
    private final JsonSchemaObject other;
    private final JsonSchemaObject pointTo;

    public MergedJsonSchemaObjectView(@Nonnull JsonSchemaObject base,
                                      @Nonnull JsonSchemaObject other,
                                      @Nonnull JsonSchemaObject pointTo) {
        this.base = base;
        this.other = other;
        this.pointTo = pointTo;
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
        JsonSchemaObject rootSchemaObject = pointTo.getRootSchemaObject();
        if (rootSchemaObject instanceof RootJsonSchemaObjectBackedByJackson) {
            return ((RootJsonSchemaObjectBackedByJackson) rootSchemaObject).getSchemaInterpretationStrategy();
        }
        return null;
    }

    @Override
    @Nonnull
    public String getPointer() {
        return pointTo.getPointer();
    }

    @Override
    @Nullable
    public String getFileUrl() {
        return pointTo.getFileUrl();
    }

    @Override
    @Nullable
    public VirtualFile getRawFile() {
        return pointTo.getRawFile();
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
        return baseIfConditionOrOtherWithArgument(base, other,
            (schema, name) -> schema.readChildNodeValue(name),
            childNodeName,
            LightweightJsonSchemaObjectMerger::isNotBlank);
    }

    @Override
    public boolean hasChildNode(@Nonnull String childNodeName) {
        return booleanOrWithArgument(base, other,
            (schema, name) -> schema.hasChildNode(name),
            childNodeName);
    }

    @Override
    public boolean hasChildFieldsExcept(@Nonnull List<String> namesToSkip) {
        return booleanOrWithArgument(base, other,
            (schema, names) -> schema.hasChildFieldsExcept(names),
            namesToSkip);
    }

    @Override
    @Nonnull
    public Iterable<JsonSchemaValidation> getValidations(@Nullable JsonSchemaType type, @Nullable JsonValueAdapter value) {
        JsonSchemaInterpretationStrategy strategy = getMergedSchemaInterpretationStrategy();
        if (strategy == null) {
            return Collections.emptyList();
        }

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
        return booleanAndNullable(base, other, JsonSchemaObject::getConstantSchema);
    }

    @Override
    public boolean isValidByExclusion() {
        LegacyJsonSchemaObjectMerger.ExclusionAndTypeInfo result =
            LegacyJsonSchemaObjectMerger.computeMergedExclusionAndType(base.getType(), other.getType(), other.getTypeVariants());
        if (result != null) {
            return result.isValidByExclusion();
        }

        LegacyJsonSchemaObjectMerger.ExclusionAndTypeInfo variantResult =
            LegacyJsonSchemaObjectMerger.mergeTypeVariantSets(base.getTypeVariants(), other.getTypeVariants());
        if (variantResult != null) {
            return variantResult.isValidByExclusion();
        }

        return base.isValidByExclusion();
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
        if (baseDef == null) {
            return other.getDefinitionByName(name);
        }

        JsonSchemaObject otherDef = other.getDefinitionByName(name);
        if (otherDef == null) {
            return baseDef;
        }

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
        if (baseProp == null) {
            return other.getPropertyByName(name);
        }

        JsonSchemaObject otherProp = other.getPropertyByName(name);
        if (otherProp == null) {
            return baseProp;
        }

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
        if (baseDef == null) {
            return other.getSchemaDependencyByName(name);
        }

        JsonSchemaObject otherDef = other.getSchemaDependencyByName(name);
        if (otherDef == null) {
            return baseDef;
        }

        return LightweightJsonSchemaObjectMerger.INSTANCE.mergeObjects(baseDef, otherDef, otherDef);
    }

    @Override
    @Nullable
    public List<JsonSchemaMetadataEntry> getMetadata() {
        List<JsonSchemaMetadataEntry> otherMetadata = other.getMetadata();
        return otherMetadata != null ? otherMetadata : base.getMetadata();
    }

    @Override
    public boolean hasPatternProperties() {
        return booleanOr(base, other, JsonSchemaObject::hasPatternProperties);
    }

    @Override
    @Nullable
    public JsonSchemaType getType() {
        LegacyJsonSchemaObjectMerger.TypeAndExclusion result =
            LegacyJsonSchemaObjectMerger.computeMergedExclusionAndType(base.getType(), other.getType(), other.getTypeVariants());
        return result != null ? result.getType() : base.getType();
    }

    @Override
    @Nullable
    public Number getMultipleOf() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getMultipleOf, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public Number getMaximum() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getMaximum, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public boolean isExclusiveMaximum() {
        return booleanOr(base, other, JsonSchemaObject::isExclusiveMaximum);
    }

    @Override
    @Nullable
    public Number getExclusiveMaximumNumber() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getExclusiveMaximumNumber, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public Number getExclusiveMinimumNumber() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getExclusiveMinimumNumber, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public Number getMinimum() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getMinimum, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public boolean isExclusiveMinimum() {
        return booleanOr(base, other, JsonSchemaObject::isExclusiveMinimum);
    }

    @Override
    @Nullable
    public Integer getMaxLength() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getMaxLength, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public Integer getMinLength() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getMinLength, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public String getPattern() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getPattern, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public boolean getAdditionalPropertiesAllowed() {
        return booleanAnd(base, other, JsonSchemaObject::getAdditionalPropertiesAllowed);
    }

    @Override
    public boolean hasOwnExtraPropertyProhibition() {
        return !pointTo.getAdditionalPropertiesAllowed();
    }

    @Override
    @Nullable
    public JsonSchemaObject getPropertyNamesSchema() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getPropertyNamesSchema, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public JsonSchemaObject getAdditionalPropertiesSchema() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getAdditionalPropertiesSchema, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public JsonSchemaObject getUnevaluatedPropertiesSchema() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getUnevaluatedPropertiesSchema, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public Boolean getAdditionalItemsAllowed() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getAdditionalItemsAllowed, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public String getDeprecationMessage() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getDeprecationMessage, LightweightJsonSchemaObjectMerger::isNotBlank);
    }

    @Override
    @Nullable
    public JsonSchemaObject getAdditionalItemsSchema() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getAdditionalItemsSchema, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public JsonSchemaObject getItemsSchema() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getItemsSchema, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public JsonSchemaObject getUnevaluatedItemsSchema() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getUnevaluatedItemsSchema, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public JsonSchemaObject getContainsSchema() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getContainsSchema, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public List<JsonSchemaObject> getItemsSchemaList() {
        return mergeLists(this, JsonSchemaObject::getItemsSchemaList);
    }

    @Override
    @Nullable
    public Integer getMaxItems() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getMaxItems, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public Integer getMinItems() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getMinItems, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public boolean isUniqueItems() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::isUniqueItems, Objects::nonNull);
    }

    @Override
    @Nullable
    public Integer getMaxProperties() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getMaxProperties, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public Integer getMinProperties() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getMinProperties, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public Set<String> getRequired() {
        return mergeSets(base.getRequired(), other.getRequired());
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
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getEnum, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public List<JsonSchemaObject> getAllOf() {
        return mergeLists(this, JsonSchemaObject::getAllOf);
    }

    @Override
    @Nullable
    public List<JsonSchemaObject> getAnyOf() {
        return mergeLists(this, JsonSchemaObject::getAnyOf);
    }

    @Override
    @Nullable
    public List<JsonSchemaObject> getOneOf() {
        return mergeLists(this, JsonSchemaObject::getOneOf);
    }

    @Override
    @Nullable
    public JsonSchemaObject getNot() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getNot, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public List<IfThenElse> getIfThenElse() {
        return mergeLists(this, JsonSchemaObject::getIfThenElse);
    }

    @Override
    @Nullable
    public Set<JsonSchemaType> getTypeVariants() {
        LegacyJsonSchemaObjectMerger.TypeAndExclusion result =
            LegacyJsonSchemaObjectMerger.mergeTypeVariantSets(base.getTypeVariants(), other.getTypeVariants());
        return result != null ? result.getTypes() : null;
    }

    @Override
    public boolean isRefRecursive() {
        return booleanOr(base, other, JsonSchemaObject::isRefRecursive);
    }

    @Override
    public boolean isRecursiveAnchor() {
        return booleanOr(base, other, JsonSchemaObject::isRecursiveAnchor);
    }

    @Override
    @Nullable
    public Object getDefault() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getDefault, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public JsonSchemaObject getExampleByName(@Nonnull String name) {
        return baseIfConditionOrOtherWithArgument(base, other,
            (schema, n) -> schema.getExampleByName(n),
            name,
            LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public String getFormat() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getFormat, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public String getId() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getId, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public String getSchema() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getSchema, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public String getDescription() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getDescription, LightweightJsonSchemaObjectMerger::isNotBlank);
    }

    @Override
    @Nullable
    public String getTitle() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getTitle, LightweightJsonSchemaObjectMerger::isNotBlank);
    }

    @Override
    @Nullable
    public JsonSchemaObject getMatchingPatternPropertySchema(@Nonnull String name) {
        return baseIfConditionOrOtherWithArgument(base, other,
            (schema, n) -> schema.getMatchingPatternPropertySchema(n),
            name,
            LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public boolean checkByPattern(@Nonnull String value) {
        return booleanOrWithArgument(base, other,
            (schema, v) -> schema.checkByPattern(v),
            value);
    }

    @Override
    @Nullable
    public String getPatternError() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getPatternError, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public JsonSchemaObject findRelativeDefinition(@Nonnull String ref) {
        return baseIfConditionOrOtherWithArgument(base, other,
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
        return baseIfConditionOrOtherWithArgument(base, other,
            (schema, s) -> schema.getTypeDescription(s),
            shortDesc,
            LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public String getHtmlDescription() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getHtmlDescription, LightweightJsonSchemaObjectMerger::isNotBlank);
    }

    @Override
    @Nullable
    public Map<String, Object> getExample() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getExample, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public JsonSchemaObject getBackReference() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getBackReference, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public boolean isForceCaseInsensitive() {
        return booleanOr(base, other, JsonSchemaObject::isForceCaseInsensitive);
    }

    @Override
    @Nullable
    public String getLanguageInjection() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getLanguageInjection, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public String getLanguageInjectionPrefix() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getLanguageInjectionPrefix, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    @Nullable
    public String getLanguageInjectionPostfix() {
        return baseIfConditionOrOther(base, other, JsonSchemaObject::getLanguageInjectionPostfix, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public boolean isShouldValidateAgainstJSType() {
        return booleanOr(base, other, JsonSchemaObject::isShouldValidateAgainstJSType);
    }

    @Override
    @Nullable
    public JsonSchemaObject resolveRefSchema(@Nonnull JsonSchemaService service) {
        return other.resolveRefSchema(service);
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
