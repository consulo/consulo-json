// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.jetbrains.jsonSchema.internal.JsonSchemaObjectReadingUtils;
import consulo.util.collection.impl.CollectionFactory;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Map;
import java.util.regex.Pattern;

import static com.jetbrains.jsonSchema.internal.JsonSchemaObjectReadingUtils.matchPattern;

public final class PropertyNamePattern {
  public final @Nonnull String myPattern;
  public final @Nullable Pattern myCompiledPattern;
  public final @Nullable String myPatternError;
  public final @Nonnull Map<String, Boolean> myValuePatternCache;

  public PropertyNamePattern(@Nonnull String pattern) {
    myPattern = StringUtil.unescapeBackSlashes(pattern);
    final Pair<Pattern, String> pair = JsonSchemaObjectReadingUtils.compilePattern(pattern);
    myPatternError = pair.getSecond();
    myCompiledPattern = pair.getFirst();
    myValuePatternCache = CollectionFactory.createConcurrentWeakKeyWeakValueMap();
  }

  public @Nullable String getPatternError() {
    return myPatternError;
  }

  public boolean checkByPattern(final @Nonnull String name) {
    if (myPatternError != null) return true;
    if (Boolean.TRUE.equals(myValuePatternCache.get(name))) return true;
    assert myCompiledPattern != null;
    boolean matches = matchPattern(myCompiledPattern, name);
    myValuePatternCache.put(name, matches);
    return matches;
  }

  public @Nonnull String getPattern() {
    return myPattern;
  }
}
