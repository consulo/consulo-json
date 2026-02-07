// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import consulo.application.progress.ProgressManager;
import consulo.util.collection.Maps;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static com.jetbrains.jsonSchema.internal.JsonSchemaObjectReadingUtils.matchPattern;

public final class PatternProperties {
  public final @Nonnull Map<String, JsonSchemaObject> mySchemasMap;
  public final @Nonnull Map<String, Pattern> myCachedPatterns;
  public final @Nonnull Map<String, String> myCachedPatternProperties;

  public @Nonnull Map<String, JsonSchemaObject> getSchemasMap() {
    return mySchemasMap;
  }

  public PatternProperties(final @Nonnull Map<String, ? extends JsonSchemaObject> schemasMap) {
    mySchemasMap = new HashMap<>();
    schemasMap.keySet().forEach(key -> mySchemasMap.put(StringUtil.unescapeBackSlashes(key), schemasMap.get(key)));
    myCachedPatterns = new HashMap<>();
    myCachedPatternProperties = Maps.newConcurrentWeakKeyWeakValueHashMap();
    mySchemasMap.keySet().forEach(key -> {
      ProgressManager.checkCanceled();
      final Pair<Pattern, String> pair = JsonSchemaObjectReadingUtils.compilePattern(key);
      if (pair.getSecond() == null) {
        assert pair.getFirst() != null;
        myCachedPatterns.put(key, pair.getFirst());
      }
    });
  }

  public @Nullable JsonSchemaObject getPatternPropertySchema(final @Nonnull String name) {
    String value = myCachedPatternProperties.get(name);
    if (value != null) {
      assert mySchemasMap.containsKey(value);
      return mySchemasMap.get(value);
    }

    value = myCachedPatterns.keySet().stream()
      .filter(key -> matchPattern(myCachedPatterns.get(key), name))
      .findFirst()
      .orElse(null);
    if (value != null) {
      myCachedPatternProperties.put(name, value);
      assert mySchemasMap.containsKey(value);
      return mySchemasMap.get(value);
    }
    return null;
  }
}