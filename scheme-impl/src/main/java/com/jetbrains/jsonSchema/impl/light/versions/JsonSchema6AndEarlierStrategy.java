// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.versions;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.light.JsonSchemaRefResolver;
import com.jetbrains.jsonSchema.impl.light.LocalSchemaReferenceResolver;
import com.jetbrains.jsonSchema.impl.light.RemoteSchemaReferenceResolver;
import com.jetbrains.jsonSchema.impl.validations.JsonSchemaValidationsCollector;
import jakarta.annotation.Nullable;

import java.util.Arrays;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.*;

public class JsonSchema6AndEarlierStrategy implements JsonSchemaInterpretationStrategy {
  public static final JsonSchema6AndEarlierStrategy INSTANCE = new JsonSchema6AndEarlierStrategy();

  private JsonSchema6AndEarlierStrategy() {}

  @Override
  public String getIdKeyword() {
    return JSON_ID;
  }

  @Override
  public String getNonPositionalItemsKeyword() {
    return ADDITIONAL_ITEMS;
  }

  @Override
  public String getPositionalItemsKeyword() {
    return ITEMS;
  }

  @Override
  public String getDefinitionsKeyword() {
    return JSON_DEFINITIONS;
  }

  @Nullable
  @Override
  public String getDynamicReferenceKeyword() {
    return null;
  }

  @Nullable
  @Override
  public String getDynamicAnchorKeyword() {
    return null;
  }

  @Override
  public String getDependencySchemasKeyword() {
    return DEPENDENCIES;
  }

  @Override
  public String getPropertyDependenciesKeyword() {
    return DEPENDENCIES;
  }

  @Nullable
  @Override
  public String getUnevaluatedItemsKeyword() {
    return null;
  }

  @Nullable
  @Override
  public String getUnevaluatedPropertiesKeyword() {
    return null;
  }

  @Override
  public Iterable<JsonSchemaRefResolver> getReferenceResolvers() {
    return Arrays.asList(
      LocalSchemaReferenceResolver.INSTANCE,
      RemoteSchemaReferenceResolver.INSTANCE
    );
  }

  @Override
  public Iterable<JsonSchemaValidation> getValidations(JsonSchemaObject schemaNode, @Nullable JsonSchemaType type, JsonValueAdapter value) {
    return JsonSchemaValidationsCollector.getSchema7AndEarlierValidations(schemaNode, type, value);
  }
}
