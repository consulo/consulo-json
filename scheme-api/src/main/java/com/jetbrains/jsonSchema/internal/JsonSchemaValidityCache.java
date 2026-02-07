// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import com.jetbrains.jsonSchema.JsonAnnotationsCollectionMode;
import com.jetbrains.jsonSchema.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.language.psi.PsiElement;
import consulo.util.dataholder.Key;

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
        delegatePsi.getManager().getModificationTracker().getModificationTracker(),
        //delegatePsi.getManager().getModificationTracker().forLanguage(delegatePsi.getLanguage()),
        JsonSchemaService.get(delegatePsi.getProject())
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
