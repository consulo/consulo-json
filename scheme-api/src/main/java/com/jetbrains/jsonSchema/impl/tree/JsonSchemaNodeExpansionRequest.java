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
package com.jetbrains.jsonSchema.impl.tree;

import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Represents a request to expand a JSON schema node.
 * <p>
 * {@code inspectedValueAdapter} The JSON value adapter for the inspected instance node.
 * Might be useful for in-schema resolve that must work with the validated instance
 * to choose which branch to resolve to depending on what user had actually typed.
 * <p>
 * {@code strictIfElseBranchChoice} FALSE to consider all "if-else" branches of the schema no matter what data
 * the instance file has, TRUE to select only one actually valid against the instance file branch.
 * In cases like incomplete code, it is useful to consider all branches independently of the validated instance code,
 * e.g., to display all possible completions
 */
public final class JsonSchemaNodeExpansionRequest {
    private final @Nullable JsonValueAdapter myInspectedValueAdapter;
    private final boolean myStrictIfElseBranchChoice;

    public JsonSchemaNodeExpansionRequest(@Nullable JsonValueAdapter inspectedValueAdapter, boolean strictIfElseBranchChoice) {
        myInspectedValueAdapter = inspectedValueAdapter;
        myStrictIfElseBranchChoice = strictIfElseBranchChoice;
    }

    public @Nullable JsonValueAdapter getInspectedValueAdapter() {
        return myInspectedValueAdapter;
    }

    public boolean getStrictIfElseBranchChoice() {
        return myStrictIfElseBranchChoice;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JsonSchemaNodeExpansionRequest that = (JsonSchemaNodeExpansionRequest) o;
        return myStrictIfElseBranchChoice == that.myStrictIfElseBranchChoice &&
            Objects.equals(myInspectedValueAdapter, that.myInspectedValueAdapter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myInspectedValueAdapter, myStrictIfElseBranchChoice);
    }

    @Override
    public String toString() {
        return "JsonSchemaNodeExpansionRequest(inspectedValueAdapter=" + myInspectedValueAdapter +
            ", strictIfElseBranchChoice=" + myStrictIfElseBranchChoice + ")";
    }
}
