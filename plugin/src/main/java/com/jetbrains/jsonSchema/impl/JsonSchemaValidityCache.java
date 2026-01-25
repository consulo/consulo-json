// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.jetbrains.jsonSchema.extension.JsonAnnotationsCollectionMode;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JsonSchemaValidityCache {
  private static final Key<CachedValue<Map<JsonSchemaObject, Boolean>>> JSON_SCHEMA_VALIDATION_MAP =
    Key.create("JSON_SCHEMA_VALIDATION_MAP");

  public static boolean getOrComputeAdapterValidityAgainstGivenSchema(JsonValueAdapter value, JsonSchemaObject schema) {
    PsiElement delegatePsi = value.getDelegate();
    Map<JsonSchemaObject, Boolean> cachedMap = CachedValuesManager.getManager(delegatePsi.getProject()).getCachedValue(
      delegatePsi,
      JSON_SCHEMA_VALIDATION_MAP,
      () -> CachedValueProvider.Result.create(
        new ConcurrentHashMap<>(),
        delegatePsi.getManager().getModificationTracker().forLanguage(delegatePsi.getLanguage()),
        JsonSchemaService.Impl.get(delegatePsi.getProject())
      ),
      false
    );

    Boolean cachedValue = cachedMap.get(schema);
    if (cachedValue != null) {
      return cachedValue;
    }

    JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker(
      value.getDelegate().getProject(),
      new JsonComplianceCheckerOptions(false, false, false, JsonAnnotationsCollectionMode.FIND_FIRST)
    );
    checker.checkByScheme(value, schema);
    boolean computedValue = checker.isCorrect();

    cachedMap.put(schema, computedValue);
    return computedValue;
  }
}
