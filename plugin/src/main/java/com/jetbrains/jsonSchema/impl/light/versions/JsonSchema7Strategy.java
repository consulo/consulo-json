// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.versions;

import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import com.jetbrains.jsonSchema.impl.light.*;
import com.jetbrains.jsonSchema.impl.validations.JsonSchemaValidationsCollector;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Iterator;

public class JsonSchema7Strategy implements JsonSchemaInterpretationStrategy {
  public static final JsonSchema7Strategy INSTANCE = new JsonSchema7Strategy();

  private JsonSchema7Strategy() {}

  @Override
  public String getIdKeyword() {
    return JsonSchemaObjectKeywords.JSON_DOLLAR_ID;
  }

  @Override
  public String getNonPositionalItemsKeyword() {
    return JsonSchemaObjectKeywords.ADDITIONAL_ITEMS;
  }

  @Override
  public String getPositionalItemsKeyword() {
    return JsonSchemaObjectKeywords.ITEMS;
  }

  @Override
  public String getDefinitionsKeyword() {
    return JsonSchemaObjectKeywords.JSON_DEFINITIONS;
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
    return JsonSchemaObjectKeywords.DEPENDENCIES;
  }

  @Override
  public String getPropertyDependenciesKeyword() {
    return JsonSchemaObjectKeywords.DEPENDENCIES;
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
  public Iterator<JsonSchemaRefResolver> getReferenceResolvers() {
    return Arrays.asList(
      LocalSchemaReferenceResolver.INSTANCE,
      RemoteSchemaReferenceResolver.INSTANCE
    ).iterator();
  }

  @Override
  public Iterable<JsonSchemaValidation> getValidations(JsonSchemaObject schemaNode, @Nullable JsonSchemaType type, JsonValueAdapter value) {
    return JsonSchemaValidationsCollector.getSchema7AndEarlierValidations(schemaNode, type, value);
  }
}
