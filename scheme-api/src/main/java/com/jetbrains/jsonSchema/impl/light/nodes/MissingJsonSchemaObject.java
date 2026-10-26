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

import tools.jackson.databind.node.MissingNode;
import com.jetbrains.jsonSchema.IfThenElse;
import com.jetbrains.jsonSchema.JsonSchemaMetadataEntry;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.SCHEMA_ROOT_POINTER;

public final class MissingJsonSchemaObject extends JsonSchemaObjectBackedByJacksonBase {
    public static final MissingJsonSchemaObject INSTANCE = new MissingJsonSchemaObject();

    private static final String ERROR_MESSAGE = "MissingJsonSchemaObject does not provide any meaningful method implementations";

    private MissingJsonSchemaObject() {
        super(MissingNode.getInstance(), SCHEMA_ROOT_POINTER);
    }

    @Override
    public boolean isValidByExclusion() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public String getPointer() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable String getFileUrl() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable VirtualFile getRawFile() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public boolean hasPatternProperties() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public RootJsonSchemaObjectBackedByJackson getRootSchemaObject() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable JsonSchemaType getType() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable Boolean getConstantSchema() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable Number getMultipleOf() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable Number getMaximum() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public boolean isExclusiveMaximum() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable Number getExclusiveMaximumNumber() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable Number getExclusiveMinimumNumber() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable Number getMinimum() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public boolean isExclusiveMinimum() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable Integer getMaxLength() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable Integer getMinLength() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable String getPattern() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public boolean getAdditionalPropertiesAllowed() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public boolean hasOwnExtraPropertyProhibition() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable JsonSchemaObject getPropertyNamesSchema() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable JsonSchemaObject getAdditionalPropertiesSchema() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public Boolean getAdditionalItemsAllowed() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable String getDeprecationMessage() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable JsonSchemaObject getAdditionalItemsSchema() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable JsonSchemaObject getItemsSchema() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable JsonSchemaObject getContainsSchema() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable List<JsonSchemaObject> getItemsSchemaList() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable Integer getMaxItems() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable Integer getMinItems() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public boolean isUniqueItems() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable Integer getMaxProperties() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable Integer getMinProperties() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable Set<String> getRequired() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable Map<String, List<String>> getPropertyDependencies() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable List<Object> getEnum() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable List<JsonSchemaObject> getAllOf() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable List<JsonSchemaObject> getAnyOf() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable List<JsonSchemaObject> getOneOf() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable JsonSchemaObject getNot() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable List<IfThenElse> getIfThenElse() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable Set<JsonSchemaType> getTypeVariants() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable String getRef() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public boolean isRefRecursive() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public boolean isRecursiveAnchor() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable Object getDefault() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable String getFormat() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable String getId() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable String getSchema() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable String getDescription() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable String getTitle() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable JsonSchemaObject getMatchingPatternPropertySchema(String name) {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public boolean checkByPattern(String value) {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable String getPatternError() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable Map<String, Map<String, String>> getEnumMetadata() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable String getHtmlDescription() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public boolean isForceCaseInsensitive() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable String getLanguageInjection() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable String getLanguageInjectionPrefix() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable String getLanguageInjectionPostfix() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public boolean isShouldValidateAgainstJSType() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable List<JsonSchemaMetadataEntry> getMetadata() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public boolean hasChildFieldsExcept(List<String> namesToSkip) {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }

    @Override
    public @Nullable Map<String, JsonSchemaObject> getSchemaDependencies() {
        throw new UnsupportedOperationException(ERROR_MESSAGE);
    }
}
