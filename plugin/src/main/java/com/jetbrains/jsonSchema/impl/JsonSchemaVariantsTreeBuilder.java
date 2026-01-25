// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.impl.pointer.JsonPointerPosition;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaInheritanceUtil;
import com.jetbrains.jsonSchema.impl.tree.JsonSchemaNodeExpansionRequest;
import com.jetbrains.jsonSchema.impl.tree.Operation;
import com.jetbrains.jsonSchema.impl.tree.ProcessDefinitionsOperation;
import consulo.application.util.registry.Registry;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.util.lang.ThreeState;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static com.jetbrains.jsonSchema.JsonPointerUtil.isSelfReference;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.*;

public final class JsonSchemaVariantsTreeBuilder {

  public static JsonSchemaTreeNode buildTree(@Nonnull Project project,
                                             @Nullable JsonSchemaNodeExpansionRequest expansionRequest,
                                             final @Nonnull JsonSchemaObject schema,
                                             final @Nonnull JsonPointerPosition position,
                                             final boolean skipLastExpand) {
    final JsonSchemaTreeNode root = new JsonSchemaTreeNode(null, schema);
    JsonSchemaService service = JsonSchemaService.Impl.get(project);
    expandChildSchema(root, expansionRequest, schema, service);
    // set root's position since this children are just variants of root
    for (JsonSchemaTreeNode treeNode : root.getChildren()) {
      treeNode.setPosition(position);
    }

    final ArrayDeque<JsonSchemaTreeNode> queue = new ArrayDeque<>(root.getChildren());

    while (!queue.isEmpty()) {
      final JsonSchemaTreeNode node = queue.removeFirst();
      if (node.isAny() || node.isNothing() || node.getPosition().isEmpty() || node.getSchema() == null) continue;
      final JsonPointerPosition step = node.getPosition();
      if (!typeMatches(step.isObject(0), node.getSchema())) {
        node.nothingChild();
        continue;
      }
      final Pair<ThreeState, JsonSchemaObject> pair = doSingleStep(step, node.getSchema());
      if (ThreeState.NO.equals(pair.getFirst())) {
        node.nothingChild();
      }
      else if (ThreeState.YES.equals(pair.getFirst())) {
        node.anyChild();
      }
      else {
        // process step results
        assert pair.getSecond() != null;
        if (node.getPosition().size() > 1 || !skipLastExpand) {
          expandChildSchema(node, expansionRequest, pair.getSecond(), service);
        }
        else {
          node.setChild(pair.getSecond());
        }
      }

      queue.addAll(node.getChildren());
    }

    return root;
  }

  private static boolean typeMatches(final boolean isObject, final @Nonnull JsonSchemaObject schema) {
    final JsonSchemaType requiredType = isObject ? JsonSchemaType._object : JsonSchemaType._array;
    if (schema.getType() != null) {
      return requiredType.equals(schema.getType());
    }
    if (schema.getTypeVariants() != null) {
      for (JsonSchemaType schemaType : schema.getTypeVariants()) {
        if (requiredType.equals(schemaType)) return true;
      }
      return false;
    }
    return true;
  }

  private static void expandChildSchema(@Nonnull JsonSchemaTreeNode node,
                                        @Nullable JsonSchemaNodeExpansionRequest expansionRequest,
                                        @Nonnull JsonSchemaObject childSchema,
                                        @Nonnull JsonSchemaService service) {
    if (interestingSchema(childSchema)) {
      node.createChildrenFromOperation(getOperation(service, childSchema, expansionRequest));
    }
    else {
      node.setChild(childSchema);
    }
  }

  private static @Nonnull Operation getOperation(@Nonnull JsonSchemaService service,
                                                 @Nonnull JsonSchemaObject param,
                                                 @Nullable JsonSchemaNodeExpansionRequest expansionRequest) {
    final Operation expand = new ProcessDefinitionsOperation(param, service, expansionRequest);
    expand.doMap(new HashSet<>());
    expand.doReduce();
    return expand;
  }

  public static @Nonnull Pair<ThreeState, JsonSchemaObject> doSingleStep(@Nonnull JsonPointerPosition step,
                                                                         @Nonnull JsonSchemaObject parent) {
    final String name = step.getFirstName();
    if (name != null) {
      return propertyStep(name, parent);
    }
    else {
      final int index = step.getFirstIndex();
      assert index >= 0;
      return arrayOrNumericPropertyElementStep(index, parent);
    }
  }

  // even if there are no definitions to expand, this object may work as an intermediate node in a tree,
  // connecting oneOf and allOf expansion, for example
  public static List<JsonSchemaObject> andGroups(@Nonnull List<? extends JsonSchemaObject> g1,
                                                 @Nonnull List<? extends JsonSchemaObject> g2) {
    List<JsonSchemaObject> result = new ArrayList<>(g1.size() * g2.size());
    for (JsonSchemaObject s : g1) {
      result.addAll(andGroup(s, g2));
    }
    return result;
  }

  // here is important, which pointer gets the result: lets make them all different, otherwise two schemas of branches of oneOf would be equal
  public static List<JsonSchemaObject> andGroup(@Nonnull JsonSchemaObject object, @Nonnull List<? extends JsonSchemaObject> group) {
    List<JsonSchemaObject> list = new ArrayList<>(group.size());
    for (JsonSchemaObject s : group) {
      var schemaObject = getJsonSchemaObjectMerger().mergeObjects(object, s, s);
      if (schemaObject.isValidByExclusion()) {
        list.add(schemaObject);
      }
    }
    return list;
  }


