// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.versions.v202012;

import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import com.jetbrains.jsonSchema.impl.light.*;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy;
import com.jetbrains.jsonSchema.impl.validations.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class JsonSchema202012Strategy implements JsonSchemaInterpretationStrategy {
  public static final JsonSchema202012Strategy INSTANCE = new JsonSchema202012Strategy();

  private JsonSchema202012Strategy() {}

  @Override
  public String getIdKeyword() {
    return JsonSchemaObjectKeywords.JSON_DOLLAR_ID;
  }

  @Override
  public String getNonPositionalItemsKeyword() {
    return JsonSchemaObjectKeywords.ITEMS;
  }

  @Override
  public String getPositionalItemsKeyword() {
    return JsonSchemaObjectKeywords.PREFIX_ITEMS;
  }

  @Override
  public String getDefinitionsKeyword() {
    return JsonSchemaObjectKeywords.DEFS;
  }

  @Override
  public String getDynamicReferenceKeyword() {
    return JsonSchemaObjectKeywords.DYNAMIC_REF;
  }

  @Override
  public String getDynamicAnchorKeyword() {
    return JsonSchemaObjectKeywords.DYNAMIC_ANCHOR;
  }

  @Override
  public String getDependencySchemasKeyword() {
    return JsonSchemaObjectKeywords.DEPENDENT_SCHEMAS;
  }

  @Override
  public String getPropertyDependenciesKeyword() {
    return JsonSchemaObjectKeywords.DEPENDENT_REQUIRED;
  }

  @Override
  public String getUnevaluatedItemsKeyword() {
    return JsonSchemaObjectKeywords.UNEVALUATED_ITEMS;
  }

  @Override
  public String getUnevaluatedPropertiesKeyword() {
    return JsonSchemaObjectKeywords.UNEVALUATED_PROPERTIES;
  }

  @Override
  public Iterator<JsonSchemaRefResolver> getReferenceResolvers() {
    return Arrays.asList(
      LocalSchemaReferenceResolver.INSTANCE,
      RemoteSchemaReferenceResolver.INSTANCE,
      Vocabulary2020Resolver.INSTANCE
    ).iterator();
  }

  @Override
  public Iterable<JsonSchemaValidation> getValidations(JsonSchemaObject schemaNode, @Nullable JsonSchemaType type, JsonValueAdapter value) {
    List<JsonSchemaValidation> validations = new ArrayList<>();
    if (type != null) {
      validations.addAll(getTypeValidations(type));
    }
    validations.addAll(getBaseValidations(value, schemaNode));
    return validations;
  }

  private List<JsonSchemaValidation> getBaseValidations(JsonValueAdapter value, JsonSchemaObject schemaNode) {
    if (schemaNode.getConstantSchema() != null) {
      return Arrays.asList(ConstantSchemaValidation.INSTANCE);
    }

    List<JsonSchemaValidation> validations = new ArrayList<>();
    validations.add(EnumValidation.INSTANCE);

    if (!value.isShouldBeIgnored()) {
      if (JsonSchemaObjectReadingUtils.hasNumericChecks(schemaNode) && value.isNumberLiteral()) {
        validations.add(NumericValidation.INSTANCE);
      }
      if (JsonSchemaObjectReadingUtils.hasStringChecks(schemaNode) && value.isStringLiteral()) {
        validations.add(StringValidation.INSTANCE);
      }
      if (JsonSchemaObjectReadingUtils.hasArrayChecks(schemaNode) && value.isArray()) {
        validations.add(Array2020Validator.INSTANCE);
      }
      if (JsonSchemaValidationsCollector.hasMinMaxLengthChecks(schemaNode)) {
        if (value.isStringLiteral()) {
          validations.add(StringValidation.INSTANCE);
        }
        else if (value.isArray()) {
          validations.add(Array2020Validator.INSTANCE);
        }
      }
      if (JsonSchemaObjectReadingUtils.hasObjectChecks(schemaNode) && value.isObject()) {
        validations.add(ObjectValidation.INSTANCE);
      }
    }

    if (schemaNode.getNot() != null) {
      validations.add(NotValidation.INSTANCE);
    }

    return validations;
  }

  private List<JsonSchemaValidation> getTypeValidations(JsonSchemaType type) {
    List<JsonSchemaValidation> validations = new ArrayList<>();
    validations.add(TypeValidation.INSTANCE);
    switch (type) {
      case _string_number:
        validations.add(NumericValidation.INSTANCE);
        validations.add(StringValidation.INSTANCE);
        break;
      case _number:
      case _integer:
        validations.add(NumericValidation.INSTANCE);
        break;
      case _string:
        validations.add(StringValidation.INSTANCE);
        break;
      case _array:
        validations.add(Array2020Validator.INSTANCE);
        break;
      case _object:
        validations.add(ObjectValidation.INSTANCE);
        break;
      default:
        // no additional validations
        break;
    }
    return validations;
  }
}
