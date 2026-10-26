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
package com.jetbrains.jsonSchema.impl.remote;

import com.jetbrains.jsonSchema.JsonSchemaCatalogManager;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.internal.JsonCachedValues;
import com.jetbrains.jsonSchema.internal.JsonFileResolver;
import com.jetbrains.jsonSchema.internal.JsonSchemaCatalogEntry;
import com.jetbrains.jsonSchema.internal.JsonSchemaCatalogProjectConfiguration;
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogExclusion;
import com.jetbrains.jsonSchema.remote.JsonSchemaRemoteContentService;
import com.jetbrains.jsonSchema.remote.RemoteSchemaDownloadListener;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.Maps;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.TestOnly;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

@Singleton
@ServiceImpl
public final class JsonSchemaCatalogManagerImpl implements JsonSchemaCatalogManager {
    private static final Set<String> SCHEMA_URL_PREFIXES_WITH_TOO_MANY_VARIANTS = Set.of(
        // To match changing schema URLs for azure-pipelines:
        // - https://raw.githubusercontent.com/microsoft/azure-pipelines-vscode/master/service-schema.json
        // - https://raw.githubusercontent.com/microsoft/azure-pipelines-vscode/v1.174.2/service-schema.json
        "https://raw.githubusercontent.com/microsoft/azure-pipelines-vscode/"
    );

    private final Project myProject;
    private volatile @Nullable VirtualFile myCatalog = null;
    private final ConcurrentMap<VirtualFile, String> myResolvedMappings = ContainerUtil.createConcurrentSoftMap();
    private static final String EMPTY = "$_$_WS_EMPTY_$_$";
    private @Nullable VirtualFile myTestSchemaStoreFile;

    private final Map<Runnable, Disposable> myCatalogUpdateListeners = Maps.newConcurrentWeakHashMap();

    @Inject
    public JsonSchemaCatalogManagerImpl(Project project) {
        myProject = project;
    }

    @Override
    public void startUpdates(Disposable parentDisposable) {
        JsonSchemaCatalogProjectConfiguration.getInstance(myProject).addChangeHandler(() -> {
            update();
            JsonSchemaService.get(myProject).reset();
        }, parentDisposable);
        update();
    }

    @Override
    public void updateIfDownloaded(Collection<String> urls) {
        for (String url : urls) {
            if (DEFAULT_CATALOG_HTTPS.equals(url)) {
                update();
                return;
            }
        }
    }

    private void update() {
        myResolvedMappings.clear();
        Application application = Application.get();
        if (application.isUnitTestMode()) {
            myCatalog = myTestSchemaStoreFile;
            return;
        }
        // ignore schema catalog when remote activity is disabled (when we're in tests, or it is off in settings)
        // non-blocking: returns the cached catalog file if present, otherwise schedules a download and returns null;
        // the batched download consumer in JsonSchemaVfsListener calls updateIfDownloaded once per batch.
        myCatalog = !JsonFileResolver.isRemoteEnabled(myProject) ? null :
            JsonSchemaRemoteContentService.getInstance(myProject).getCachedFile(DEFAULT_CATALOG_HTTPS);
    }

    @TestOnly
    @Override
    public void registerTestSchemaStoreFile(VirtualFile testSchemaStoreFile, Disposable testDisposable) {
        myTestSchemaStoreFile = testSchemaStoreFile;
        Disposer.register(testDisposable, () -> {
            myTestSchemaStoreFile = null;
            update();
        });
        update();
    }

    @TestOnly
    @Override
    public @Nullable VirtualFile getSchemaFileForFile(VirtualFile file) {
        String schemaUrl = getResolvedSchemaUrl(file);
        if (schemaUrl == null || isIgnoredAsHavingTooManyVariants(schemaUrl)) {
            return null;
        }
        return JsonFileResolver.resolveSchemaByReference(file, schemaUrl, myProject);
    }

    @Override
    public @Nullable JsonSchemaCatalogEntry getSchemaCatalogEntryForFile(VirtualFile file) {
        String schemaUrl = getResolvedSchemaUrl(file);
        VirtualFile catalog = myCatalog;
        if (schemaUrl == null || isIgnoredAsHavingTooManyVariants(schemaUrl) || catalog == null) {
            return null;
        }
        return resolveSchemaEntry(file, catalog, myProject, schemaUrl);
    }

