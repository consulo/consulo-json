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

import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import com.jetbrains.jsonSchema.extension.adapter.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class JsonJsonPropertyAdapter implements JsonPropertyAdapter {
    private final JsonProperty myProperty;

    public JsonJsonPropertyAdapter(JsonProperty property) {
        myProperty = property;
    }

    @Override
    public @Nullable String getName() {
        return myProperty.getName();
    }

    @Override
    public Collection<JsonValueAdapter> getValues() {
        return myProperty.getValue() == null ? List.of() : Collections.singletonList(createAdapterByType(myProperty.getValue()));
    }

    @Override
    public @Nullable JsonValueAdapter getNameValueAdapter() {
        return createAdapterByType(myProperty.getNameElement());
    }

    @Override
    public PsiElement getDelegate() {
        return myProperty;
    }

    @Override
    public @Nullable JsonObjectValueAdapter getParentObject() {
        return myProperty.getParent() instanceof JsonObject ? new JsonJsonObjectAdapter((JsonObject)myProperty.getParent()) : null;
    }

    public static JsonValueAdapter createAdapterByType(JsonValue value) {
        if (value instanceof JsonObject) {
            return new JsonJsonObjectAdapter((JsonObject)value);
        }
        if (value instanceof JsonArray) {
            return new JsonJsonArrayAdapter((JsonArray)value);
        }
        return new JsonJsonGenericValueAdapter(value);
    }
}