  private static boolean interestingSchema(@Nonnull JsonSchemaObject schema) {
    boolean hasAggregators;
    if (Registry.is("json.schema.object.v2")) {
      hasAggregators =
        schema.hasChildNode(ANY_OF) || schema.hasChildNode(ONE_OF) || schema.hasChildNode(ALL_OF) || schema.hasChildNode(IF);
    }
    else {
      hasAggregators = schema.getAnyOf() != null || schema.getOneOf() != null || schema.getAllOf() != null;
    }
    return hasAggregators || schema.getRef() != null || schema.getIfThenElse() != null;
  }


  private static @Nonnull Pair<ThreeState, JsonSchemaObject> propertyStep(@Nonnull String name,
                                                                          @Nonnull JsonSchemaObject parent) {
    final JsonSchemaObject child = parent.getPropertyByName(name);
    if (child != null) {
      return Pair.create(ThreeState.UNSURE, JsonSchemaInheritanceUtil.inheritBaseSchemaIfNeeded(parent, child));
    }
    final JsonSchemaObject schema = parent.getMatchingPatternPropertySchema(name);
    if (schema != null) {
      return Pair.create(ThreeState.UNSURE, JsonSchemaInheritanceUtil.inheritBaseSchemaIfNeeded(parent, schema));
    }
    if (parent.getAdditionalPropertiesSchema() != null) {
      return Pair.create(ThreeState.UNSURE, JsonSchemaInheritanceUtil.inheritBaseSchemaIfNeeded(parent, parent.getAdditionalPropertiesSchema()));
    }
    if (!parent.getAdditionalPropertiesAllowed()) {
      return Pair.create(ThreeState.NO, null);
    }

    JsonSchemaObject unevaluatedPropertiesSchema = parent.getUnevaluatedPropertiesSchema();
    if (unevaluatedPropertiesSchema != null) {
      if (Boolean.TRUE.equals(unevaluatedPropertiesSchema.getConstantSchema())) {
        return Pair.create(ThreeState.YES, JsonSchemaInheritanceUtil.inheritBaseSchemaIfNeeded(parent, unevaluatedPropertiesSchema));
      }
      else {
        return Pair.create(ThreeState.UNSURE, JsonSchemaInheritanceUtil.inheritBaseSchemaIfNeeded(parent, unevaluatedPropertiesSchema));
      }
    }
    // by default, additional properties are allowed
    return Pair.create(ThreeState.YES, null);
  }

  private static @Nonnull Pair<ThreeState, JsonSchemaObject> arrayOrNumericPropertyElementStep(int idx, @Nonnull JsonSchemaObject parent) {
    if (parent.getItemsSchema() != null) {
      return Pair.create(ThreeState.UNSURE, JsonSchemaInheritanceUtil.inheritBaseSchemaIfNeeded(parent, parent.getItemsSchema()));
    }
    if (parent.getItemsSchemaList() != null) {
      final var list = parent.getItemsSchemaList();
      if (idx >= 0 && idx < list.size()) {
        return Pair.create(ThreeState.UNSURE, JsonSchemaInheritanceUtil.inheritBaseSchemaIfNeeded(parent, list.get(idx)));
      }
    }
    final String keyAsString = String.valueOf(idx);
    var propWithNameOrNull = parent.getPropertyByName(keyAsString);
    if (propWithNameOrNull != null) {
      return Pair.create(ThreeState.UNSURE, JsonSchemaInheritanceUtil.inheritBaseSchemaIfNeeded(parent, propWithNameOrNull));
    }
    final JsonSchemaObject matchingPatternPropertySchema = parent.getMatchingPatternPropertySchema(keyAsString);
    if (matchingPatternPropertySchema != null) {
      return Pair.create(ThreeState.UNSURE, JsonSchemaInheritanceUtil.inheritBaseSchemaIfNeeded(parent, matchingPatternPropertySchema));
    }
    if (parent.getAdditionalItemsSchema() != null) {
      return Pair.create(ThreeState.UNSURE, JsonSchemaInheritanceUtil.inheritBaseSchemaIfNeeded(parent, parent.getAdditionalItemsSchema()));
    }
    if (Boolean.FALSE.equals(parent.getAdditionalItemsAllowed())) {
      return Pair.create(ThreeState.NO, null);
    }

    JsonSchemaObject unevaluatedItemsSchema = parent.getUnevaluatedItemsSchema();
    if (unevaluatedItemsSchema != null) {
      if (Boolean.TRUE.equals(unevaluatedItemsSchema.getConstantSchema())) {
        return Pair.create(ThreeState.YES, JsonSchemaInheritanceUtil.inheritBaseSchemaIfNeeded(parent, unevaluatedItemsSchema));
      }
      else {
        return Pair.create(ThreeState.UNSURE, JsonSchemaInheritanceUtil.inheritBaseSchemaIfNeeded(parent, unevaluatedItemsSchema));
      }
    }

    return Pair.create(ThreeState.YES, null);
  }

  public static final class SchemaUrlSplitter {
    private final @Nullable String mySchemaId;
    private final @Nonnull String myRelativePath;

    public SchemaUrlSplitter(final @Nonnull String ref) {
      if (isSelfReference(ref)) {
        mySchemaId = null;
        myRelativePath = "";
        return;
      }
      if (!ref.startsWith("#/")) {
        int idx = ref.indexOf("#/");
        if (idx == -1) {
          mySchemaId = ref.endsWith("#") ? ref.substring(0, ref.length() - 1) : ref;
          myRelativePath = "";
        }
        else {
          mySchemaId = ref.substring(0, idx);
          myRelativePath = ref.substring(idx);
        }
      }
      else {
        mySchemaId = null;
        myRelativePath = ref;
      }
    }

    public boolean isAbsolute() {
      return mySchemaId != null;
    }

    public @Nullable String getSchemaId() {
      return mySchemaId;
    }

    public @Nonnull String getRelativePath() {
      return myRelativePath;
    }
  }
}
