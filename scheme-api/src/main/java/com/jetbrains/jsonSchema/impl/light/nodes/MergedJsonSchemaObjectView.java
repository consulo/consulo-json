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

import com.jetbrains.jsonSchema.IfThenElse;
import com.jetbrains.jsonSchema.JsonSchemaMetadataEntry;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.light.legacy.LegacyJsonSchemaObjectMerger;
import com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy;
import com.jetbrains.jsonSchema.internal.MergedJsonSchemaObject;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class MergedJsonSchemaObjectView extends JsonSchemaObject implements MergedJsonSchemaObject {
    private final JsonSchemaObject myBase;
    private final JsonSchemaObject myOther;
    private final JsonSchemaObject myPointTo;

    public MergedJsonSchemaObjectView(JsonSchemaObject base, JsonSchemaObject other, JsonSchemaObject pointTo) {
        myBase = base;
        myOther = other;
        myPointTo = pointTo;
    }

    @Override
    public JsonSchemaObject getBase() {
        return myBase;
    }

    @Override
    public JsonSchemaObject getOther() {
        return myOther;
    }

    private @Nullable JsonSchemaInterpretationStrategy getMergedSchemaInterpretationStrategy() {
        return myPointTo.getRootSchemaObject() instanceof RootJsonSchemaObjectBackedByJackson root
            ? root.getSchemaInterpretationStrategy()
            : null;
    }

    @Override
    public String getPointer() {
        return myPointTo.getPointer();
    }

    @Override
    public @Nullable String getFileUrl() {
        return myPointTo.getFileUrl();
    }

    @Override
    public @Nullable VirtualFile getRawFile() {
        return myPointTo.getRawFile();
    }

    // important to see the following two methods together - they must resolve other's ref according to the resolver logic
    @Override
    public @Nullable String getRef() {
        return myOther.getRef();
    }

    @Override
    public @Nullable String readChildNodeValue(String childNodeName) {
        return baseIfConditionOrOtherWithArgument(JsonSchemaObject::readChildNodeValue, childNodeName,
            LightweightJsonSchemaObjectMerger::isNotBlank);
    }

    @Override
    public boolean hasChildNode(String childNodeName) {
        return booleanOrWithArgument(JsonSchemaObject::hasChildNode, childNodeName);
    }

    @Override
    public boolean hasChildFieldsExcept(List<String> namesToSkip) {
        return booleanOrWithArgument(JsonSchemaObject::hasChildFieldsExcept, namesToSkip);
    }

    @Override
    public Iterable<JsonSchemaValidation> getValidations(@Nullable JsonSchemaType type, JsonValueAdapter value) {
        JsonSchemaInterpretationStrategy strategy = getMergedSchemaInterpretationStrategy();
        if (strategy == null) {
            return List.of();
        }
        return strategy.getValidations(this, type, value).toList();
    }

    @Override
    public JsonSchemaObject getRootSchemaObject() {
        return myBase.getRootSchemaObject();
    }

    @Override
    public @Nullable Boolean getConstantSchema() {
        return booleanAndNullable(JsonSchemaObject::getConstantSchema);
    }

    @Override
    public boolean isValidByExclusion() {
        LegacyJsonSchemaObjectMerger.ExclusionAndTypeInfo mergedExclusionAndType =
            LegacyJsonSchemaObjectMerger.computeMergedExclusionAndType(myBase.getType(), myOther.getType(), myOther.getTypeVariants());
        if (mergedExclusionAndType != null && mergedExclusionAndType.isValidByExclusion != null) {
            return mergedExclusionAndType.isValidByExclusion;
        }
        Boolean mergedTypeVariantsExclusion =
            LegacyJsonSchemaObjectMerger.mergeTypeVariantSets(myBase.getTypeVariants(), myOther.getTypeVariants()).isValidByExclusion;
        if (mergedTypeVariantsExclusion != null) {
            return mergedTypeVariantsExclusion;
        }
        return myBase.isValidByExclusion();
    }

    @Override
    public Iterator<String> getDefinitionNames() {
        return Stream.concat(asStream(myBase.getDefinitionNames()), asStream(myOther.getDefinitionNames())).distinct().iterator();
    }

    @Override
    public @Nullable JsonSchemaObject getDefinitionByName(String name) {
        JsonSchemaObject baseDef = myBase.getDefinitionByName(name);
        if (baseDef == null) {
            return myOther.getDefinitionByName(name);
        }

        JsonSchemaObject otherDef = myOther.getDefinitionByName(name);
        if (otherDef == null) {
            return baseDef;
        }

        return LightweightJsonSchemaObjectMerger.INSTANCE.mergeObjects(baseDef, otherDef, otherDef);
    }

    @Override
    public Iterator<String> getPropertyNames() {
        return Stream.concat(asStream(myBase.getPropertyNames()), asStream(myOther.getPropertyNames())).distinct().iterator();
    }

    @Override
    public @Nullable JsonSchemaObject getPropertyByName(String name) {
        JsonSchemaObject baseProp = myBase.getPropertyByName(name);
        if (baseProp == null) {
            return myOther.getPropertyByName(name);
        }

        JsonSchemaObject otherProp = myOther.getPropertyByName(name);
        if (otherProp == null) {
            return baseProp;
        }

        return LightweightJsonSchemaObjectMerger.INSTANCE.mergeObjects(baseProp, otherProp, otherProp);
    }

    @Override
    public Iterator<String> getSchemaDependencyNames() {
        return Stream.concat(asStream(myBase.getSchemaDependencyNames()), asStream(myOther.getSchemaDependencyNames())).distinct().iterator();
    }

    @Override
    public @Nullable JsonSchemaObject getSchemaDependencyByName(String name) {
        JsonSchemaObject baseDef = myBase.getSchemaDependencyByName(name);
        if (baseDef == null) {
            return myOther.getSchemaDependencyByName(name);
        }

        JsonSchemaObject otherDef = myOther.getSchemaDependencyByName(name);
        if (otherDef == null) {
            return baseDef;
        }

        return LightweightJsonSchemaObjectMerger.INSTANCE.mergeObjects(baseDef, otherDef, otherDef);
    }

    @Override
    public @Nullable List<JsonSchemaMetadataEntry> getMetadata() {
        List<JsonSchemaMetadataEntry> metadata = myOther.getMetadata();
        return metadata != null ? metadata : myBase.getMetadata();
    }

    @Override
    public boolean hasPatternProperties() {
        return booleanOr(JsonSchemaObject::hasPatternProperties);
    }

    @Override
    public @Nullable JsonSchemaType getType() {
        LegacyJsonSchemaObjectMerger.ExclusionAndTypeInfo mergedExclusionAndType =
            LegacyJsonSchemaObjectMerger.computeMergedExclusionAndType(myBase.getType(), myOther.getType(), myOther.getTypeVariants());
        if (mergedExclusionAndType != null && mergedExclusionAndType.type != null) {
            return mergedExclusionAndType.type;
        }
        return myBase.getType();
    }

    @Override
    public @Nullable Number getMultipleOf() {
        return baseIfConditionOrOther(JsonSchemaObject::getMultipleOf, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable Number getMaximum() {
        return baseIfConditionOrOther(JsonSchemaObject::getMaximum, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public boolean isExclusiveMaximum() {
        return booleanOr(JsonSchemaObject::isExclusiveMaximum);
    }

    @Override
    public @Nullable Number getExclusiveMaximumNumber() {
        return baseIfConditionOrOther(JsonSchemaObject::getExclusiveMaximumNumber, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable Number getExclusiveMinimumNumber() {
        return baseIfConditionOrOther(JsonSchemaObject::getExclusiveMinimumNumber, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable Number getMinimum() {
        return baseIfConditionOrOther(JsonSchemaObject::getMinimum, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public boolean isExclusiveMinimum() {
        return booleanOr(JsonSchemaObject::isExclusiveMinimum);
    }

    @Override
    public @Nullable Integer getMaxLength() {
        return baseIfConditionOrOther(JsonSchemaObject::getMaxLength, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable Integer getMinLength() {
        return baseIfConditionOrOther(JsonSchemaObject::getMinLength, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable String getPattern() {
        return baseIfConditionOrOther(JsonSchemaObject::getPattern, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public boolean getAdditionalPropertiesAllowed() {
        return booleanAnd(JsonSchemaObject::getAdditionalPropertiesAllowed);
    }

    @Override
    public boolean hasOwnExtraPropertyProhibition() {
        return !myPointTo.getAdditionalPropertiesAllowed();
    }

    @Override
    public @Nullable JsonSchemaObject getPropertyNamesSchema() {
        return baseIfConditionOrOther(JsonSchemaObject::getPropertyNamesSchema, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable JsonSchemaObject getAdditionalPropertiesSchema() {
        return baseIfConditionOrOther(JsonSchemaObject::getAdditionalPropertiesSchema, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable JsonSchemaObject getUnevaluatedPropertiesSchema() {
        return baseIfConditionOrOther(JsonSchemaObject::getUnevaluatedPropertiesSchema, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable Boolean getAdditionalItemsAllowed() {
        return baseIfConditionOrOther(JsonSchemaObject::getAdditionalItemsAllowed, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable String getDeprecationMessage() {
        return baseIfConditionOrOther(JsonSchemaObject::getDeprecationMessage, LightweightJsonSchemaObjectMerger::isNotBlank);
    }

    @Override
    public @Nullable JsonSchemaObject getAdditionalItemsSchema() {
        return baseIfConditionOrOther(JsonSchemaObject::getAdditionalItemsSchema, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable JsonSchemaObject getItemsSchema() {
        return baseIfConditionOrOther(JsonSchemaObject::getItemsSchema, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable JsonSchemaObject getUnevaluatedItemsSchema() {
        return baseIfConditionOrOther(JsonSchemaObject::getUnevaluatedItemsSchema, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable JsonSchemaObject getContainsSchema() {
        return baseIfConditionOrOther(JsonSchemaObject::getContainsSchema, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable List<JsonSchemaObject> getItemsSchemaList() {
        return LightweightJsonSchemaObjectMerger.mergeLists(this, JsonSchemaObject::getItemsSchemaList);
    }

    @Override
    public @Nullable Integer getMaxItems() {
        return baseIfConditionOrOther(JsonSchemaObject::getMaxItems, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable Integer getMinItems() {
        return baseIfConditionOrOther(JsonSchemaObject::getMinItems, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public boolean isUniqueItems() {
        return baseIfConditionOrOther(JsonSchemaObject::isUniqueItems, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable Integer getMaxProperties() {
        return baseIfConditionOrOther(JsonSchemaObject::getMaxProperties, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable Integer getMinProperties() {
        return baseIfConditionOrOther(JsonSchemaObject::getMinProperties, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable Set<String> getRequired() {
        return LightweightJsonSchemaObjectMerger.mergeSets(myBase.getRequired(), myOther.getRequired());
    }

    @Override
    public @Nullable Map<String, List<String>> getPropertyDependencies() {
        return LightweightJsonSchemaObjectMerger.mergeMaps(this, JsonSchemaObject::getPropertyDependencies);
    }

    @Override
    public @Nullable Map<String, JsonSchemaObject> getSchemaDependencies() {
        return LightweightJsonSchemaObjectMerger.mergeMaps(this, JsonSchemaObject::getSchemaDependencies);
    }

    @Override
    public @Nullable List<Object> getEnum() {
        return baseIfConditionOrOther(JsonSchemaObject::getEnum, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable List<JsonSchemaObject> getAllOf() {
        return LightweightJsonSchemaObjectMerger.mergeLists(this, JsonSchemaObject::getAllOf);
    }

    @Override
    public @Nullable List<JsonSchemaObject> getAnyOf() {
        return LightweightJsonSchemaObjectMerger.mergeLists(this, JsonSchemaObject::getAnyOf);
    }

    @Override
    public @Nullable List<JsonSchemaObject> getOneOf() {
        return LightweightJsonSchemaObjectMerger.mergeLists(this, JsonSchemaObject::getOneOf);
    }

    @Override
    public @Nullable JsonSchemaObject getNot() {
        return baseIfConditionOrOther(JsonSchemaObject::getNot, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable List<IfThenElse> getIfThenElse() {
        return LightweightJsonSchemaObjectMerger.mergeLists(this, JsonSchemaObject::getIfThenElse);
    }

    @Override
    public @Nullable Set<JsonSchemaType> getTypeVariants() {
        return LegacyJsonSchemaObjectMerger.mergeTypeVariantSets(myBase.getTypeVariants(), myOther.getTypeVariants()).types;
    }

    @Override
    public boolean isRefRecursive() {
        return booleanOr(JsonSchemaObject::isRefRecursive);
    }

    @Override
    public boolean isRecursiveAnchor() {
        return booleanOr(JsonSchemaObject::isRecursiveAnchor);
    }

    @Override
    public @Nullable Object getDefault() {
        return baseIfConditionOrOther(JsonSchemaObject::getDefault, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable JsonSchemaObject getExampleByName(String name) {
        return baseIfConditionOrOtherWithArgument(JsonSchemaObject::getExampleByName, name,
            LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable String getFormat() {
        return baseIfConditionOrOther(JsonSchemaObject::getFormat, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable String getId() {
        return baseIfConditionOrOther(JsonSchemaObject::getId, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable String getSchema() {
        return baseIfConditionOrOther(JsonSchemaObject::getSchema, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable String getDescription() {
        return baseIfConditionOrOther(JsonSchemaObject::getDescription, LightweightJsonSchemaObjectMerger::isNotBlank);
    }

    @Override
    public @Nullable String getTitle() {
        return baseIfConditionOrOther(JsonSchemaObject::getTitle, LightweightJsonSchemaObjectMerger::isNotBlank);
    }

    @Override
    public @Nullable JsonSchemaObject getMatchingPatternPropertySchema(String name) {
        return baseIfConditionOrOtherWithArgument(JsonSchemaObject::getMatchingPatternPropertySchema, name,
            LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public boolean checkByPattern(String value) {
        return booleanOrWithArgument(JsonSchemaObject::checkByPattern, value);
    }

    @Override
    public @Nullable String getPatternError() {
        return baseIfConditionOrOther(JsonSchemaObject::getPatternError, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable JsonSchemaObject findRelativeDefinition(String ref) {
        return baseIfConditionOrOtherWithArgument(JsonSchemaObject::findRelativeDefinition, ref,
            LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable Map<String, Map<String, String>> getEnumMetadata() {
        return LightweightJsonSchemaObjectMerger.mergeMaps(this, JsonSchemaObject::getEnumMetadata);
    }

    @Override
    public @Nullable String getTypeDescription(boolean shortDesc) {
        return baseIfConditionOrOtherWithArgument(JsonSchemaObject::getTypeDescription, shortDesc,
            LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable String getHtmlDescription() {
        return baseIfConditionOrOther(JsonSchemaObject::getHtmlDescription, LightweightJsonSchemaObjectMerger::isNotBlank);
    }

    @Override
    public boolean isForceCaseInsensitive() {
        return booleanOr(JsonSchemaObject::isForceCaseInsensitive);
    }

    @Override
    public @Nullable String getLanguageInjection() {
        return baseIfConditionOrOther(JsonSchemaObject::getLanguageInjection, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable String getLanguageInjectionPrefix() {
        return baseIfConditionOrOther(JsonSchemaObject::getLanguageInjectionPrefix, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public @Nullable String getLanguageInjectionPostfix() {
        return baseIfConditionOrOther(JsonSchemaObject::getLanguageInjectionPostfix, LightweightJsonSchemaObjectMerger::isNotNull);
    }

    @Override
    public boolean isShouldValidateAgainstJSType() {
        return booleanOr(JsonSchemaObject::isShouldValidateAgainstJSType);
    }

    @Override
    public @Nullable JsonSchemaObject resolveRefSchema(JsonSchemaService service) {
        return myOther.resolveRefSchema(service);
    }

    private <T extends @Nullable Object, V extends @Nullable Object> T baseIfConditionOrOtherWithArgument(BiFunction<JsonSchemaObject, V, T> memberReference, V argument, Predicate<T> condition) {
        return LightweightJsonSchemaObjectMerger.baseIfConditionOrOtherWithArgument(myBase, myOther, memberReference, argument, condition);
    }

    private <T extends @Nullable Object> T baseIfConditionOrOther(Function<JsonSchemaObject, T> memberReference, Predicate<T> condition) {
        return LightweightJsonSchemaObjectMerger.baseIfConditionOrOther(myBase, myOther, memberReference, condition);
    }

    private <V extends @Nullable Object> boolean booleanOrWithArgument(BiFunction<JsonSchemaObject, V, Boolean> memberReference, V argument) {
        return LightweightJsonSchemaObjectMerger.booleanOrWithArgument(myBase, myOther, memberReference, argument);
    }

    private @Nullable Boolean booleanAndNullable(Function<JsonSchemaObject, @Nullable Boolean> memberReference) {
        return LightweightJsonSchemaObjectMerger.booleanAndNullable(myBase, myOther, memberReference);
    }

    private boolean booleanAnd(Predicate<JsonSchemaObject> memberReference) {
        return LightweightJsonSchemaObjectMerger.booleanAnd(myBase, myOther, memberReference);
    }

    private boolean booleanOr(Predicate<JsonSchemaObject> memberReference) {
        return LightweightJsonSchemaObjectMerger.booleanOr(myBase, myOther, memberReference);
    }

    private static <T> Stream<T> asStream(Iterator<T> iterator) {
        return StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false);
    }
}
