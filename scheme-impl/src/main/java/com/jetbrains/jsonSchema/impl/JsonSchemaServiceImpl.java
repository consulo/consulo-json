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
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.pointer.JsonPointerUtil;
import com.jetbrains.jsonSchema.JsonSchemaCatalogManager;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.JsonSchemaVersion;
import com.jetbrains.jsonSchema.extension.ContentAwareJsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectStorage;
import com.jetbrains.jsonSchema.internal.JsonCachedValues;
import com.jetbrains.jsonSchema.internal.JsonFileResolver;
import com.jetbrains.jsonSchema.internal.JsonSchemaCatalogEntry;
import com.jetbrains.jsonSchema.internal.JsonSchemaCatalogProjectConfiguration;
import com.jetbrains.jsonSchema.remote.JsonSchemaRemoteContentService;
import com.jetbrains.jsonSchema.remote.SchemaOrigin;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.application.util.SynchronizedClearableLazy;
import consulo.component.ProcessCanceledException;
import consulo.component.messagebus.MessageBusConnection;
import consulo.component.util.ModificationTracker;
import consulo.component.util.PluginExceptionUtil;
import consulo.disposer.Disposable;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.logging.Logger;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import consulo.util.lang.StringUtil;
import consulo.util.lang.lazy.ClearableLazyValue;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Singleton
@ServiceImpl
public class JsonSchemaServiceImpl implements JsonSchemaService, ModificationTracker, Disposable {
    private static final Logger LOG = Logger.getInstance(JsonSchemaServiceImpl.class);

    private final Project myProject;
    private final MyState myState;
    private final ClearableLazyValue<Set<String>> myBuiltInSchemaIds;
    private final Set<String> myRefs = ConcurrentHashMap.newKeySet();
    private final AtomicLong myAnyChangeCount = new AtomicLong(0);

    private final JsonSchemaCatalogManager myCatalogManager;
    private final JsonSchemaProviderFactories myFactories;

    @Inject
    public JsonSchemaServiceImpl(Project project) {
        myProject = project;
        myFactories = new JsonSchemaProviderFactories();
        myState = new MyState(() -> myFactories.getProviders());
        myBuiltInSchemaIds = ClearableLazyValue.notNull(
            () -> {
                Set<String> ids = new HashSet<>();
                for (VirtualFile file : myState.getFiles()) {
                    String id = JsonCachedValues.getSchemaId(file, myProject);
                    if (id != null) {
                        ids.add(id);
                    }
                }
                return ids;
            }
        );

        myCatalogManager = JsonSchemaCatalogManager.getInstance(myProject);

        MessageBusConnection connection = project.getMessageBus().connect(this);
        connection.subscribe(JsonSchemaVfsListener.JSON_SCHEMA_CHANGED, myAnyChangeCount::incrementAndGet);
        connection.subscribe(JsonSchemaVfsListener.JSON_DEPS_CHANGED, () -> {
            myRefs.clear();
            myAnyChangeCount.incrementAndGet();
        });
        JsonSchemaVfsListener.startListening(project);
        myCatalogManager.startUpdates(this);
    }

    @Override
    public long getModificationCount() {
        return myAnyChangeCount.get();
    }

    @Override
    public void dispose() {
    }

    protected List<JsonSchemaProviderFactory> getProviderFactories() {
        return Application.get().getExtensionPoint(JsonSchemaProviderFactory.class).getExtensionList();
    }

    @Override
    public @Nullable JsonSchemaFileProvider getSchemaProvider(VirtualFile schemaFile) {
        return myState.getProvider(schemaFile);
    }

    @Override
    public @Nullable JsonSchemaFileProvider getSchemaProvider(JsonSchemaObject schemaObject) {
        VirtualFile file = resolveSchemaFile(schemaObject);
        return file == null ? null : getSchemaProvider(file);
    }

    @Override
    public @Nullable JsonSchemaFileProvider getSchemaProviderForFile(VirtualFile file, JsonSchemaObject schemaObject) {
        VirtualFile schemaFile = resolveSchemaFile(schemaObject);
        return schemaFile == null ? null : myState.getProvider(schemaFile, file);
    }

    @Override
    public void reset() {
        myFactories.reset();
        resetWithCurrentFactories();
    }

