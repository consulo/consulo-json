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
package com.jetbrains.jsonSchema.impl.remote;

import com.jetbrains.jsonSchema.remote.SchemaUrl;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import org.jspecify.annotations.Nullable;

@ServiceAPI(ComponentScope.APPLICATION)
public interface SchemaContentCache {
    /**
     * Returns the cached schema of {@code url} from the memory. Does no IO and is safe under the read lock.
     */
    @Nullable CachedSchema read(SchemaUrl url);

    /**
     * Returns the cached state of {@code url} from the memory. Does no IO and is safe under the read lock.
     */
    default @Nullable CachedStatus readStatus(SchemaUrl url) {
        CachedSchema cached = read(url);
        if (cached instanceof CachedSchema.Content content) {
            return new CachedStatus.Available(content.isStale(), content.etag(), content.lastModified());
        }
        if (cached instanceof CachedSchema.Unavailable unavailable) {
            return new CachedStatus.Unavailable(unavailable.code(), unavailable.message());
        }
        return null;
    }

    /**
     * Returns the cached file of {@code url} from the memory. Does no IO and is safe under the read lock.
     */
    default @Nullable CachedFile lookupFile(SchemaUrl url) {
        return null;
    }

    /**
     * Reads the persistent entry of {@code url} into the memory. Does IO, so the caller must not hold read access.
     *
     * @return {@code true} when the memory entry of {@code url} changed.
     */
    default boolean load(SchemaUrl url) {
        return false;
    }

    /**
     * Stores {@code result} for {@code url}. Does IO, so the caller must not hold read access.
     */
    void write(SchemaUrl url, DownloadResult result);

    /**
     * Marks the cached content of {@code url} as fresh. Does IO, so the caller must not hold read access.
     */
    default void refreshTimestamp(SchemaUrl url) {
    }
}
