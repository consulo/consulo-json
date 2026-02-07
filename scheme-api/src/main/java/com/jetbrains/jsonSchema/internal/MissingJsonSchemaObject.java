// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import com.fasterxml.jackson.databind.node.MissingNode;
import com.jetbrains.jsonSchema.IfThenElse;
import com.jetbrains.jsonSchema.JsonSchemaMetadataEntry;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.jetbrains.jsonSchema.internal.SchemaKeywords.SCHEMA_ROOT_POINTER;

public final class MissingJsonSchemaObject extends JsonSchemaObjectBackedByJacksonBase {
  private static final String ERROR_MESSAGE = "MissingJsonSchemaObject does not provide any meaningful method implementations";

  public static final MissingJsonSchemaObject INSTANCE = new MissingJsonSchemaObject();

  private MissingJsonSchemaObject() {
    super(MissingNode.getInstance(), SCHEMA_ROOT_POINTER);
  }

  @Override
  public boolean isValidByExclusion() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nonnull
  @Override
  public String getPointer() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public String getFileUrl() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public VirtualFile getRawFile() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Override
  public boolean hasPatternProperties() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nonnull
  @Override
  public RootJsonSchemaObjectBackedByJackson getRootSchemaObject() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public JsonSchemaType getType() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public Boolean getConstantSchema() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public Number getMultipleOf() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public Number getMaximum() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Override
  public boolean isExclusiveMaximum() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public Number getExclusiveMaximumNumber() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public Number getExclusiveMinimumNumber() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public Number getMinimum() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Override
  public boolean isExclusiveMinimum() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public Integer getMaxLength() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public Integer getMinLength() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public String getPattern() {
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

  @Nullable
  @Override
  public JsonSchemaObject getPropertyNamesSchema() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public JsonSchemaObject getAdditionalPropertiesSchema() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Override
  public Boolean getAdditionalItemsAllowed() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public String getDeprecationMessage() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public JsonSchemaObject getAdditionalItemsSchema() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public JsonSchemaObject getItemsSchema() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public JsonSchemaObject getContainsSchema() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public List<? extends JsonSchemaObject> getItemsSchemaList() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public Integer getMaxItems() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public Integer getMinItems() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Override
  public boolean isUniqueItems() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public Integer getMaxProperties() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public Integer getMinProperties() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public Set<String> getRequired() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public Map<String, List<String>> getPropertyDependencies() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public List<Object> getEnum() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public List<? extends JsonSchemaObject> getAllOf() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public List<? extends JsonSchemaObject> getAnyOf() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public List<? extends JsonSchemaObject> getOneOf() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public JsonSchemaObject getNot() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public List<IfThenElse> getIfThenElse() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public Set<JsonSchemaType> getTypeVariants() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public String getRef() {
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

  @Nullable
  @Override
  public Object getDefault() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public String getFormat() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public String getId() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public String getSchema() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public String getDescription() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public String getTitle() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public JsonSchemaObject getMatchingPatternPropertySchema(@Nonnull String name) {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Override
  public boolean checkByPattern(@Nonnull String value) {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public String getPatternError() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public Map<String, Map<String, String>> getEnumMetadata() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public String getHtmlDescription() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Override
  public boolean isForceCaseInsensitive() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public String getLanguageInjection() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public String getLanguageInjectionPrefix() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public String getLanguageInjectionPostfix() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Override
  public boolean isShouldValidateAgainstJSType() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public Map<String, ? extends JsonSchemaObject> getDefinitionsMap() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nonnull
  @Override
  public Map<String, JsonSchemaObjectBackedByJacksonBase> getProperties() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public List<JsonSchemaMetadataEntry> getMetadata() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Override
  public boolean hasChildFieldsExcept(@Nonnull List<String> namesToSkip) {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }

  @Nullable
  @Override
  public Map<String, JsonSchemaObject> getSchemaDependencies() {
    throw new UnsupportedOperationException(ERROR_MESSAGE);
  }
}
