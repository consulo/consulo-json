// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema;

import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import consulo.annotation.DeprecationInfo;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public abstract class JsonSchemaObject {
    @Override
    public boolean equals(@Nullable Object o) {
        if (o == null) {
            return false;
        }
        if (this == o) {
            return true;
        }
        if (this.getClass() != o.getClass()) {
            return false;
        }
        JsonSchemaObject object = (JsonSchemaObject) o;
        return Objects.equals(getFileUrl(), object.getFileUrl()) && Objects.equals(getPointer(), object.getPointer());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getFileUrl(), getPointer());
    }

    public abstract @Nullable Boolean getConstantSchema();

    public abstract boolean hasChildFieldsExcept(List<String> namesToSkip);

    public abstract Iterable<JsonSchemaValidation> getValidations(@Nullable JsonSchemaType type, JsonValueAdapter value);

    public abstract JsonSchemaObject getRootSchemaObject();

    public abstract boolean isValidByExclusion();

    public abstract String getPointer();

    public abstract @Nullable String getFileUrl();

    public abstract @Nullable VirtualFile getRawFile();

    public abstract boolean hasPatternProperties();

    public abstract @Nullable JsonSchemaType getType();

    public abstract @Nullable Number getMultipleOf();

    public abstract @Nullable Number getMaximum();

    public abstract boolean isExclusiveMaximum();

    public abstract @Nullable Number getExclusiveMaximumNumber();

    public abstract @Nullable Number getExclusiveMinimumNumber();

    public abstract @Nullable Number getMinimum();

    public abstract boolean isExclusiveMinimum();

    public abstract @Nullable Integer getMaxLength();

    public abstract @Nullable Integer getMinLength();

    public abstract @Nullable String getPattern();

    public abstract boolean getAdditionalPropertiesAllowed();

    // for the sake of merging validation results, we need to know if this schema prohibits additional properties itself,
    // or if it inherits this prohibition flag from the merge result, as the behavior differs in these cases
    public abstract boolean hasOwnExtraPropertyProhibition();

    public abstract @Nullable JsonSchemaObject getPropertyNamesSchema();

    public abstract @Nullable JsonSchemaObject getAdditionalPropertiesSchema();

    public abstract @Nullable JsonSchemaObject getUnevaluatedPropertiesSchema();

    public abstract @Nullable Boolean getAdditionalItemsAllowed();

    public abstract @Nullable String getDeprecationMessage();

    public abstract @Nullable JsonSchemaObject getAdditionalItemsSchema();

    public abstract @Nullable JsonSchemaObject getItemsSchema();

    public abstract @Nullable JsonSchemaObject getUnevaluatedItemsSchema();

    public abstract @Nullable JsonSchemaObject getContainsSchema();

    public abstract @Nullable List<? extends JsonSchemaObject> getItemsSchemaList();

    public abstract @Nullable Integer getMaxItems();

    public abstract @Nullable Integer getMinItems();

    public abstract boolean isUniqueItems();

    public abstract @Nullable Integer getMaxProperties();

    public abstract @Nullable Integer getMinProperties();

    public abstract @Nullable Set<String> getRequired();

    public abstract @Nullable List<Object> getEnum();

    public abstract @Nullable JsonSchemaObject getNot();

    public abstract @Nullable List<IfThenElse> getIfThenElse();

    public abstract @Nullable Set<JsonSchemaType> getTypeVariants();

    public abstract @Nullable String getRef();

    public abstract boolean isRefRecursive();

    public abstract boolean isRecursiveAnchor();

    public abstract @Nullable Object getDefault();

    public abstract @Nullable JsonSchemaObject getExampleByName(String name);

    public abstract @Nullable String getFormat();

    public abstract @Nullable String getId();

    public abstract @Nullable String getSchema();

    public abstract @Nullable String getDescription();

    public abstract @Nullable String getTitle();

    public abstract @Nullable JsonSchemaObject getMatchingPatternPropertySchema(String name);

    public abstract boolean checkByPattern(String value);

    public abstract @Nullable String getPatternError();

    public abstract @Nullable JsonSchemaObject findRelativeDefinition(String ref);

    public abstract @Nullable Map<String, Map<String, String>> getEnumMetadata();

    public abstract @Nullable Map<String, List<String>> getPropertyDependencies();


    // Recently introduced methods that replace old inconvenient ones
    public abstract @Nullable JsonSchemaObject getDefinitionByName(String name);

    public abstract Iterator<String> getDefinitionNames();

    public abstract @Nullable String readChildNodeValue(String childNodeName);

    public abstract boolean hasChildNode(String childNodeName);

    public abstract Iterator<String> getPropertyNames();

    public abstract @Nullable JsonSchemaObject getPropertyByName(String name);

    public abstract Iterator<String> getSchemaDependencyNames();

    public abstract @Nullable JsonSchemaObject getSchemaDependencyByName(String name);

    // custom metadata provided by schemas, can be used in IDE features
    // the format in the schema is a key with either a single string value or an array of string values
    public abstract @Nullable List<JsonSchemaMetadataEntry> getMetadata();

    // also remove?
    public abstract @Nullable List<? extends JsonSchemaObject> getAllOf();

    public abstract @Nullable List<? extends JsonSchemaObject> getAnyOf();

    public abstract @Nullable List<? extends JsonSchemaObject> getOneOf();

    /**
     * @deprecated use {@link JsonSchemaObject#getSchemaDependencyNames} and {@link JsonSchemaObject#getSchemaDependencyByName}
     */
    @Deprecated
    @DeprecationInfo("Use #getSchemaDependencyNames and #getSchemaDependencyByName")
    public abstract @Nullable Map<String, ? extends JsonSchemaObject> getSchemaDependencies();

    // Previously static methods that were moved to utility class to avoid pollution of the JsonSchemaObject API class

    /**
     * @deprecated use {@link JsonSchemaObjectReadingUtils#getTypeDescription)}
     */
    @Deprecated
    @DeprecationInfo("Use JsonSchemaObjectReadingUtils#getTypeDescription")
    public @Nullable String getTypeDescription(boolean shortDesc) {
        return JsonSchemaObjectReadingUtils.getTypeDescription(this, shortDesc);
    }

    // Candidates for removal

    /**
     * @deprecated use {@link JsonSchemaObject#readChildNodeValue)} with the corresponding sub-node path as a parameter
     */
    @Deprecated
    @DeprecationInfo("Use #readChildNodeValue")
    public abstract @Nullable String getHtmlDescription();

    /**
     * @deprecated use {@link JsonSchemaObject#readChildNodeValue)} with the corresponding parameter
     */
    @Deprecated
    @DeprecationInfo("Use #readChildNodeValue")
    public abstract boolean isForceCaseInsensitive();

    /**
     * @deprecated use {@link JsonSchemaObject#readChildNodeValue)} with the corresponding parameter
     */
    @Deprecated
    @DeprecationInfo("Use #readChildNodeValue")
    public abstract @Nullable String getLanguageInjection();

    /**
     * @deprecated use {@link JsonSchemaTraversalUtils#getChildAsText(JsonSchemaObject, String...)} with the corresponding parameter
     */
    @Deprecated
    @DeprecationInfo("Use JsonSchemaTraversalUtils#getChildAsText")
    public abstract @Nullable String getLanguageInjectionPrefix();

    /**
     * @deprecated use {@link JsonSchemaTraversalUtils#getChildAsText(JsonSchemaObject, String...)} with the corresponding parameter
     */
    @Deprecated
    @DeprecationInfo("Use JsonSchemaTraversalUtils#getChildAsText")
    public abstract @Nullable String getLanguageInjectionPostfix();

    /**
     * @deprecated use {@link JsonSchemaObject#hasChildNode)} with the corresponding parameter
     */
    @Deprecated
    @DeprecationInfo("Use #hasChildNode")
    public abstract boolean isShouldValidateAgainstJSType();

    /**
     * @deprecated use {@link JsonSchemaObjectReadingUtils#resolveRefSchema}
     */
    @Deprecated
    @DeprecationInfo("Use JsonSchemaObjectReadingUtils#resolveRefSchema")
    public abstract @Nullable JsonSchemaObject resolveRefSchema(JsonSchemaService service);
}
