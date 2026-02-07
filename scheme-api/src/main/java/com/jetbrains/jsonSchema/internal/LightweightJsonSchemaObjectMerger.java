// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import consulo.application.progress.ProgressManager;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.Function;

public class LightweightJsonSchemaObjectMerger implements JsonSchemaObjectMerger {
  public static final LightweightJsonSchemaObjectMerger INSTANCE = new LightweightJsonSchemaObjectMerger();

  private LightweightJsonSchemaObjectMerger() {
  }

  @Override
  @Nonnull
  public JsonSchemaObject mergeObjects(@Nonnull JsonSchemaObject base,
                                       @Nonnull JsonSchemaObject other,
                                       @Nonnull JsonSchemaObject pointTo) {
    ProgressManager.checkCanceled();
    if (base == other) {
      return base;
    }
    return new MergedJsonSchemaObjectView(base, other, pointTo);
  }

  @Nullable
  public static <T> List<T> mergeLists(@Nonnull MergedJsonSchemaObject merged,
                                       @Nonnull Function<JsonSchemaObject, List<T>> memberReference) {
    List<T> first = memberReference.apply(merged.getBase());
    List<T> second = memberReference.apply(merged.getOther());

    if (first == null || first.isEmpty()) return second;
    if (second == null || second.isEmpty()) {
      return first;
    }
    ProgressManager.checkCanceled();
    return ContainerUtil.concat(first, second);
  }

  @Nullable
  public static <K, V> Map<K, V> mergeMaps(@Nonnull MergedJsonSchemaObject merged,
                                           @Nonnull Function<JsonSchemaObject, Map<K, V>> memberReference) {
    Map<K, V> first = memberReference.apply(merged.getBase());
    Map<K, V> second = memberReference.apply(merged.getOther());

    if (first == null || first.isEmpty()) return second;
    if (second == null || second.isEmpty()) {
      return first;
    }
    Map<K, V> mutableMerged = new HashMap<>(first);
    ProgressManager.checkCanceled();
    mutableMerged.putAll(second);
    return Map.copyOf(mutableMerged);
  }

  @Nullable
  public static <T> Set<T> mergeSets(@Nullable Set<T> first, @Nullable Set<T> second) {
    if (first == null || first.isEmpty()) return second;
    if (second == null || second.isEmpty()) {
      return first;
    }
    Set<T> merged = new HashSet<>(first);
    ProgressManager.checkCanceled();
    merged.addAll(second);
    return Set.copyOf(merged);
  }

  public static boolean booleanOr(@Nonnull JsonSchemaObject base,
                                  @Nonnull JsonSchemaObject other,
                                  @Nonnull Function<JsonSchemaObject, Boolean> memberReference) {
    boolean first = memberReference.apply(base);
    if (first) return true;
    ProgressManager.checkCanceled();
    return memberReference.apply(other);
  }

  public static boolean booleanAnd(@Nonnull JsonSchemaObject base,
                                   @Nonnull JsonSchemaObject other,
                                   @Nonnull Function<JsonSchemaObject, Boolean> memberReference) {
    boolean first = memberReference.apply(base);
    if (!first) return false;
    ProgressManager.checkCanceled();
    return memberReference.apply(other);
  }

  @Nullable
  public static Boolean booleanAndNullable(@Nonnull JsonSchemaObject base,
                                           @Nonnull JsonSchemaObject other,
                                           @Nonnull Function<JsonSchemaObject, Boolean> memberReference) {
    Boolean first = memberReference.apply(base);
    if (Boolean.FALSE.equals(first)) return false;
    ProgressManager.checkCanceled();
    return memberReference.apply(other);
  }

  public static <V> boolean booleanOrWithArgument(@Nonnull JsonSchemaObject base,
                                                  @Nonnull JsonSchemaObject other,
                                                  @Nonnull java.util.function.BiFunction<JsonSchemaObject, V, Boolean> memberReference,
                                                  V argument) {
    boolean first = memberReference.apply(base, argument);
    if (first) return true;
    ProgressManager.checkCanceled();
    return memberReference.apply(other, argument);
  }

  public static <T> T baseIfConditionOrOther(@Nonnull JsonSchemaObject base,
                                             @Nonnull JsonSchemaObject other,
                                             @Nonnull Function<JsonSchemaObject, T> memberReference,
                                             @Nonnull java.util.function.Predicate<T> condition) {
    ProgressManager.checkCanceled();
    T baseResult = memberReference.apply(base);
    ProgressManager.checkCanceled();
    if (condition.test(baseResult)) return baseResult;
    ProgressManager.checkCanceled();
    return memberReference.apply(other);
  }

  public static <T, V> T baseIfConditionOrOtherWithArgument(@Nonnull JsonSchemaObject base,
                                                            @Nonnull JsonSchemaObject other,
                                                            @Nonnull java.util.function.BiFunction<JsonSchemaObject, V, T> memberReference,
                                                            V argument,
                                                            @Nonnull java.util.function.Predicate<T> condition) {
    ProgressManager.checkCanceled();
    T baseResult = memberReference.apply(base, argument);
    ProgressManager.checkCanceled();
    if (condition.test(baseResult)) return baseResult;
    ProgressManager.checkCanceled();
    return memberReference.apply(other, argument);
  }

  public static boolean isNotBlank(@Nullable String str) {
    return str != null && !str.isBlank();
  }

  public static boolean isNotNull(@Nullable Object obj) {
    return obj != null;
  }
}
