// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.validations;

import consulo.json.localize.JsonLocalize;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.JsonValidationError;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.fus.JsonSchemaFusCountedFeature;
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector;
import com.jetbrains.jsonSchema.impl.*;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public final class NumericValidation implements JsonSchemaValidation {
  public static final NumericValidation INSTANCE = new NumericValidation();
  private static boolean checkNumber(PsiElement propValue,
                                     JsonSchemaObject schema,
                                     JsonSchemaType schemaType,
                                     JsonValidationHost consumer,
                                     @Nonnull JsonComplianceCheckerOptions options) {
    Number value;
    String valueText = JsonSchemaAnnotatorChecker.getValue(propValue, schema);
    if (valueText == null) return true;
    if (JsonSchemaType._integer.equals(schemaType)) {
      value = JsonSchemaType.getIntegerValue(valueText);
      if (value == null) {
        consumer.error(JsonLocalize.schemaValidationIntegerExpected().get(), propValue,
                       JsonValidationError.FixableIssueKind.TypeMismatch,
                       new JsonValidationError.TypeMismatchIssueData(new JsonSchemaType[]{schemaType}), JsonErrorPriority.TYPE_MISMATCH);
        return false;
      }
    }
    else {
      try {
        value = Double.valueOf(valueText);
      }
      catch (NumberFormatException e) {
        if (!JsonSchemaType._string_number.equals(schemaType)) {
          consumer.error(JsonLocalize.schemaValidationNumberExpected().get(), propValue,
                JsonValidationError.FixableIssueKind.TypeMismatch,
                new JsonValidationError.TypeMismatchIssueData(new JsonSchemaType[]{schemaType}), JsonErrorPriority.TYPE_MISMATCH);
          return false;
        }
        return true;
      }
    }
    final Number multipleOf = schema.getMultipleOf();
    if (multipleOf != null) {
      final double leftOver = value.doubleValue() % multipleOf.doubleValue();
      if (leftOver > 0.000001) {
        final String multipleOfValue = String.valueOf(Math.abs(multipleOf.doubleValue() - multipleOf.intValue()) < 0.000001 ?
                                                      multipleOf.intValue() : multipleOf);
        consumer.error(JsonLocalize.schemaValidationNotMultipleOf(multipleOfValue).get(), propValue, JsonErrorPriority.LOW_PRIORITY);
        return false;
      }
    }

    return checkMinimum(schema, value, propValue, consumer, options) &
           checkMaximum(schema, value, propValue, consumer, options);
  }

  private static boolean checkMaximum(JsonSchemaObject schema,
                                   Number value,
                                   PsiElement propertyValue,
                                   JsonValidationHost consumer,
                                   @Nonnull JsonComplianceCheckerOptions options) {
    var isValid = true;
    Number exclusiveMaximumNumber = schema.getExclusiveMaximumNumber();
    if (exclusiveMaximumNumber != null) {
      final double doubleValue = exclusiveMaximumNumber.doubleValue();
      if (value.doubleValue() >= doubleValue) {
        consumer.error(JsonLocalize.schemaValidationGreaterThanExclusiveMaximum(exclusiveMaximumNumber).get(), propertyValue, JsonErrorPriority.LOW_PRIORITY);
        isValid = false;
        if (options.shouldStopValidationAfterAnyErrorFound()) return false;
      }
    }
    Number maximum = schema.getMaximum();
    if (maximum == null) return isValid;
    boolean isExclusive = schema.isExclusiveMaximum();
    final double doubleValue = maximum.doubleValue();
    if (isExclusive) {
      if (value.doubleValue() >= doubleValue) {
        consumer.error(JsonLocalize.schemaValidationGreaterThanExclusiveMaximum(maximum).get(), propertyValue, JsonErrorPriority.LOW_PRIORITY);
        isValid = false;
        if (options.shouldStopValidationAfterAnyErrorFound()) return false;
      }
    }
    else {
      if (value.doubleValue() > doubleValue) {
        consumer.error(JsonLocalize.schemaValidationGreaterThanMaximum(maximum).get(), propertyValue, JsonErrorPriority.LOW_PRIORITY);
        isValid = false;
        if (options.shouldStopValidationAfterAnyErrorFound()) return false;
      }
    }

    return isValid;
  }

  private static boolean checkMinimum(JsonSchemaObject schema,
                                      Number value,
                                      PsiElement propertyValue,
                                      JsonValidationHost consumer, @Nonnull JsonComplianceCheckerOptions options) {
    var isValid = true;
    // schema v6 - exclusiveMinimum is numeric now
    Number exclusiveMinimumNumber = schema.getExclusiveMinimumNumber();
    if (exclusiveMinimumNumber != null) {
      final double doubleValue = exclusiveMinimumNumber.doubleValue();
      if (value.doubleValue() <= doubleValue) {
        consumer.error(JsonLocalize.schemaValidationLessThanExclusiveMinimum(exclusiveMinimumNumber).get(), propertyValue, JsonErrorPriority.LOW_PRIORITY);
        isValid = false;
        if (options.shouldStopValidationAfterAnyErrorFound()) return false;
      }
    }

    Number minimum = schema.getMinimum();
    if (minimum == null) return isValid;
    boolean isExclusive = schema.isExclusiveMinimum();
    final double doubleValue = minimum.doubleValue();
    if (isExclusive) {
      if (value.doubleValue() <= doubleValue) {
        consumer.error(JsonLocalize.schemaValidationLessThanExclusiveMinimum(minimum).get(), propertyValue, JsonErrorPriority.LOW_PRIORITY);
        isValid = false;
        if (options.shouldStopValidationAfterAnyErrorFound()) return false;
      }
    }
    else {
      if (value.doubleValue() < doubleValue) {
        consumer.error(JsonLocalize.schemaValidationLessThanMinimum(minimum).get(), propertyValue, JsonErrorPriority.LOW_PRIORITY);
        isValid = false;
        if (options.shouldStopValidationAfterAnyErrorFound()) return false;
      }
    }

    return isValid;
  }

  @Override
  public boolean validate(@Nonnull JsonValueAdapter propValue,
                          @Nonnull JsonSchemaObject schema,
                          @Nullable JsonSchemaType schemaType,
                          @Nonnull JsonValidationHost consumer,
                          @Nonnull JsonComplianceCheckerOptions options) {
    JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.NumberValidation);
    return checkNumber(propValue.getDelegate(), schema, schemaType, consumer, options);
  }
}
