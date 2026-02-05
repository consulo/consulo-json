// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.IfThenElse;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.Function;

/**
 * This performs a simple search for properties that must **not** be present.
 * It uses either:
 * - {@code { "not": { "required": ["x"] } }}
 * <p>
 * most likely to be found in a condition as follows:
 * <p>
 * {@code { "if": { <y> }, "else": { "not": { "required": "x" } } }}
 * <p>
 * (Read this as: If not {@code <y>}, must not have property "x")
 * - Or {@code { "if": { "required": { "x" } }, "then": { <y> } }}
 * <p>
 * (Read this as: If we have property "x", we must {@code <y>})
 *
 * @param existingProperties to understand why this knowledge is required, see {@code com.jetbrains.jsonSchema.impl.JsonBySchemaNotRequiredCompletionTest.test not required x, y and z, then it will not complete the last of the three fields}
 */
public final class NotRequiredProperties {

  @Nonnull
  public static Set<String> findPropertiesThatMustNotBePresent(@Nonnull JsonSchemaObject schema,
                                                                @Nonnull PsiElement position,
                                                                @Nonnull Project project,
                                                                @Nonnull Set<String> existingProperties) {
    Set<String> result = mapEffectiveSchemasNotNull(schema, position, project, schemaObj -> {
      JsonSchemaObject not = schemaObj.getNot();
      if (not != null && not.getRequired() != null) {
        Set<String> required = new HashSet<>(not.getRequired());
        required.removeAll(existingProperties);
        if (required.size() == 1) {
          return required.iterator().next();
        }
      }
      return null;
    });

    result = plusLikelyEmpty(result, flatMapIfThenElseBranches(schema, position, (ifThenElse, parent) -> {
      JsonSchemaObject then = ifThenElse.getThen();
      if (then != null) {
        Set<String> ifRequired = mapEffectiveSchemasNotNull(ifThenElse.getIf(), position, project, ifSchema -> {
          if (ifSchema.getRequired() != null) {
            Set<String> required = new HashSet<>(ifSchema.getRequired());
            required.removeAll(existingProperties);
            if (required.size() == 1) {
              return required.iterator().next();
            }
          }
          return null;
        });
        if (!ifRequired.isEmpty() && adheresTo(parent, then, project)) {
          return ifRequired;
        }
      }
      return null;
    }));

    return result;
  }

  /**
   * Traverses the graph of effective schema's and returns a set of all values where {@code selector} returned a non-null value.
   * Effective schema's includes schema's inside {@code "allOf"} and {@code "if" "then" "else"} blocks.
   */
  @Nonnull
  private static <T> Set<T> mapEffectiveSchemasNotNull(@Nonnull JsonSchemaObject schema,
                                                        @Nonnull PsiElement position,
                                                        @Nonnull Project project,
                                                        @Nonnull Function<JsonSchemaObject, T> selector) {
    T value = selector.apply(schema);
    Set<T> result = value != null ? Collections.singleton(value) : Collections.emptySet();

    List<JsonSchemaObject> allOf = schema.getAllOf();
    if (allOf != null) {
      result = plusLikelyEmpty(result, flatMapLikelyEmpty(allOf, obj ->
        mapEffectiveSchemasNotNull(obj, position, project, selector)
      ));
    }

    result = plusLikelyEmpty(result, flatMapIfThenElseBranches(schema, position, (ifThenElse, parent) -> {
      JsonSchemaObject effective = effectiveBranchOrNull(ifThenElse, project, parent);
      return effective != null ? mapEffectiveSchemasNotNull(effective, position, project, selector) : null;
    }));

    return result;
  }

  @Nonnull
  private static <T> Set<T> flatMapIfThenElseBranches(@Nonnull JsonSchemaObject schema,
                                                       @Nonnull PsiElement position,
                                                       @Nonnull java.util.function.BiFunction<IfThenElse, JsonObjectValueAdapter, Set<T>> mapper) {
    List<IfThenElse> ifThenElseList = schema.getIfThenElse();
    if (ifThenElseList == null || ifThenElseList.isEmpty()) {
      return Collections.emptySet();
    }

    JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(position, schema);
    if (walker == null) return Collections.emptySet();

    JsonPropertyAdapter parentPropertyAdapter = walker.getParentPropertyAdapter(position);
    if (parentPropertyAdapter == null) return Collections.emptySet();

    JsonObjectValueAdapter parent = parentPropertyAdapter.getParentObject();
    if (parent == null) return Collections.emptySet();

    return flatMapLikelyEmpty(ifThenElseList, ifThenElse -> mapper.apply(ifThenElse, parent));
  }

  @Nullable
  private static JsonSchemaObject effectiveBranchOrNull(@Nonnull IfThenElse ifThenElse,
                                                         @Nonnull Project project,
                                                         @Nonnull JsonObjectValueAdapter parent) {
    return adheresTo(parent, ifThenElse.getIf(), project) ? ifThenElse.getThen() : ifThenElse.getElse();
  }

  private static boolean adheresTo(@Nonnull JsonObjectValueAdapter parentObject,
                                    @Nonnull JsonSchemaObject schema,
                                    @Nonnull Project project) {
    JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker(project, JsonComplianceCheckerOptions.RELAX_ENUM_CHECK);
    checker.checkByScheme(parentObject, schema);
    return checker.isCorrect();
  }

  // These allocation optimizations are in place because the checks in this file are often performed, but don't frequently yield results
  @Nonnull
  private static <T> Set<T> plusLikelyEmpty(@Nonnull Set<T> thisSet, @Nullable Set<T> elements) {
    if (thisSet.isEmpty()) {
      return elements != null ? elements : Collections.emptySet();
    }
    if (elements == null || elements.isEmpty()) {
      return thisSet;
    }
    Set<T> result = new HashSet<>(thisSet);
    result.addAll(elements);
    return result;
  }

  @Nonnull
  private static <T, R> Set<R> flatMapLikelyEmpty(@Nonnull Iterable<T> iterable,
                                                   @Nonnull Function<T, Collection<R>> transform) {
    Set<R> destination = null;
    for (T element : iterable) {
      Collection<R> list = transform.apply(element);
      if (list == null || list.isEmpty()) continue;

      if (destination == null) {
        destination = new HashSet<>();
      }
      destination.addAll(list);
    }
    return destination != null ? destination : Collections.emptySet();
  }
}
