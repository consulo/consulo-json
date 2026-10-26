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
package com.jetbrains.jsonSchema;

import com.jetbrains.jsonSchema.internal.JsonSchemaCatalogEntry;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.disposer.Disposable;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.TestOnly;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

@ServiceAPI(ComponentScope.PROJECT)
public interface JsonSchemaCatalogManager {
    String DEFAULT_CATALOG = "http://schemastore.org/api/json/catalog.json";
    String DEFAULT_CATALOG_HTTPS = "https://schemastore.org/api/json/catalog.json";

    static JsonSchemaCatalogManager getInstance(Project project) {
        return project.getInstance(JsonSchemaCatalogManager.class);
    }

    void startUpdates(Disposable parentDisposable);

    void updateIfDownloaded(Collection<String> urls);

    @TestOnly
    void registerTestSchemaStoreFile(VirtualFile testSchemaStoreFile, Disposable testDisposable);

    @TestOnly
    @Nullable VirtualFile getSchemaFileForFile(VirtualFile file);

    @Nullable JsonSchemaCatalogEntry getSchemaCatalogEntryForFile(VirtualFile file);

    List<JsonSchemaCatalogEntry> getAllCatalogEntries();

    void registerCatalogUpdateCallback(Runnable callback);

    void unregisterCatalogUpdateCallback(Runnable callback);

    void triggerUpdateCatalog(Project project);
}
