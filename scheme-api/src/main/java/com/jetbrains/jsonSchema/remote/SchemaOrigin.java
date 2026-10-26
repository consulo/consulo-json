// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema.remote;

import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

public final class SchemaOrigin {
    /**
     * The URL that served the content of a cached schema file. It is the final URL after redirects.
     */
    public static final Key<String> URL_KEY = Key.create("json.schema.origin.url");

    /**
     * The normalized {@link SchemaUrl} that the IDE requested for a cached schema file.
     */
    public static final Key<String> REQUEST_URL_KEY = Key.create("json.schema.origin.request.url");

    private SchemaOrigin() {
    }

    /**
     * Returns {@code true} when {@code file} is the cached content of the remote schema {@code url}.
     */
    public static boolean isCachedContentOf(VirtualFile file, @Nullable String url) {
        if (url == null) {
            return false;
        }
        String requestUrl = file.getUserData(REQUEST_URL_KEY);
        if (requestUrl == null) {
            return false;
        }
        SchemaUrl parsed = JsonSchemaRemoteContentService.parseOrNull(url);
        return parsed != null && requestUrl.equals(parsed.getValue());
    }
}