    private @Nullable String getResolvedSchemaUrl(VirtualFile file) {
        if (!JsonSchemaCatalogProjectConfiguration.getInstance(myProject).isCatalogEnabled()) {
            return null;
        }

        if (Application.get().getExtensionPoint(JsonSchemaCatalogExclusion.class).anyMatchSafe(exclusion -> exclusion.isExcluded(file))) {
            return null;
        }

        String schemaUrl = myResolvedMappings.get(file);
        if (EMPTY.equals(schemaUrl)) {
            return null;
        }
        VirtualFile catalog = myCatalog;
        if (schemaUrl == null && catalog != null) {
            schemaUrl = resolveSchemaFile(file, catalog, myProject);
            myResolvedMappings.put(file, StringUtil.notNullize(schemaUrl, EMPTY));
        }
        return schemaUrl;
    }

    private static boolean isIgnoredAsHavingTooManyVariants(String schemaUrl) {
        return ContainerUtil.exists(SCHEMA_URL_PREFIXES_WITH_TOO_MANY_VARIANTS, prefix -> schemaUrl.startsWith(prefix));
    }

    @Override
    public List<JsonSchemaCatalogEntry> getAllCatalogEntries() {
        VirtualFile catalogFile = myCatalog;
        if (catalogFile != null) {
            List<JsonSchemaCatalogEntry> catalog = JsonCachedValues.getSchemaCatalog(catalogFile, myProject);
            return catalog == null ? List.of() : catalog;
        }
        return List.of();
    }

    @Override
    public void registerCatalogUpdateCallback(Runnable callback) {
        Disposable disposable = Disposable.newDisposable();
        myCatalogUpdateListeners.put(callback, disposable);
        myProject.getMessageBus().connect(disposable).subscribe(RemoteSchemaDownloadListener.class, url -> {
            if (DEFAULT_CATALOG_HTTPS.equals(url)) {
                callback.run();
            }
        });
    }

    @Override
    public void unregisterCatalogUpdateCallback(Runnable callback) {
        Disposable disposable = myCatalogUpdateListeners.remove(callback);
        if (disposable != null) {
            Disposer.dispose(disposable);
        }
    }

    @Override
    public void triggerUpdateCatalog(Project project) {
        // an explicit user request, so try again even after a failed download
        JsonSchemaRemoteContentService.getInstance(project).retryDownload(DEFAULT_CATALOG_HTTPS);
    }

    private static @Nullable String resolveSchemaFile(VirtualFile file, VirtualFile catalogFile, Project project) {
        List<JsonSchemaCatalogEntry> schemaCatalog = JsonCachedValues.getSchemaCatalog(catalogFile, project);
        if (schemaCatalog == null) {
            return null;
        }

        JsonSchemaCatalogEntry entry = resolveSchemaEntry(file, schemaCatalog, project, null);
        return entry == null ? null : entry.getUrl();
    }

    private static @Nullable JsonSchemaCatalogEntry resolveSchemaEntry(VirtualFile file,
                                                                       VirtualFile catalogFile,
                                                                       Project project,
                                                                       String schemaUrl) {
        List<JsonSchemaCatalogEntry> schemaCatalog = JsonCachedValues.getSchemaCatalog(catalogFile, project);
        if (schemaCatalog == null) {
            return null;
        }

        return resolveSchemaEntry(file, schemaCatalog, project, schemaUrl);
    }

    private static @Nullable JsonSchemaCatalogEntry resolveSchemaEntry(VirtualFile file,
                                                                       List<JsonSchemaCatalogEntry> schemaCatalog,
                                                                       Project project,
                                                                       @Nullable String schemaUrl) {
        List<JsonSchemaCatalogEntryFileMatcher> fileMatchers = ContainerUtil.mapNotNull(schemaCatalog,
            entry1 -> schemaUrl == null || schemaUrl.equals(entry1.getUrl())
                ? new JsonSchemaCatalogEntryFileMatcher(entry1)
                : null);

        return findMatchedEntry(fileMatchers, file, project);
    }

    private static @Nullable JsonSchemaCatalogEntry findMatchedEntry(List<JsonSchemaCatalogEntryFileMatcher> matchers,
                                                                     VirtualFile file,
                                                                     Project project) {
        for (JsonSchemaCatalogEntryFileMatcher matcher : matchers) {
            if (matcher.matches(file, project)) {
                return matcher.getEntry();
            }
        }
        return null;
    }
}
