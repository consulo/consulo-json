// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema.impl;

import com.jetbrains.jsonSchema.IfThenElse;
import com.jetbrains.jsonSchema.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.extension.adapter.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.internal.JsonSchemaAnnotatorChecker;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public final class NotRequiredProperties {
    private NotRequiredProperties() {
    }

    /**
     * This performs a simple search for properties that must <b>not</b> be present.
     * It uses either:
     * <ul>
     *   <li>{@code { "not": { "required": ["x"] } }}
     *   <p>
     *   most likely to be found in a condition as follows:
     *   <p>
     *   {@code { "if": { <y> }, "else": { "not": { "required": "x" } } }}
     *   <p>
     *   (Read this as: If not {@code <y>}, must not have property "x")</li>
     *   <li>Or {@code { "if": { "required": { "x" } }, "then": { <y> } }}
     *   <p>
     *   (Read this as: If we have property "x", we must {@code <y>})</li>
     * </ul>
     *
     * @param existingProperties to understand why this knowledge is required, see
     *                           {@code com.jetbrains.jsonSchema.impl.JsonBySchemaNotRequiredCompletionTest.test not required x, y and z, then it will not complete the last of the three fields}
     */
    public static Set<String> findPropertiesThatMustNotBePresent(JsonSchemaObject schema,
                                                                 PsiElement position,
                                                                 Project project,
                                                                 Set<String> existingProperties) {
        Set<String> notRequired = mapEffectiveSchemasNotNull(schema, position, project, it -> {
            JsonSchemaObject not = it.getNot();
            return not == null ? null : singleOrNullMinus(not.getRequired(), existingProperties);
        });
        return plusLikelyEmpty(notRequired, flatMapIfThenElseBranches(schema, position, (ifThenElse, parent) -> {
            JsonSchemaObject then = ifThenElse.getThen();
            if (then == null) {
                return null;
            }
            Set<String> result = mapEffectiveSchemasNotNull(
                ifThenElse.getIf(),
                position,
                project,
                it -> singleOrNullMinus(it.getRequired(), existingProperties)
            );
            return !result.isEmpty() && adheresTo(parent, then, project) ? result : null;
        }));
    }

    /**
     * Traverses the graph of effective schema's and returns a set of all values where {@code selector} returned a non-null value.
     * Effective schema's includes schema's inside {@code "allOf"} and {@code "if" "then" "else"} blocks.
     */
    private static <T> Set<T> mapEffectiveSchemasNotNull(JsonSchemaObject schema,
                                                         PsiElement position,
                                                         Project project,
                                                         Function<JsonSchemaObject, @Nullable T> selector) {
        T selected = selector.apply(schema);
        Set<T> result = selected == null ? Set.of() : Set.of(selected);

        List<? extends JsonSchemaObject> allOf = schema.getAllOf();
        result = plusLikelyEmpty(
            result,
            allOf == null ? null : flatMapLikelyEmpty(allOf, it -> mapEffectiveSchemasNotNull(it, position, project, selector))
        );
        return plusLikelyEmpty(result, flatMapIfThenElseBranches(schema, position, (ifThenElse, parent) -> {
            JsonSchemaObject branch = effectiveBranchOrNull(ifThenElse, project, parent);
            return branch == null ? null : mapEffectiveSchemasNotNull(branch, position, project, selector);
        }));
    }

    private static <T> Set<T> flatMapIfThenElseBranches(JsonSchemaObject schema,
                                                        PsiElement position,
                                                        BiFunction<IfThenElse, JsonObjectValueAdapter, @Nullable Set<T>> mapper) {
        List<IfThenElse> ifThenElseList = schema.getIfThenElse();
        if (ifThenElseList == null || ifThenElseList.isEmpty()) {
            return Set.of();
        }
        JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(position, schema);
        if (walker == null) {
            return Set.of();
        }
        JsonPropertyAdapter propertyAdapter = walker.getParentPropertyAdapter(position);
        if (propertyAdapter == null) {
            return Set.of();
        }
        JsonObjectValueAdapter parent = propertyAdapter.getParentObject();
        if (parent == null) {
            return Set.of();
        }

        return flatMapLikelyEmpty(ifThenElseList, it -> mapper.apply(it, parent));
    }

    public static @Nullable JsonSchemaObject effectiveBranchOrNull(IfThenElse ifThenElse, Project project, JsonObjectValueAdapter parent) {
        return adheresTo(parent, ifThenElse.getIf(), project) ? ifThenElse.getThen() : ifThenElse.getElse();
    }

    private static boolean adheresTo(JsonObjectValueAdapter adapter, JsonSchemaObject schema, Project project) {
        JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker(project, JsonComplianceCheckerOptions.RELAX_ENUM_CHECK);
        checker.checkByScheme(adapter, schema);
        return checker.isCorrect();
    }

    private static @Nullable String singleOrNullMinus(@Nullable Set<String> required, Set<String> existingProperties) {
        if (required == null) {
            return null;
        }
        String single = null;
        for (String name : required) {
            if (existingProperties.contains(name)) {
                continue;
            }
            if (single != null) {
                return null;
            }
            single = name;
        }
        return single;
    }

    // These allocation optimizations are in place because the checks in this file are often performed, but don't frequently yield results */
    private static <T> Set<T> plusLikelyEmpty(Set<T> set, @Nullable Set<T> elements) {
        if (set.isEmpty()) {
            return elements == null ? Set.of() : elements;
        }
        if (elements == null || elements.isEmpty()) {
            return set;
        }
        Set<T> result = new HashSet<>(set);
        result.addAll(elements);
        return result;
    }

    private static <T, R> Set<R> flatMapLikelyEmpty(Iterable<? extends T> iterable, Function<T, @Nullable Collection<R>> transform) {
        Set<R> destination = null;
        for (T element : iterable) {
            Collection<R> list = transform.apply(element);
            if (list == null || list.isEmpty()) {
                continue;
            }

            if (destination == null) {
                destination = new HashSet<>();
            }
            destination.addAll(list);
        }
        return destination == null ? Set.of() : destination;
    }
}
