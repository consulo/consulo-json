// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.impl.light.nodes;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectMerger;
import com.jetbrains.jsonSchema.internal.MergedJsonSchemaObject;
import consulo.application.progress.ProgressManager;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public final class LightweightJsonSchemaObjectMerger implements JsonSchemaObjectMerger {
    public static final LightweightJsonSchemaObjectMerger INSTANCE = new LightweightJsonSchemaObjectMerger();

    private LightweightJsonSchemaObjectMerger() {
    }

    @Override
    public JsonSchemaObject mergeObjects(JsonSchemaObject base, JsonSchemaObject other, JsonSchemaObject pointTo) {
        ProgressManager.checkCanceled();
        if (base == other) {
            return base;
        }
        return new MergedJsonSchemaObjectView(base, other, pointTo);
    }

    @SuppressWarnings("unchecked")
    public static <T> @Nullable List<T> mergeLists(MergedJsonSchemaObject merged,
                                                   Function<JsonSchemaObject, ? extends @Nullable List<? extends T>> memberReference) {
        List<? extends T> first = memberReference.apply(merged.getBase());
        List<? extends T> second = memberReference.apply(merged.getOther());

        if (first == null || first.isEmpty()) {
            return (List<T>) second;
        }
        if (second == null || second.isEmpty()) {
            return (List<T>) first;
        }
        ProgressManager.checkCanceled();
        return ContainerUtil.concat(first, second);
    }

    @SuppressWarnings("unchecked")
    public static <K, V> @Nullable Map<K, V> mergeMaps(MergedJsonSchemaObject merged,
                                                       Function<JsonSchemaObject, ? extends @Nullable Map<K, ? extends V>> memberReference) {
        Map<K, ? extends V> first = memberReference.apply(merged.getBase());
        Map<K, ? extends V> second = memberReference.apply(merged.getOther());

        if (first == null || first.isEmpty()) {
            return (Map<K, V>) second;
        }
        if (second == null || second.isEmpty()) {
            return (Map<K, V>) first;
        }
        Map<K, V> mergedMap = new LinkedHashMap<>(first);
        ProgressManager.checkCanceled();
        mergedMap.putAll(second);
        return Collections.unmodifiableMap(mergedMap);
    }

    public static <T> @Nullable Set<T> mergeSets(@Nullable Set<T> first, @Nullable Set<T> second) {
        if (first == null || first.isEmpty()) {
            return second;
        }
        if (second == null || second.isEmpty()) {
            return first;
        }
        Set<T> merged = new LinkedHashSet<>(first);
        ProgressManager.checkCanceled();
        merged.addAll(second);
        return Collections.unmodifiableSet(merged);
    }

    public static boolean booleanOr(JsonSchemaObject base, JsonSchemaObject other, Predicate<JsonSchemaObject> memberReference) {
        boolean first = memberReference.test(base);
        if (first) {
            return true;
        }
        ProgressManager.checkCanceled();
        return memberReference.test(other);
    }

    public static boolean booleanAnd(JsonSchemaObject base, JsonSchemaObject other, Predicate<JsonSchemaObject> memberReference) {
        boolean first = memberReference.test(base);
        if (!first) {
            return false;
        }
        ProgressManager.checkCanceled();
        return memberReference.test(other);
    }

    public static @Nullable Boolean booleanAndNullable(JsonSchemaObject base,
                                                       JsonSchemaObject other,
                                                       Function<JsonSchemaObject, @Nullable Boolean> memberReference) {
        Boolean first = memberReference.apply(base);
        if (Boolean.FALSE.equals(first)) {
            return false;
        }
        ProgressManager.checkCanceled();
        return memberReference.apply(other);
    }

    public static <V extends @Nullable Object> boolean booleanOrWithArgument(JsonSchemaObject base,
                                                    JsonSchemaObject other,
                                                    BiFunction<JsonSchemaObject, V, Boolean> memberReference,
                                                    V argument) {
        boolean first = memberReference.apply(base, argument);
        if (first) {
            return true;
        }
        ProgressManager.checkCanceled();
        return memberReference.apply(other, argument);
    }

    public static <T extends @Nullable Object> T baseIfConditionOrOther(JsonSchemaObject base,
                                               JsonSchemaObject other,
                                               Function<JsonSchemaObject, T> memberReference,
                                               Predicate<T> condition) {
        ProgressManager.checkCanceled();
        T baseResult = memberReference.apply(base);
        ProgressManager.checkCanceled();
        if (condition.test(baseResult)) {
            return baseResult;
        }
        ProgressManager.checkCanceled();
        return memberReference.apply(other);
    }

    public static <T extends @Nullable Object, V extends @Nullable Object> T baseIfConditionOrOtherWithArgument(JsonSchemaObject base,
                                                              JsonSchemaObject other,
                                                              BiFunction<JsonSchemaObject, V, T> memberReference,
                                                              V argument,
                                                              Predicate<T> condition) {
        ProgressManager.checkCanceled();
        T baseResult = memberReference.apply(base, argument);
        ProgressManager.checkCanceled();
        if (condition.test(baseResult)) {
            return baseResult;
        }
        ProgressManager.checkCanceled();
        return memberReference.apply(other, argument);
    }

    public static boolean isNotBlank(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    public static boolean isNotNull(@Nullable Object value) {
        return value != null;
    }
}