    private void resetWithCurrentFactories() {
        myState.reset();
        myBuiltInSchemaIds.clear();
        myAnyChangeCount.incrementAndGet();
        for (Runnable action : myResetActions) {
            action.run();
        }

        if (!myProject.isDisposed()) {
            DaemonCodeAnalyzer daemonCodeAnalyzer = myProject.getInstanceIfCreated(DaemonCodeAnalyzer.class);
            if (daemonCodeAnalyzer != null) {
                daemonCodeAnalyzer.restart();
            }
        }
    }

    @Override
    public Project getProject() {
        return myProject;
    }

    @Override
    public @Nullable VirtualFile findSchemaFileByReference(String reference, VirtualFile referent) {
        boolean restrictReference = shouldRestrictSchemaReferences(referent);
        VirtualFile file = restrictReference
            ? findAllowedRegisteredSchemaByReference(reference)
            : findBuiltInSchemaByReference(reference);
        if (file != null) {
            return file;
        }
        if (reference.startsWith("#")) {
            return referent;
        }
        String normalizedReference = JsonPointerUtil.normalizeId(reference);
        VirtualFile resolved;
        if (restrictReference) {
            String resolvedUrl = JsonFileResolver.resolveSchemaUrlByReference(referent, normalizedReference);
            if (resolvedUrl == null || isForbiddenBeforeResolution(resolvedUrl)) {
                return null;
            }
            resolved = JsonFileResolver.urlToFile(resolvedUrl);
        }
        else {
            resolved = JsonFileResolver.resolveSchemaByReference(referent, normalizedReference, myProject);
        }

        if (restrictReference && resolved != null && isForbiddenAfterResolution(resolved)) {
            return null;
        }
        return resolved;
    }

    /**
     * Restricts {@code $schema}/{@code $ref} references originating in a project before the user has granted trust
     * (IJPL-249802, CWE-22 / CWE-862 / CWE-918).
     * Also restricts references from a file in the safe mode (IJPL-254130).
     */
    @Override
    public boolean shouldRestrictSchemaReferences(VirtualFile referent) {
        return false;
    }

    private boolean isForbiddenBeforeResolution(String resolvedUrl) {
        if (!LocalFileSystem.PROTOCOL.equals(VirtualFileManager.extractProtocol(resolvedUrl))) {
            return true;
        }
        return !isPathInsideProject(toPath(VirtualFileManager.extractPath(resolvedUrl), false));
    }

    private boolean isForbiddenAfterResolution(VirtualFile resolved) {
        if (!resolved.isInLocalFileSystem()) {
            return true;
        }
        try {
            return !isPathInsideProject(resolved.toNioPath().toRealPath());
        }
        catch (IOException | UnsupportedOperationException e) {
            return true;
        }
    }

    private boolean isPathInsideProject(@Nullable Path path) {
        if (path == null) {
            return false;
        }
        String basePath = myProject.getBasePath();
        return basePath != null && startsWithProjectRoot(path, basePath);
    }

    private static boolean startsWithProjectRoot(Path path, String rootPath) {
        Path normalizedRoot = toPath(rootPath, false);
        if (normalizedRoot != null && path.startsWith(normalizedRoot)) {
            return true;
        }
        Path realRoot = toPath(rootPath, true);
        return realRoot != null && path.startsWith(realRoot);
    }

    private static @Nullable Path toPath(String path, boolean realPath) {
        try {
            Path nioPath = Path.of(path);
            return realPath ? nioPath.toRealPath() : nioPath.toAbsolutePath().normalize();
        }
        catch (IOException | InvalidPathException e) {
            return null;
        }
    }

    @Override
    public @Nullable VirtualFile findBuiltInSchemaByReference(String reference) {
        String id = JsonPointerUtil.normalizeId(reference);
        if (!myBuiltInSchemaIds.get().contains(id)) {
            return null;
        }
        for (VirtualFile file : myState.getFiles()) {
            if (id.equals(JsonCachedValues.getSchemaId(file, myProject))) {
                return file;
            }
        }
        return null;
    }

    private @Nullable VirtualFile findAllowedRegisteredSchemaByReference(String reference) {
        String id = JsonPointerUtil.normalizeId(reference);
        for (JsonSchemaFileProvider provider : myFactories.getProviders()) {
            VirtualFile file;
            try {
                file = provider.getSchemaFile();
            }
            catch (ProcessCanceledException e) {
                throw e;
            }
            catch (Exception e) {
                LOG.error(e);
                continue;
            }
            if (file == null || (!isBundledSchemaProvider(provider) && isForbiddenAfterResolution(file))) {
                continue;
            }
            if (id.equals(JsonCachedValues.getSchemaId(file, myProject))) {
                return file;
            }
        }
        return null;
    }

