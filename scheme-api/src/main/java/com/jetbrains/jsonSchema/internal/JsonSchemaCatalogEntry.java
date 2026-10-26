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
package com.jetbrains.jsonSchema.internal;

import org.jspecify.annotations.Nullable;

import java.util.Collection;

public final class JsonSchemaCatalogEntry {
    private final Collection<String> fileMasks;
    private final String url;
    private final @Nullable String name;
    private final @Nullable String description;

    public JsonSchemaCatalogEntry(Collection<String> fileMasks, String url, @Nullable String name, @Nullable String description) {
        this.fileMasks = fileMasks;
        this.url = url;
        this.name = name;
        this.description = description;
    }

    public Collection<String> getFileMasks() {
        return fileMasks;
    }

    public String getUrl() {
        return url;
    }

    public @Nullable String getName() {
        return name;
    }

    public @Nullable String getDescription() {
        return description;
    }
}
