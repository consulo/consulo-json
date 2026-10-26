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
package com.jetbrains.jsonSchema.extension;

import com.jetbrains.jsonSchema.extension.adapter.JsonObjectValueAdapter;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.language.psi.PsiFile;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Handles a special "shorthand" form of values in some configuration formats.
 * For example, you can have "feature: enabled" and "feature:\n enabled: true".
 * Currently, it's used only in nested completion, but can be reused in other features.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface JsonSchemaShorthandValueHandler {
    /**
     * Whether shorthand values can happen in this particular kind of files
     */
    boolean isApplicable(PsiFile file);

    /**
     * Gets a path within a file (nested property names), and a property literal value
     * Returns a key-value for a replacement
     */
    @Nullable
    KeyValue expandShorthandValue(List<String> path, String value);

    /**
     * Gets a path within a file (nested property names), and a property name and value in the full form
     * Returns a collapsed representation of that property
     */
    @Nullable
    default String collapseToShorthandValue(List<String> path, KeyValue data) {
        return null;
    }

    /**
     * Whether the object is collapsible. By default, it's collapsible if there is just one property.
     */
    default boolean isCollapsible(JsonObjectValueAdapter parentObject) {
        return parentObject.getPropertyList().size() == 1;
    }

    class KeyValue {
        private final String myKey;
        private final String myValue;

        public KeyValue(String key, String value) {
            myKey = key;
            myValue = value;
        }

        public String getKey() {
            return myKey;
        }

        public String getValue() {
            return myValue;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            KeyValue keyValue = (KeyValue) o;
            return Objects.equals(myKey, keyValue.myKey) && Objects.equals(myValue, keyValue.myValue);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myKey, myValue);
        }

        @Override
        public String toString() {
            return "KeyValue(key=" + myKey + ", value=" + myValue + ")";
        }
    }
}
