// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.validations;

import consulo.json.localize.JsonLocalize;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.fus.JsonSchemaFusCountedFeature;
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector;
import com.jetbrains.jsonSchema.impl.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import jakarta.annotation.Nullable;

public class ConstantSchemaValidation implements JsonSchemaValidation {
  public static final ConstantSchemaValidation INSTANCE = new ConstantSchemaValidation();

  @Override
  public boolean validate(JsonValueAdapter propValue,
                          JsonSchemaObject schema,
                          @Nullable JsonSchemaType schemaType,
                          JsonValidationHost consumer,
                          JsonComplianceCheckerOptions options) {
    JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.ConstantNodeValidation);
    Boolean constantSchema = schema.getConstantSchema();
    if (Boolean.FALSE.equals(constantSchema)) {
      consumer.error(JsonLocalize.schemaValidationConstantSchema().get(), propValue.getDelegate().getParent(), JsonErrorPriority.LOW_PRIORITY);
      return false;
    }
    return true;
  }
}