    private static boolean isBundledSchemaProvider(JsonSchemaFileProvider provider) {
        return provider.getSchemaType() == SchemaType.schema || provider.getSchemaType() == SchemaType.embeddedSchema;
    }

    @Override
    public Collection<VirtualFile> getSchemaFilesForFile(VirtualFile file) {
        return getSchemasForFile(file, false, false);
    }

    @Override
    public @Nullable VirtualFile getDynamicSchemaForFile(PsiFile psiFile) {
        return Application.get().getExtensionPoint(ContentAwareJsonSchemaFileProvider.class)
            .computeSafeIfAny(provider -> provider.getSchemaFile(psiFile));
    }

    @Override
    public Collection<VirtualFile> getSchemasForFile(VirtualFile file, boolean single, boolean onlyUserSchemas) {
        if (JsonSchemaMappingsProjectConfiguration.getInstance(myProject).isIgnoredFile(file)) {
            return Collections.emptyList();
        }
        String schemaUrl = null;
        if (!onlyUserSchemas) {
            // prefer schema-schema if it is specified in "$schema" property
            schemaUrl = JsonCachedValues.getSchemaUrlFromSchemaProperty(file, myProject);
            if (JsonFileResolver.isSchemaUrl(schemaUrl)) {
                VirtualFile virtualFile = resolveFromSchemaProperty(schemaUrl, file);
                if (virtualFile != null) {
                    return Collections.singletonList(virtualFile);
                }
            }
        }


        List<JsonSchemaFileProvider> providers = getProvidersForFile(file);

        // proper priority:
        // 1) user providers
        // 2) $schema property
        // 3) built-in providers
        // SchemaStore matches are suggested separately via editor notification.

        boolean checkSchemaProperty = true;
        if (!onlyUserSchemas && providers.stream().noneMatch(p -> p.getSchemaType() == SchemaType.userSchema)) {
            if (schemaUrl == null) {
                schemaUrl = JsonCachedValues.getSchemaUrlFromSchemaProperty(file, myProject);
            }
            if (schemaUrl == null) {
                schemaUrl = JsonSchemaByCommentProvider.getCommentSchema(file, myProject);
            }
            VirtualFile virtualFile = resolveFromSchemaProperty(schemaUrl, file);
            if (virtualFile != null) {
                return Collections.singletonList(virtualFile);
            }
            checkSchemaProperty = false;
        }

        if (!single) {
            List<VirtualFile> files = new ArrayList<>();
            for (JsonSchemaFileProvider provider : providers) {
                VirtualFile schemaFile = getAllowedSchemaForProvider(file, provider);
                if (schemaFile != null) {
                    files.add(schemaFile);
                }
            }
            if (!files.isEmpty()) {
                return files;
            }
        }
        else if (!providers.isEmpty()) {
            JsonSchemaFileProvider selected;
            if (providers.size() > 1) {
                Optional<JsonSchemaFileProvider> userSchema =
                    providers.stream().filter(provider -> SchemaType.userSchema.equals(provider.getSchemaType())).findFirst();
                selected = userSchema.orElse(providers.get(0));
            }
            else {
                selected = providers.get(0);
            }
            VirtualFile schemaFile = getAllowedSchemaForProvider(file, selected);
            return ContainerUtil.createMaybeSingletonList(schemaFile);
        }

        if (onlyUserSchemas) {
            return Collections.emptyList();
        }

        if (checkSchemaProperty) {
            if (schemaUrl == null) {
                schemaUrl = JsonCachedValues.getSchemaUrlFromSchemaProperty(file, myProject);
            }
            VirtualFile virtualFile = resolveFromSchemaProperty(schemaUrl, file);
            if (virtualFile != null) {
                return Collections.singletonList(virtualFile);
            }
        }

        PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
        if (psiFile == null) {
            return Collections.emptyList();
        }
        else {
            return ContainerUtil.createMaybeSingletonList(getDynamicSchemaForFile(psiFile));
        }
    }

