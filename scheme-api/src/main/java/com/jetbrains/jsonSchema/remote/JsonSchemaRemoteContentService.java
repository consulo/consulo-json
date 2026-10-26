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

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.TestOnly;
import org.jspecify.annotations.Nullable;

import java.net.URISyntaxException;

@ServiceAPI(ComponentScope.PROJECT)
public interface JsonSchemaRemoteContentService extends RemoteSchemaContentProvider {
    static @Nullable SchemaUrl parseOrNull(String url) {
        try {
            return SchemaUrl.parse(url);
        }
        catch (IllegalArgumentException | URISyntaxException e) {
            return null;
        }
    }

    static boolean isValidUrl(String url) {
        return parseOrNull(url) != null;
    }

    static JsonSchemaRemoteContentService getInstance(Project project) {
        return project.getInstance(JsonSchemaRemoteContentService.class);
    }

    /**
     * Returns cached remote schema as a local JSON file, or schedules its download on cache miss.
     */
    @Nullable VirtualFile getCachedFile(SchemaUrl url);

    @Nullable VirtualFile getCachedFile(String url);

    String getSchemaIri(VirtualFile file);

    /**
     * Schedules a download of {@code url} unless its last download failed.
     * An automatic retry after each failure would restart the schema consumers in a loop, for example when the IDE is offline.
     * Use {@link #retryDownload} to try a failed URL again.
     */
    @Override
    boolean prefetch(SchemaUrl url);

    boolean prefetch(String url);

    /**
     * Clears the last failure of {@code url} and schedules its download again.
     * This is an explicit user request, so it also skips a cached error and revalidates fresh content.
     */
    void retryDownload(SchemaUrl url);

    void retryDownload(String url);

    @TestOnly
    boolean hasInFlight(SchemaUrl url);

    boolean hasInFlight(String url);

    boolean isUnavailable(SchemaUrl url);

    boolean isUnavailable(String url);

    @Nullable RemoteDownloadFailure lastFailure(SchemaUrl url);

    @Nullable RemoteDownloadFailure lastFailure(String url);

    /**
     * {@code lookupFile} only. Does not prefetch.
     */
    @Nullable VirtualFile peekCachedFile(SchemaUrl url);

    @Nullable VirtualFile peekCachedFile(String url);

    /**
     * Schedules a download of {@code url}, ignores its last failure, and waits for the result.
     * Returns the cached file right away when it is available.
     */
    @TestOnly
    @Nullable VirtualFile downloadAndWaitForTest(SchemaUrl url);
}
