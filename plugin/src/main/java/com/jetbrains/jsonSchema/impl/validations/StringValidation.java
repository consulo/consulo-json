// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.validations;

import consulo.json.localize.JsonLocalize;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.fus.JsonSchemaFusCountedFeature;
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector;
import com.jetbrains.jsonSchema.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static com.jetbrains.jsonSchema.impl.JsonSchemaAnnotatorChecker.getValue;

public final class StringValidation implements JsonSchemaValidation {
  public static final StringValidation INSTANCE = new StringValidation();
  @Override
  public boolean validate(@Nonnull JsonValueAdapter propValue,
                          @Nonnull JsonSchemaObject schema,
                          @Nullable JsonSchemaType schemaType,
                          @Nonnull JsonValidationHost consumer,
                          @Nonnull JsonComplianceCheckerOptions options) {
    JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.StringValidation);
    return checkString(propValue.getDelegate(), schema, consumer, options);
  }

  private static boolean checkString(PsiElement propValue,
                                  JsonSchemaObject schema,
                                  JsonValidationHost consumer,
                                  @Nonnull JsonComplianceCheckerOptions options) {
    String v = getValue(propValue, schema);
    if (v == null) return true;
    final String value = StringUtil.unquoteString(v);
    if (schema.getMinLength() != null) {
      if (value.length() < schema.getMinLength()) {
        consumer.error(JsonLocalize.schemaValidationStringShorterThan(schema.getMinLength().get()), propValue, JsonErrorPriority.LOW_PRIORITY);
        return false;
      }
    }
    if (schema.getMaxLength() != null) {
      if (value.length() > schema.getMaxLength()) {
        consumer.error(JsonLocalize.schemaValidationStringLongerThan(schema.getMaxLength().get()), propValue, JsonErrorPriority.LOW_PRIORITY);
        return false;
      }
    }
    if (schema.getPattern() != null) {
      if (schema.getPatternError() != null) {
        consumer.error(JsonLocalize.schemaValidationInvalidStringPattern(StringUtil.convertLineSeparators(schema.getPatternError().get())),
              propValue, JsonErrorPriority.LOW_PRIORITY);
        return false;
      }
      if (!schema.checkByPattern(value)) {
        consumer.error(JsonLocalize.schemaValidationStringViolatesPattern(StringUtil.convertLineSeparators(schema.getPattern().get())), propValue, JsonErrorPriority.LOW_PRIORITY);
        return false;
      }
    }
    return true;
  }
}