    public List<JsonSchemaFileProvider> getProvidersForFile(VirtualFile file) {
        List<JsonSchemaFileProvider> providers = myState.getProviders();
        if (providers.isEmpty()) {
            return Collections.emptyList();
        }

        List<JsonSchemaFileProvider> result = null;
        for (JsonSchemaFileProvider provider : providers) {
            if (isProviderAvailable(file, provider)) {
                if (result == null) {
                    result = new SmartList<>();
                }
                result.add(provider);
            }
        }
        return result == null ? Collections.emptyList() : result;
    }

    private @Nullable VirtualFile resolveFromSchemaProperty(@Nullable String schemaUrl, VirtualFile file) {
        if (schemaUrl != null) {
            VirtualFile virtualFile = findSchemaFileByReference(schemaUrl, file);
            if (virtualFile != null) {
                return virtualFile;
            }
        }
        return null;
    }

    @Override
    public List<JsonSchemaInfo> getAllUserVisibleSchemas() {
        List<JsonSchemaCatalogEntry> schemas = myCatalogManager.getAllCatalogEntries();
        List<JsonSchemaFileProvider> providers = myState.getProviders();
        List<JsonSchemaInfo> results = new ArrayList<>(schemas.size() + providers.size());
        Map<String, JsonSchemaInfo> processedRemotes = new HashMap<>();
        myState.processProviders(provider -> {
            if (provider.isUserVisible()) {
                String remoteSource = provider.getRemoteSource();
                if (remoteSource != null) {
                    if (!processedRemotes.containsKey(remoteSource)) {
                        JsonSchemaInfo info = new JsonSchemaInfo(provider);
                        processedRemotes.put(remoteSource, info);
                        results.add(info);
                    }
                }
                else {
                    results.add(new JsonSchemaInfo(provider));
                }
            }
        });

        for (JsonSchemaCatalogEntry schema : schemas) {
            String url = schema.getUrl();
            if (!processedRemotes.containsKey(url)) {
                JsonSchemaInfo info = new JsonSchemaInfo(url);
                if (schema.getDescription() != null) {
                    info.setDocumentation(schema.getDescription());
                }
                if (schema.getName() != null) {
                    info.setName(schema.getName());
                }
                results.add(info);
            }
            else {
                // use documentation from schema catalog for bundled schemas if possible
                // we don't have our own docs, so let's reuse the existing docs from the catalog
                JsonSchemaInfo info = processedRemotes.get(url);
                if (info.getDocumentation() == null) {
                    info.setDocumentation(schema.getDescription());
                }
                if (info.getName() == null) {
                    info.setName(schema.getName());
                }
            }
        }
        return results;
    }

    @Override
    public @Nullable JsonSchemaObject getSchemaObject(VirtualFile file) {
        Collection<VirtualFile> schemas = getSchemasForFile(file, true, false);
        if (schemas.isEmpty()) {
            return null;
        }
        assert schemas.size() == 1;
        VirtualFile schemaFile = schemas.iterator().next();
        JsonSchemaObject result = JsonCachedValues.getSchemaObject(schemaFile, myProject);
        if (result == null) {
            String message = "JSON Schema mapped to '" + file.getName() + "' could not be loaded from '" + schemaFile.getUrl() +
                "' (valid=" + schemaFile.isValid() + ", length=" + schemaFile.getLength() + ")";
            LOG.warn(message);
        }
        return result;
    }


    @Override
    public @Nullable JsonSchemaObject getSchemaObject(PsiFile file) {
        return JsonCachedValues.computeSchemaForFile(file, this);
    }

    @Override
    public @Nullable JsonSchemaObject getSchemaObjectForSchemaFile(VirtualFile schemaFile) {
        return JsonCachedValues.getSchemaObject(schemaFile, myProject);
    }

    @Override
    public boolean isSchemaFile(VirtualFile file) {
        return !file.isDirectory()
            && (isMappedSchema(file)
            || isSchemaByProvider(file)
            || hasSchemaSchema(file));
    }

    @Override
    public boolean isSchemaFile(JsonSchemaObject schemaObject) {
        VirtualFile file = resolveSchemaFile(schemaObject);
        return file != null && isSchemaFile(file);
    }

    public boolean isMappedSchema(VirtualFile file) {
        return isMappedSchema(file, true);
    }

