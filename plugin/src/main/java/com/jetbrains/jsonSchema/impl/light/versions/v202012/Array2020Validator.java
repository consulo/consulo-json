// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.versions.v202012;

import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.validations.ArrayValidation;
import consulo.json.localize.JsonLocalize;
import org.jetbrains.annotations.Nls;

import java.util.List;

public class Array2020Validator extends ArrayValidation {
  public static final Array2020Validator INSTANCE = new Array2020Validator();

  private Array2020Validator() {}

  @Override
  protected boolean validateIndividualItems(List<JsonValueAdapter> instanceArrayItems,
                                             JsonSchemaObject schema,
                                             JsonValidationHost consumer,
                                             JsonComplianceCheckerOptions options) {
    List<JsonSchemaObject> additionalItemsSchemaList = schema.getItemsSchemaList();
    int firstRegularItemIndex = (additionalItemsSchemaList == null || additionalItemsSchemaList.isEmpty()) ? 0 : additionalItemsSchemaList.size();

    boolean isValid = true;

    // check instance items with positional schema
    for (int index = 0; index < firstRegularItemIndex; index++) {
      if (additionalItemsSchemaList == null || index >= additionalItemsSchemaList.size()) break;
      JsonSchemaObject positionalSchema = additionalItemsSchemaList.get(index);
      if (index >= instanceArrayItems.size()) break;
      JsonValueAdapter inspectedInstanceItem = instanceArrayItems.get(index);
      consumer.checkObjectBySchemaRecordErrors(positionalSchema, inspectedInstanceItem);

      isValid = isValid && consumer.getErrors().isEmpty();
      if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) return false;
    }

    // check the rest of instance items with regular schema
    JsonSchemaObject additionalItemsSchema = schema.getAdditionalItemsSchema();
    if (additionalItemsSchema != null) {
      isValid = isValid && validateAgainstNonPositionalSchema(
        additionalItemsSchema,
        instanceArrayItems,
        firstRegularItemIndex,
        consumer,
        options,
        JsonLocalize.schemaValidationArrayNoExtra().get()
      );
      if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) return false;
    }

    JsonSchemaObject unevaluatedItemsSchema = schema.getUnevaluatedItemsSchema();
    if (unevaluatedItemsSchema != null) {
      isValid = isValid && validateAgainstNonPositionalSchema(
        unevaluatedItemsSchema,
        instanceArrayItems,
        firstRegularItemIndex,
        consumer,
        options,
        JsonLocalize.schemaValidationArrayNoUnevaluated().get()
      );
      if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) return false;
    }

    return isValid;
  }

  private boolean validateAgainstNonPositionalSchema(JsonSchemaObject nonPositionalItemsSchema,
                                                      List<JsonValueAdapter> instanceArrayItems,
                                                      int firstRegularItemIndex,
                                                      JsonValidationHost consumer,
                                                      JsonComplianceCheckerOptions options,
                                                      @Nls String errorMessage) {
    Boolean constantSchema = nonPositionalItemsSchema.getConstantSchema();
    if (Boolean.TRUE.equals(constantSchema)) {
      return true;
    }

    if (Boolean.FALSE.equals(constantSchema) && firstRegularItemIndex < instanceArrayItems.size()) {
      consumer.error(errorMessage, instanceArrayItems.get(firstRegularItemIndex).getDelegate(), JsonErrorPriority.LOW_PRIORITY);
      return false;
    }

    boolean isValid = true;
    for (int index = firstRegularItemIndex; index < instanceArrayItems.size(); index++) {
      JsonValueAdapter instanceArrayItem = instanceArrayItems.get(index);
      consumer.checkObjectBySchemaRecordErrors(nonPositionalItemsSchema, instanceArrayItem);

      isValid = isValid && consumer.getErrors().isEmpty();
      if (!isValid && options.shouldStopValidationAfterAnyErrorFound()) return false;
    }
    return isValid;
  }
}
