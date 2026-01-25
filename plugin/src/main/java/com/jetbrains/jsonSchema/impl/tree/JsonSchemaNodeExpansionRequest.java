// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.tree;

import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import jakarta.annotation.Nullable;

/**
 * Represents a request to expand a JSON schema node.
 *
 * @param inspectedValueAdapter The JSON value adapter for the inspected instance node.
 * Might be useful for in-schema resolve that must work with the validated instance
 * to choose which branch to resolve to depending on what user had actually typed.
 * @param strictIfElseBranchChoice FALSE to consider all "if-else" branches of the schema no matter what data
 * the instance file has, TRUE to select only one actually valid against the instance file branch.
 * In cases like incomplete code, it is useful to consider all branches independently of the validated instance code,
 * e.g., to display all possible completions
 */
public class JsonSchemaNodeExpansionRequest {
  @Nullable
  private final JsonValueAdapter inspectedValueAdapter;
  private final boolean strictIfElseBranchChoice;

  public JsonSchemaNodeExpansionRequest(@Nullable JsonValueAdapter inspectedValueAdapter,
                                        boolean strictIfElseBranchChoice) {
    this.inspectedValueAdapter = inspectedValueAdapter;
    this.strictIfElseBranchChoice = strictIfElseBranchChoice;
  }

  @Nullable
  public JsonValueAdapter getInspectedValueAdapter() {
    return inspectedValueAdapter;
  }

  public boolean isStrictIfElseBranchChoice() {
    return strictIfElseBranchChoice;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    JsonSchemaNodeExpansionRequest that = (JsonSchemaNodeExpansionRequest) o;

    if (strictIfElseBranchChoice != that.strictIfElseBranchChoice) return false;
    return inspectedValueAdapter != null ? inspectedValueAdapter.equals(that.inspectedValueAdapter) : that.inspectedValueAdapter == null;
  }

  @Override
  public int hashCode() {
    int result = inspectedValueAdapter != null ? inspectedValueAdapter.hashCode() : 0;
    result = 31 * result + (strictIfElseBranchChoice ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return "JsonSchemaNodeExpansionRequest{" +
           "inspectedValueAdapter=" + inspectedValueAdapter +
           ", strictIfElseBranchChoice=" + strictIfElseBranchChoice +
           '}';
  }
}
