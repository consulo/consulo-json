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

import com.jetbrains.jsonSchema.IfThenElse;
import com.jetbrains.jsonSchema.JsonSchemaMetadataEntry;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EmptyJsonSchemaObject extends JsonSchemaObject {
    public static final EmptyJsonSchemaObject INSTANCE = new EmptyJsonSchemaObject();

    private EmptyJsonSchemaObject() {
    }

    @Override
    public @Nullable Boolean getConstantSchema() {
        return null;
    }

    @Override
    public boolean hasChildFieldsExcept(List<String> namesToSkip) {
        return false;
    }

    @Override
    public Iterable<JsonSchemaValidation> getValidations(@Nullable JsonSchemaType type, JsonValueAdapter value) {
        return List.of();
    }

    @Override
    public JsonSchemaObject getRootSchemaObject() {
        return this;
    }

    @Override
    public boolean isValidByExclusion() {
        return false;
    }

    @Override
    public String getPointer() {
        return "$_NULL_$";
    }

    @Override
    public @Nullable String getFileUrl() {
        return null;
    }

    @Override
    public @Nullable VirtualFile getRawFile() {
        return null;
    }

    @Override
    public boolean hasPatternProperties() {
        return false;
    }

    @Override
    public @Nullable JsonSchemaType getType() {
        return null;
    }

    @Override
    public @Nullable Number getMultipleOf() {
        return null;
    }

    @Override
    public @Nullable Number getMaximum() {
        return null;
    }

    @Override
    public boolean isExclusiveMaximum() {
        return false;
    }

    @Override
    public @Nullable Number getExclusiveMaximumNumber() {
        return null;
    }

    @Override
    public @Nullable Number getExclusiveMinimumNumber() {
        return null;
    }

    @Override
    public @Nullable Number getMinimum() {
        return null;
    }

    @Override
    public boolean isExclusiveMinimum() {
        return false;
    }

    @Override
    public @Nullable Integer getMaxLength() {
        return null;
    }

    @Override
    public @Nullable Integer getMinLength() {
        return null;
    }

    @Override
    public @Nullable String getPattern() {
        return null;
    }

    @Override
    public boolean getAdditionalPropertiesAllowed() {
        return false;
    }

    @Override
    public boolean hasOwnExtraPropertyProhibition() {
        return false;
    }

    @Override
    public @Nullable JsonSchemaObject getPropertyNamesSchema() {
        return null;
    }

    @Override
    public @Nullable JsonSchemaObject getAdditionalPropertiesSchema() {
        return null;
    }

    @Override
    public @Nullable JsonSchemaObject getUnevaluatedPropertiesSchema() {
        return null;
    }

    @Override
    public @Nullable Boolean getAdditionalItemsAllowed() {
        return null;
    }

    @Override
    public @Nullable String getDeprecationMessage() {
        return null;
    }

    @Override
    public @Nullable JsonSchemaObject getAdditionalItemsSchema() {
        return null;
    }

    @Override
    public @Nullable JsonSchemaObject getItemsSchema() {
        return null;
    }

    @Override
    public @Nullable JsonSchemaObject getUnevaluatedItemsSchema() {
        return null;
    }

    @Override
    public @Nullable JsonSchemaObject getContainsSchema() {
        return null;
    }

    @Override
    public @Nullable List<JsonSchemaObject> getItemsSchemaList() {
        return null;
    }

    @Override
    public @Nullable Integer getMaxItems() {
        return null;
    }

    @Override
    public @Nullable Integer getMinItems() {
        return null;
    }

    @Override
    public boolean isUniqueItems() {
        return false;
    }

    @Override
    public @Nullable Integer getMaxProperties() {
        return null;
    }

    @Override
    public @Nullable Integer getMinProperties() {
        return null;
    }

    @Override
    public @Nullable Set<String> getRequired() {
        return null;
    }

    @Override
    public @Nullable List<Object> getEnum() {
        return null;
    }

    @Override
    public @Nullable JsonSchemaObject getNot() {
        return null;
    }

    @Override
    public @Nullable List<IfThenElse> getIfThenElse() {
        return null;
    }

    @Override
    public @Nullable Set<JsonSchemaType> getTypeVariants() {
        return null;
    }

    @Override
    public @Nullable String getRef() {
        return null;
    }

    @Override
    public boolean isRefRecursive() {
        return false;
    }

    @Override
    public boolean isRecursiveAnchor() {
        return false;
    }

    @Override
    public @Nullable Object getDefault() {
        return null;
    }

    @Override
    public @Nullable JsonSchemaObject getExampleByName(String name) {
        return null;
    }

    @Override
    public @Nullable String getFormat() {
        return null;
    }

    @Override
    public @Nullable String getId() {
        return null;
    }

    @Override
    public @Nullable String getSchema() {
        return null;
    }

    @Override
    public @Nullable String getDescription() {
        return null;
    }

    @Override
    public @Nullable String getTitle() {
        return null;
    }

    @Override
    public @Nullable JsonSchemaObject getMatchingPatternPropertySchema(String name) {
        return null;
    }

    @Override
    public boolean checkByPattern(String value) {
        return false;
    }

    @Override
    public @Nullable String getPatternError() {
        return null;
    }

    @Override
    public @Nullable JsonSchemaObject findRelativeDefinition(String ref) {
        return null;
    }

    @Override
    public @Nullable Map<String, Map<String, String>> getEnumMetadata() {
        return null;
    }

    @Override
    public @Nullable Map<String, List<String>> getPropertyDependencies() {
        return null;
    }

    @Override
    public @Nullable JsonSchemaObject getDefinitionByName(String name) {
        return null;
    }

    @Override
    public Iterator<String> getDefinitionNames() {
        return Collections.emptyIterator();
    }

    @Override
    public @Nullable String readChildNodeValue(String childNodeName) {
        return null;
    }

    @Override
    public boolean hasChildNode(String childNodeName) {
        return false;
    }

    @Override
    public Iterator<String> getPropertyNames() {
        return Collections.emptyIterator();
    }

    @Override
    public @Nullable JsonSchemaObject getPropertyByName(String name) {
        return null;
    }

    @Override
    public Iterator<String> getSchemaDependencyNames() {
        return Collections.emptyIterator();
    }

    @Override
    public @Nullable JsonSchemaObject getSchemaDependencyByName(String name) {
        return null;
    }

    @Override
    public @Nullable List<JsonSchemaMetadataEntry> getMetadata() {
        return null;
    }

    @Override
    public @Nullable List<JsonSchemaObject> getAllOf() {
        return null;
    }

    @Override
    public @Nullable List<JsonSchemaObject> getAnyOf() {
        return null;
    }

    @Override
    public @Nullable List<JsonSchemaObject> getOneOf() {
        return null;
    }

    @Override
    public @Nullable Map<String, JsonSchemaObject> getSchemaDependencies() {
        return null;
    }

    @Override
    public @Nullable String getHtmlDescription() {
        return null;
    }

    @Override
    public boolean isForceCaseInsensitive() {
        return false;
    }

    @Override
    public @Nullable String getLanguageInjection() {
        return null;
    }

    @Override
    public @Nullable String getLanguageInjectionPrefix() {
        return null;
    }

    @Override
    public @Nullable String getLanguageInjectionPostfix() {
        return null;
    }

    @Override
    public boolean isShouldValidateAgainstJSType() {
        return false;
    }

    @Override
    public @Nullable JsonSchemaObject resolveRefSchema(JsonSchemaService service) {
        return null;
    }
}
