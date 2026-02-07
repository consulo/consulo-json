// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import com.intellij.json.pointer.JsonPointerPosition;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import consulo.application.progress.ProgressManager;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public final class JsonSchemaTreeNode {
  private boolean myAny;
  private boolean myNothing;
  private int myExcludingGroupNumber = -1;
  private @Nonnull SchemaResolveState myResolveState = SchemaResolveState.normal;

  private final @Nullable JsonSchemaObject mySchema;
  private final @Nonnull JsonPointerPosition myPosition;

  private final @Nullable JsonSchemaTreeNode myParent;
  private final @Nonnull Collection<JsonSchemaTreeNode> myChildren = new LinkedHashSet<>();

  public JsonSchemaTreeNode(@Nullable JsonSchemaTreeNode parent,
                            @Nullable JsonSchemaObject schema) {
    assert schema != null || parent != null;
    myParent = parent;
    mySchema = schema;
    final JsonPointerPosition steps = parent != null ? parent.getPosition().skip(1) : null;
    myPosition = steps == null ? new JsonPointerPosition() : steps;
  }

  public void anyChild() {
    final JsonSchemaTreeNode node = new JsonSchemaTreeNode(this, null);
    node.myAny = true;
    myChildren.add(node);
  }

  public void nothingChild() {
    final JsonSchemaTreeNode node = new JsonSchemaTreeNode(this, null);
    node.myNothing = true;
    myChildren.add(node);
  }

  public void createChildrenFromOperation(@Nonnull Operation operation) {
    ProgressManager.checkCanceled();
    if (!SchemaResolveState.normal.equals(operation.myState)) {
      final JsonSchemaTreeNode node = new JsonSchemaTreeNode(this, null);
      node.myResolveState = operation.myState;
      myChildren.add(node);
      return;
    }
    if (!operation.myAnyOfGroup.isEmpty()) {
      myChildren.addAll(convertToNodes(operation.myAnyOfGroup));
    }
    if (!operation.myOneOfGroup.isEmpty()) {
      for (int i = 0; i < operation.myOneOfGroup.size(); i++) {
        ProgressManager.checkCanceled();
        var group = operation.myOneOfGroup.get(i);
        final List<JsonSchemaTreeNode> children = convertToNodes(group);
        final int number = i;
        children.forEach(c -> c.myExcludingGroupNumber = number);
        myChildren.addAll(children);
      }
    }
  }

  private List<JsonSchemaTreeNode> convertToNodes(List<? extends JsonSchemaObject> children) {
    List<JsonSchemaTreeNode> nodes = new ArrayList<>(children.size());
    for (JsonSchemaObject child: children) {
      nodes.add(new JsonSchemaTreeNode(this, child));
    }
    return nodes;
  }

  public @Nonnull SchemaResolveState getResolveState() {
    return myResolveState;
  }

  public boolean isAny() {
    return myAny;
  }

  public boolean isNothing() {
    return myNothing;
  }


  public void setChild(final @Nonnull JsonSchemaObject schema) {
    myChildren.add(new JsonSchemaTreeNode(this, schema));
  }

  public @Nullable JsonSchemaObject getSchema() {
    return mySchema;
  }

  public @Nonnull JsonPointerPosition getPosition() {
    return myPosition;
  }

  public @Nullable JsonSchemaTreeNode getParent() {
    return myParent;
  }

  public @Nonnull Collection<JsonSchemaTreeNode> getChildren() {
    return myChildren;
  }

  public int getExcludingGroupNumber() {
    return myExcludingGroupNumber;
  }

  public void setPosition(@Nonnull JsonPointerPosition steps) {
    myPosition.updateFrom(steps);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JsonSchemaTreeNode node = (JsonSchemaTreeNode)o;

    if (myAny != node.myAny) return false;
    if (myNothing != node.myNothing) return false;
    if (myResolveState != node.myResolveState) return false;
    if (!Objects.equals(mySchema, node.mySchema)) return false;
    //noinspection RedundantIfStatement
    if (!myPosition.equals(node.myPosition)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = (myAny ? 1 : 0);
    result = 31 * result + (myNothing ? 1 : 0);
    result = 31 * result + myResolveState.hashCode();
    result = 31 * result + (mySchema != null ? mySchema.hashCode() : 0);
    result = 31 * result + myPosition.hashCode();
    return result;
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("NODE#" + hashCode() + "\n");
    sb.append(myPosition.toString());
    sb.append("\n");
    if (myExcludingGroupNumber >= 0) sb.append("in excluding group\n");
    if (myAny) sb.append("any");
    else if (myNothing) sb.append("nothing");
    else if (!SchemaResolveState.normal.equals(myResolveState)) sb.append(myResolveState.name());
    else {
      assert mySchema != null;
      sb.append("schema").append("\n");
      if (mySchema.getRef() != null) sb.append("$ref: ").append(mySchema.getRef()).append("\n");
      else if (JsonSchemaObjectReadingUtils.hasProperties(mySchema)) {
        sb.append("properties: ");
        sb.append(String.join(", ", List.copyOf(mySchema.getPropertyNames()).toList())).append("\n");
      }
      if (!myChildren.isEmpty()) {
        sb.append("OR children of NODE#").append(hashCode()).append(":\n----------------\n")
          .append(myChildren.stream().map(Object::toString).collect(Collectors.joining("\n")))
          .append("\n=================\n");
      }
    }
    return sb.toString();
  }
}