    public boolean isMappedSchema(VirtualFile file, boolean canRecompute) {
        if (!canRecompute) {
            return myState.isMappedIfComputed(file);
        }
        if (myState.getFiles().contains(file)) {
            return true;
        }
        return myState.getProvider(file) != null;
    }

    private boolean isSchemaByProvider(VirtualFile file) {
        JsonSchemaFileProvider provider = myState.getProvider(file);
        if (provider != null) {
            return isSchemaProvider(provider);
        }

        for (JsonSchemaFileProvider p : myState.getProviders()) {
            if (isSchemaProvider(p) && p.isAvailable(file)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSchemaProvider(JsonSchemaFileProvider provider) {
        return JsonFileResolver.isSchemaUrl(provider.getRemoteSource());
    }

    @Override
    public @Nullable JsonSchemaVersion getSchemaVersion(VirtualFile file) {
        if (isMappedSchema(file)) {
            JsonSchemaFileProvider provider = myState.getProvider(file);
            if (provider != null) {
                return provider.getSchemaVersion();
            }
        }

        return getSchemaVersionFromSchemaUrl(file);
    }

    private @Nullable JsonSchemaVersion getSchemaVersionFromSchemaUrl(VirtualFile file) {
        String schemaPropertyValue;
        JsonSchemaObject schemaRootOrNull = JsonSchemaObjectStorage.getInstance(myProject).getComputedSchemaRootOrNull(file);
        if (schemaRootOrNull != null) {
            schemaPropertyValue = schemaRootOrNull.getSchema();
            return schemaPropertyValue == null ? null : JsonSchemaVersion.byId(schemaPropertyValue);
        }

        schemaPropertyValue = ReadAction.compute(() -> JsonCachedValues.getSchemaUrlFromSchemaProperty(file, myProject));
        return schemaPropertyValue == null ? null : JsonSchemaVersion.byId(schemaPropertyValue);
    }

    private boolean hasSchemaSchema(VirtualFile file) {
        return getSchemaVersionFromSchemaUrl(file) != null;
    }

    private static boolean isProviderAvailable(VirtualFile file, JsonSchemaFileProvider provider) {
        return provider.isAvailable(file);
    }

    @Override
    public void registerRemoteUpdateCallback(Runnable callback) {
        myCatalogManager.registerCatalogUpdateCallback(callback);
    }

    @Override
    public void unregisterRemoteUpdateCallback(Runnable callback) {
        myCatalogManager.unregisterCatalogUpdateCallback(callback);
    }

    private final List<Runnable> myResetActions = new CopyOnWriteArrayList<>();

    @Override
    public void registerResetAction(Runnable action) {
        myResetActions.add(action);
    }

    @Override
    public void unregisterResetAction(Runnable action) {
        myResetActions.remove(action);
    }

    @Override
    public void registerReference(String ref) {
        int index = StringUtil.lastIndexOfAny(ref, "\\/");
        myRefs.add(index >= 0 ? ref.substring(index + 1) : ref);
    }

    @Override
    public boolean possiblyHasReference(String ref) {
        return myRefs.contains(ref);
    }

    @Override
    public void triggerUpdateRemote() {
        myCatalogManager.triggerUpdateCatalog(myProject);
    }

    @Override
    public boolean isApplicableToFile(@Nullable VirtualFile file) {
        if (file == null) {
            return false;
        }
        return Application.get().getExtensionPoint(JsonSchemaEnabler.class).anyMatchSafe(e -> e.isEnabledForFile(file, myProject));
    }

    @Override
    public JsonSchemaCatalogManager getCatalogManager() {
        return myCatalogManager;
    }

    private static final class MyState {
        private final SynchronizedClearableLazy<ProviderIndex> myIndex;

        private MyState(Supplier<List<JsonSchemaFileProvider>> factory) {
            myIndex = new SynchronizedClearableLazy<>(() -> new ProviderIndex(factory.get()));
        }

        public void reset() {
            myIndex.drop();
        }

        public void processProviders(Consumer<JsonSchemaFileProvider> consumer) {
            myIndex.getValue().providers.forEach(consumer);
        }

        public Set<VirtualFile> getFiles() {
            return myIndex.getValue().schemaFiles;
        }

        public List<JsonSchemaFileProvider> getProviders() {
            return myIndex.getValue().providers;
        }

        public @Nullable JsonSchemaFileProvider getProvider(VirtualFile file) {
            return myIndex.getValue().getProvider(file);
        }

        public @Nullable JsonSchemaFileProvider getProvider(VirtualFile schemaFile, VirtualFile file) {
            return myIndex.getValue().getProvider(schemaFile, file);
        }

        private boolean isMappedIfComputed(VirtualFile file) {
            ProviderIndex index = myIndex.getValueIfInitialized();
            return index != null && index.getProvider(file) != null;
        }
    }

    private static final class ProviderIndex {
        private final List<JsonSchemaFileProvider> providers;
        private final Map<VirtualFile, JsonSchemaFileProvider> bySchemaFile;
        private final Map<VirtualFile, List<JsonSchemaFileProvider>> providersBySchemaFile;
        private final Map<JsonSchemaFileProvider, VirtualFile> schemaFileOf;
        private final Set<VirtualFile> schemaFiles;

        private ProviderIndex(List<JsonSchemaFileProvider> list) {
            List<JsonSchemaFileProvider> providers = new ArrayList<>(list.size());
            Map<VirtualFile, JsonSchemaFileProvider> bySchemaFile = new LinkedHashMap<>();
            Map<VirtualFile, List<JsonSchemaFileProvider>> providersBySchemaFile = new LinkedHashMap<>();
            Map<JsonSchemaFileProvider, VirtualFile> schemaFileOf = new HashMap<>();
            for (JsonSchemaFileProvider provider : list) {
                VirtualFile schemaFile;
                try {
                    schemaFile = provider.getSchemaFile();
                }
                catch (ProcessCanceledException e) {
                    throw e;
                }
                catch (Exception e) {
                    LOG.error(e);
                    continue;
                }
                providers.add(provider);
                if (schemaFile == null) {
                    continue;
                }
                schemaFileOf.put(provider, schemaFile);
                providersBySchemaFile.computeIfAbsent(schemaFile, k -> new ArrayList<>()).add(provider);
                JsonSchemaFileProvider existing = bySchemaFile.get(schemaFile);
                if (existing == null
                    || (existing.getSchemaType() != SchemaType.userSchema && provider.getSchemaType() == SchemaType.userSchema)) {
                    bySchemaFile.put(schemaFile, provider);
                }
            }
            this.providers = Collections.unmodifiableList(providers);
            this.bySchemaFile = bySchemaFile;
            this.providersBySchemaFile = providersBySchemaFile;
            this.schemaFileOf = schemaFileOf;
            schemaFiles = Collections.unmodifiableSet(bySchemaFile.keySet());
        }

        private @Nullable JsonSchemaFileProvider getProvider(VirtualFile file) {
            if (file.getUserData(SchemaOrigin.REQUEST_URL_KEY) == null) {
                return bySchemaFile.get(file);
            }
            return match(file, null);
        }

        private @Nullable JsonSchemaFileProvider getProvider(VirtualFile schemaFile, VirtualFile file) {
            if (schemaFile.getUserData(SchemaOrigin.REQUEST_URL_KEY) == null) {
                return selectAvailable(schemaFile, file);
            }
            return match(schemaFile, file);
        }

        private @Nullable JsonSchemaFileProvider match(VirtualFile schemaFile, @Nullable VirtualFile availabilityFile) {
            JsonSchemaFileProvider result = null;
            for (JsonSchemaFileProvider provider : providers) {
                boolean matches = schemaFile.equals(schemaFileOf.get(provider)) ||
                    SchemaOrigin.isCachedContentOf(schemaFile, provider.getRemoteSource());
                if (!matches) {
                    continue;
                }
                if (availabilityFile != null && !isProviderAvailable(availabilityFile, provider)) {
                    continue;
                }
                if (provider.getSchemaType() == SchemaType.userSchema) {
                    return provider;
                }
                if (result == null) {
                    result = provider;
                }
            }
            return result;
        }

        private @Nullable JsonSchemaFileProvider selectAvailable(VirtualFile schemaFile, VirtualFile file) {
            List<JsonSchemaFileProvider> candidates = providersBySchemaFile.get(schemaFile);
            if (candidates == null) {
                return null;
            }
            JsonSchemaFileProvider result = null;
            for (JsonSchemaFileProvider provider : candidates) {
                if (!isProviderAvailable(file, provider)) {
                    continue;
                }
                if (provider.getSchemaType() == SchemaType.userSchema) {
                    return provider;
                }
                if (result == null) {
                    result = provider;
                }
            }
            return result;
        }
    }

    private static @Nullable VirtualFile getSchemaForProvider(Project project, JsonSchemaFileProvider provider) {
        VirtualFile schemaFile = provider.getSchemaFile();
        String source = provider.getRemoteSource();
        boolean preferRemote = JsonSchemaCatalogProjectConfiguration.getInstance(project).isPreferRemoteSchemas();
        if (source != null &&
            (schemaFile == null || (!source.endsWith("!") && preferRemote && !JsonFileResolver.isSchemaUrl(source)))) {
            VirtualFile cachedFile = JsonSchemaRemoteContentService.getInstance(project).getCachedFile(source);
            if (cachedFile != null || schemaFile == null) {
                return cachedFile;
            }
        }
        return schemaFile;
    }

    private @Nullable VirtualFile getAllowedSchemaForProvider(VirtualFile referent, JsonSchemaFileProvider provider) {
        boolean restrictReference = shouldRestrictSchemaReferences(referent);
        VirtualFile schemaFile = restrictReference ? provider.getSchemaFile() : getSchemaForProvider(myProject, provider);
        if (schemaFile == null || !restrictReference || isBundledSchemaProvider(provider)) {
            return schemaFile;
        }
        return isForbiddenAfterResolution(schemaFile) ? null : schemaFile;
    }

    @Override
    public @Nullable VirtualFile resolveSchemaFile(JsonSchemaObject schemaObject) {
        VirtualFile rawFile = schemaObject.getRawFile();
        if (rawFile != null) {
            return rawFile;
        }

        String fileUrl = schemaObject.getFileUrl();
        if (fileUrl == null) {
            return null;
        }

        return VirtualFileManager.getInstance().findFileByUrl(fileUrl);
    }

    private final class JsonSchemaProviderFactories {

        private volatile @Nullable List<JsonSchemaFileProvider> myProviders;

        public List<JsonSchemaFileProvider> getProviders() {
            List<JsonSchemaFileProvider> providers = myProviders;
            if (providers == null) {
                providers = getDumbAwareProvidersAndUpdateRestWhenSmart();
                myProviders = providers;
            }
            return providers;
        }

        public void reset() {
            myProviders = null;
        }

        private List<JsonSchemaFileProvider> getDumbAwareProvidersAndUpdateRestWhenSmart() {
            List<JsonSchemaProviderFactory> readyFactories = new ArrayList<>();
            List<JsonSchemaProviderFactory> notReadyFactories = new ArrayList<>();
            boolean dumb = DumbService.getInstance(myProject).isDumb();
            for (JsonSchemaProviderFactory factory : getProviderFactories()) {
                if (!dumb || DumbService.isDumbAware(factory)) {
                    readyFactories.add(factory);
                }
                else {
                    notReadyFactories.add(factory);
                }
            }
            List<JsonSchemaFileProvider> providers = getProvidersFromFactories(readyFactories);
            myProviders = providers;
            if (!notReadyFactories.isEmpty()) {
                Application.get().executeOnPooledThread(() -> {
                    if (myProject.isDisposed()) {
                        return;
                    }
                    DumbService.getInstance(myProject).runReadActionInSmartMode(() -> {
                        if (myProviders == providers) {
                            List<JsonSchemaFileProvider> newProviders = getProvidersFromFactories(notReadyFactories);
                            if (!newProviders.isEmpty()) {
                                List<JsonSchemaFileProvider> oldProviders = providers;
                                myProviders = ContainerUtil.concat(oldProviders, newProviders);
                                JsonSchemaServiceImpl.this.resetWithCurrentFactories();
                            }
                        }
                    });
                });
            }
            return providers;
        }

        private List<JsonSchemaFileProvider> getProvidersFromFactories(List<JsonSchemaProviderFactory> factories) {
            List<JsonSchemaFileProvider> providers = new ArrayList<>();
            for (JsonSchemaProviderFactory factory : factories) {
                try {
                    providers.addAll(factory.getProviders(myProject));
                }
                catch (ProcessCanceledException e) {
                    throw e;
                }
                catch (Exception e) {
                    PluginExceptionUtil.logPluginError(Logger.getInstance(JsonSchemaService.class), e.toString(), e, factory.getClass());
                }
            }
            return providers;
        }
    }
}
