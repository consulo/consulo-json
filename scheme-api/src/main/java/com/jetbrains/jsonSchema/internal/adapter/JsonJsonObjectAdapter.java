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
package com.jetbrains.jsonSchema.internal.adapter;

import com.intellij.json.psi.JsonObject;
import com.jetbrains.jsonSchema.extension.adapter.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonPropertyAdapter;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public final class JsonJsonObjectAdapter implements JsonObjectValueAdapter {
    private final JsonObject myValue;

    public JsonJsonObjectAdapter(JsonObject value) {myValue = value;}

    @Override
    public boolean isObject() {
        return true;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public boolean isStringLiteral() {
        return false;
    }

    @Override
    public boolean isNumberLiteral() {
        return false;
    }

    @Override
    public boolean isBooleanLiteral() {
        return false;
    }

    @Override
    public PsiElement getDelegate() {
        return myValue;
    }

    @Override
    public @Nullable JsonObjectValueAdapter getAsObject() {
        return this;
    }

    @Override
    public @Nullable JsonArrayValueAdapter getAsArray() {
        return null;
    }

    @Override
    public List<JsonPropertyAdapter> getPropertyList() {
        return myValue.getPropertyList().stream().filter(p -> p != null)
          .map(p -> new JsonJsonPropertyAdapter(p)).collect(Collectors.toList());
    }
}
