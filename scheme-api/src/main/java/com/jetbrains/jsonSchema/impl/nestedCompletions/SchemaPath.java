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
package com.jetbrains.jsonSchema.impl.nestedCompletions;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a path through a tree structure. Often inversed using {@link #accessor()}
 */
public final class SchemaPath {
    private final String myName;
    private final @Nullable SchemaPath myPrevious;

    public SchemaPath(String name, @Nullable SchemaPath previous) {
        myName = name;
        myPrevious = previous;
    }

    public String getName() {
        return myName;
    }

    public @Nullable SchemaPath getPrevious() {
        return myPrevious;
    }

    public static SchemaPath div(@Nullable SchemaPath path, String name) {
        return new SchemaPath(name, path);
    }

    public List<String> accessor() {
        List<String> result = new ArrayList<>();
        SchemaPath current = this;
        while (current != null) {
            result.add(current.myName);
            current = current.myPrevious;
        }
        Collections.reverse(result);
        return result;
    }

    public String prefix() {
        return String.join(".", accessor());
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SchemaPath path = (SchemaPath) o;
        return myName.equals(path.myName) && Objects.equals(myPrevious, path.myPrevious);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myName, myPrevious);
    }

    @Override
    public String toString() {
        return "SchemaPath(name=" + myName + ", previous=" + myPrevious + ")";
    }
}
