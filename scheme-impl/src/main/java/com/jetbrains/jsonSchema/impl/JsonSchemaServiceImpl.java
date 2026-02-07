// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.jetbrains.jsonSchema.*;
import com.jetbrains.jsonSchema.extension.*;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectStorage;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogExclusion;
import com.jetbrains.jsonSchema.impl.remote.JsonSchemaCatalogManagerImpl;
import consulo.application.ApplicationManager;
import consulo.application.util.SynchronizedClearableLazy;
import consulo.application.util.registry.Registry;
import consulo.component.ProcessCanceledException;
import consulo.component.messagebus.MessageBusConnection;
import consulo.component.util.ModificationTracker;
import consulo.disposer.Disposable;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.logging.Logger;
import consulo.logging.attachment.Attachment;
import consulo.logging.attachment.RuntimeExceptionWithAttachments;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.http.HttpVirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class JsonSchemaServiceImpl implements JsonSchemaService, ModificationTracker, Disposable {
  private static final Logger LOG = Logger.getInstance(JsonSchemaServiceImpl.class);

  private final @Nonnull Project myProject;
  private final @Nonnull MyState myState;
  private final @Nonnull ClearableLazyValue<@Unmodifiable Set<String>> myBuiltInSchemaIds;
  private final @Nonnull Set<String> myRefs = ConcurrentCollectionFactory.createConcurrentSet();
  private final AtomicLong myAnyChangeCount = new AtomicLong(0);

  private final @Nonnull JsonSchemaCatalogManager myCatalogManager;
  private final @Nonnull JsonSchemaVfsListener.JsonSchemaUpdater mySchemaUpdater;
  private final JsonSchemaProviderFactories myFactories;

  public JsonSchemaServiceImpl(@Nonnull Project project) {
    myProject = project;
    myFactories = new JsonSchemaProviderFactories();
    myState = new MyState(() -> myFactories.getProviders(), myProject);
    myBuiltInSchemaIds = new ClearableLazyValue<>() {
      @Override
      protected @Nonnull Set<String> compute() {
        return ContainerUtil.map2SetNotNull(myState.getFiles(), f -> JsonCachedValues.getSchemaId(f, myProject));
      }
    };
    JsonSchemaProviderFactory.EP_NAME.addChangeListener(this::reset, this);
    JsonSchemaEnabler.EXTENSION_POINT_NAME.addChangeListener(this::reset, this);
    JsonSchemaCatalogExclusion.EP_NAME.addChangeListener(this::reset, this);

    myCatalogManager = new JsonSchemaCatalogManager(myProject);

    MessageBusConnection connection = project.getMessageBus().connect(this);
    connection.subscribe(JsonSchemaVfsListener.JSON_SCHEMA_CHANGED, myAnyChangeCount::incrementAndGet);
    connection.subscribe(JsonSchemaVfsListener.JSON_DEPS_CHANGED, () -> {
      myRefs.clear();
      myAnyChangeCount.incrementAndGet();
    });
    mySchemaUpdater = JsonSchemaVfsListener.startListening(project, this, connection);
    myCatalogManager.startUpdates();
  }

  @Override
  public long getModificationCount() {
    return myAnyChangeCount.get();
  }

  @Override
  public void dispose() {
  }

  protected @Nonnull List<JsonSchemaProviderFactory> getProviderFactories() {
    return JsonSchemaProviderFactory.EP_NAME.getExtensionList();
  }

  @Override
  public @Nullable JsonSchemaFileProvider getSchemaProvider(@Nonnull VirtualFile schemaFile) {
    return myState.getProvider(schemaFile);
  }

  @Override
  public @Nullable JsonSchemaFileProvider getSchemaProvider(@Nonnull JsonSchemaObject schemaObject) {
    VirtualFile file = resolveSchemaFile(schemaObject);
    return file == null ? null : getSchemaProvider(file);
  }

  @Override
  public void reset() {
    myFactories.reset();
    resetWithCurrentFactories();
  }

  private void resetWithCurrentFactories() {
    myState.reset();
    myBuiltInSchemaIds.drop();
    myAnyChangeCount.incrementAndGet();
    for (Runnable action : myResetActions) {
      action.run();
    }
    DaemonCodeAnalyzer.getInstance(myProject).restart(this);
  }

  @Override
  public @Nonnull Project getProject() {
    return myProject;
  }

  @Override
  public @Nullable VirtualFile findSchemaFileByReference(@Nonnull String reference, @Nullable VirtualFile referent) {
    final VirtualFile file = findBuiltInSchemaByReference(reference);
    if (file != null) return file;
    if (reference.startsWith("#")) return referent;
    return JsonFileResolver.resolveSchemaByReference(referent, JsonPointerUtil.normalizeId(reference));
  }

  private @Nullable VirtualFile findBuiltInSchemaByReference(@Nonnull String reference) {
    String id = JsonPointerUtil.normalizeId(reference);
    if (!myBuiltInSchemaIds.getValue().contains(id)) return null;
    for (VirtualFile file : myState.getFiles()) {
      if (id.equals(JsonCachedValues.getSchemaId(file, myProject))) {
        return file;
      }
    }
    return null;
  }

  @Override
  public @Nonnull Collection<VirtualFile> getSchemaFilesForFile(final @Nonnull VirtualFile file) {
    return getSchemasForFile(file, false, false);
  }

  @Override
  public @Nullable VirtualFile getDynamicSchemaForFile(@Nonnull PsiFile psiFile) {
    return ContentAwareJsonSchemaFileProvider.EP_NAME.getExtensionList().stream()
      .map(provider -> provider.getSchemaFile(psiFile))
      .filter(schemaFile -> schemaFile != null)
      .findFirst()
      .orElse(null);
  }

  private static boolean shouldIgnoreFile(@Nonnull VirtualFile file, @Nonnull Project project) {
    return JsonSchemaMappingsProjectConfiguration.getInstance(project).isIgnoredFile(file);
  }

  public @Nonnull Collection<VirtualFile> getSchemasForFile(@Nonnull VirtualFile file, boolean single, boolean onlyUserSchemas) {
    if (shouldIgnoreFile(file, myProject)) return Collections.emptyList();
    String schemaUrl = null;
    if (!onlyUserSchemas) {
      // prefer schema-schema if it is specified in "$schema" property
      schemaUrl = JsonCachedValues.getSchemaUrlFromSchemaProperty(file, myProject);
      if (JsonFileResolver.isSchemaUrl(schemaUrl)) {
        final VirtualFile virtualFile = resolveFromSchemaProperty(schemaUrl, file);
        if (virtualFile != null) return Collections.singletonList(virtualFile);
      }
    }


    List<JsonSchemaFileProvider> providers = getProvidersForFile(file);

    // proper priority:
    // 1) user providers
    // 2) $schema property
    // 3) built-in providers
    // 4) schema catalog

    boolean checkSchemaProperty = true;
    if (!onlyUserSchemas && providers.stream().noneMatch(p -> p.getSchemaType() == SchemaType.userSchema)) {
      if (schemaUrl == null) schemaUrl = JsonCachedValues.getSchemaUrlFromSchemaProperty(file, myProject);
      if (schemaUrl == null) schemaUrl = JsonSchemaByCommentProvider.getCommentSchema(file, myProject);
      VirtualFile virtualFile = resolveFromSchemaProperty(schemaUrl, file);
      if (virtualFile != null) return Collections.singletonList(virtualFile);
      checkSchemaProperty = false;
    }

    if (!single) {
      List<VirtualFile> files = new ArrayList<>();
      for (JsonSchemaFileProvider provider : providers) {
        VirtualFile schemaFile = getSchemaForProvider(myProject, provider);
        if (schemaFile != null) {
          files.add(schemaFile);
        }
      }
      if (!files.isEmpty()) {
        return files;
      }
    }
    else if (!providers.isEmpty()) {
      final JsonSchemaFileProvider selected;
      if (providers.size() > 2) return ContainerUtil.emptyList();
      if (providers.size() > 1) {
        final Optional<JsonSchemaFileProvider> userSchema =
          providers.stream().filter(provider -> SchemaType.userSchema.equals(provider.getSchemaType())).findFirst();
        if (userSchema.isEmpty()) return ContainerUtil.emptyList();
        selected = userSchema.get();
      }
      else {
        selected = providers.get(0);
      }
      VirtualFile schemaFile = getSchemaForProvider(myProject, selected);
      return ContainerUtil.createMaybeSingletonList(schemaFile);
    }

    if (onlyUserSchemas) {
      return ContainerUtil.emptyList();
    }

    if (checkSchemaProperty) {
      if (schemaUrl == null) schemaUrl = JsonCachedValues.getSchemaUrlFromSchemaProperty(file, myProject);
      VirtualFile virtualFile = resolveFromSchemaProperty(schemaUrl, file);
      if (virtualFile != null) return Collections.singletonList(virtualFile);
    }

    VirtualFile schemaFromOtherSources = resolveSchemaFromOtherSources(file);
    if (schemaFromOtherSources != null) {
      return ContainerUtil.createMaybeSingletonList(schemaFromOtherSources);
    }

    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    if (psiFile == null) {
      return Collections.emptyList();
    }
    else {
      return ContainerUtil.createMaybeSingletonList(getDynamicSchemaForFile(psiFile));
    }
  }

  public @Nonnull List<JsonSchemaFileProvider> getProvidersForFile(@Nonnull VirtualFile file) {
    Map<VirtualFile, List<JsonSchemaFileProvider>> map = myState.myData.getValue();
    if (map.isEmpty()) {
      return Collections.emptyList();
    }

    List<JsonSchemaFileProvider> result = null;
    for (List<JsonSchemaFileProvider> providers : map.values()) {
      for (JsonSchemaFileProvider provider : providers) {
        if (isProviderAvailable(file, provider)) {
          if (result == null) {
            result = new SmartList<>();
          }
          result.add(provider);
        }
      }
    }
    return result == null ? Collections.emptyList() : result;
  }

  private @Nullable VirtualFile resolveFromSchemaProperty(@Nullable String schemaUrl, @Nonnull VirtualFile file) {
    if (schemaUrl != null) {
      VirtualFile virtualFile = findSchemaFileByReference(schemaUrl, file);
      if (virtualFile != null) return virtualFile;
    }
    return null;
  }

  @Override
  public List<JsonSchemaInfo> getAllUserVisibleSchemas() {
    List<JsonSchemaCatalogEntry> schemas = myCatalogManager.getAllCatalogEntries();
    Map<VirtualFile, List<JsonSchemaFileProvider>> map = myState.myData.getValue();
    List<JsonSchemaInfo> results = new ArrayList<>(schemas.size() + map.size());
    Map<String, JsonSchemaInfo> processedRemotes = new HashMap<>();
    myState.processProviders(provider -> {
      if (provider.isUserVisible()) {
        final String remoteSource = provider.getRemoteSource();
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
      final String url = schema.getUrl();
      if (!processedRemotes.containsKey(url)) {
        final JsonSchemaInfo info = new JsonSchemaInfo(url);
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
  public @Nullable JsonSchemaObject getSchemaObject(final @Nonnull VirtualFile file) {
    Collection<VirtualFile> schemas = getSchemasForFile(file, true, false);
    if (schemas.isEmpty()) return null;
    assert schemas.size() == 1;
    VirtualFile schemaFile = schemas.iterator().next();
    return JsonCachedValues.getSchemaObject(replaceHttpFileWithBuiltinIfNeeded(schemaFile), myProject);
  }


  @Override
  public @Nullable JsonSchemaObject getSchemaObject(@Nonnull PsiFile file) {
    return JsonCachedValues.computeSchemaForFile(file, this);
  }

  public VirtualFile replaceHttpFileWithBuiltinIfNeeded(VirtualFile schemaFile) {
    // this hack is needed to handle user-defined mappings via urls
    // we cannot perform that inside corresponding provider, because it leads to recursive component dependency
    // this way we're preventing http files when a built-in schema exists
    if (schemaFile instanceof HttpVirtualFile && (!JsonSchemaCatalogProjectConfiguration.getInstance(myProject).isPreferRemoteSchemas()
                                                  || JsonFileResolver.isSchemaUrl(schemaFile.getUrl()))) {
      String url = schemaFile.getUrl();
      VirtualFile first1 = getLocalSchemaByUrl(url);
      return first1 != null ? first1 : schemaFile;
    }
    return schemaFile;
  }

  public @Nullable VirtualFile getLocalSchemaByUrl(String url) {
    return myState.getFiles().stream()
      .filter(f -> {
        JsonSchemaFileProvider prov = getSchemaProvider(f);
        return prov != null && !(prov.getSchemaFile() instanceof HttpVirtualFile)
               && (url.equals(prov.getRemoteSource()) || JsonFileResolver.replaceUnsafeSchemaStoreUrls(url).equals(prov.getRemoteSource())
                   || url.equals(JsonFileResolver.replaceUnsafeSchemaStoreUrls(prov.getRemoteSource())));
      }).findFirst().orElse(null);
  }

  @Override
  public @Nullable JsonSchemaObject getSchemaObjectForSchemaFile(@Nonnull VirtualFile schemaFile) {
    return JsonCachedValues.getSchemaObject(schemaFile, myProject);
  }

  @Override
  public boolean isSchemaFile(@Nonnull VirtualFile file) {
    return !file.isDirectory()
           && (isMappedSchema(file)
               || isSchemaByProvider(file)
               || hasSchemaSchema(file));
  }

  @Override
  public boolean isSchemaFile(@Nonnull JsonSchemaObject schemaObject) {
    VirtualFile file = resolveSchemaFile(schemaObject);
    return file != null && isSchemaFile(file);
  }

  private boolean isMappedSchema(@Nonnull VirtualFile file) {
    return isMappedSchema(file, true);
  }

  public boolean isMappedSchema(@Nonnull VirtualFile file, boolean canRecompute) {
    return (canRecompute || myState.isComputed()) && myState.getFiles().contains(file);
  }

  private boolean isSchemaByProvider(@Nonnull VirtualFile file) {
    JsonSchemaFileProvider provider = myState.getProvider(file);
    if (provider != null) {
      return isSchemaProvider(provider);
    }

    Map<VirtualFile, List<JsonSchemaFileProvider>> map = myState.myData.getValue();
    for (List<JsonSchemaFileProvider> providers : map.values()) {
      for (JsonSchemaFileProvider p : providers) {
        if (isSchemaProvider(p) && p.isAvailable(file)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean isSchemaProvider(JsonSchemaFileProvider provider) {
    return JsonFileResolver.isSchemaUrl(provider.getRemoteSource());
  }

  @Override
  public JsonSchemaVersion getSchemaVersion(@Nonnull VirtualFile file) {
    if (isMappedSchema(file)) {
      JsonSchemaFileProvider provider = myState.getProvider(file);
      if (provider != null) {
        return provider.getSchemaVersion();
      }
    }

    return getSchemaVersionFromSchemaUrl(file);
  }

  private @Nullable JsonSchemaVersion getSchemaVersionFromSchemaUrl(@Nonnull VirtualFile file) {
    String schemaPropertyValue;
    if (file instanceof LightVirtualFile || Registry.is("json.schema.object.v2")) {
      JsonSchemaObject schemaRootOrNull = JsonSchemaObjectStorage.getInstance(myProject).getComputedSchemaRootOrNull(file);
      if (schemaRootOrNull != null) {
        schemaPropertyValue = schemaRootOrNull.getSchema();
        return schemaPropertyValue == null ? null : JsonSchemaVersion.byId(schemaPropertyValue);
      }
    }
    Ref<String> res = Ref.create(null);
    //noinspection CodeBlock2Expr
    ApplicationManager.getApplication().runReadAction(() -> {
      res.set(JsonCachedValues.getSchemaUrlFromSchemaProperty(file, myProject));
    });
    schemaPropertyValue = res.get();
    return schemaPropertyValue == null ? null : JsonSchemaVersion.byId(schemaPropertyValue);
  }

  private boolean hasSchemaSchema(VirtualFile file) {
    return getSchemaVersionFromSchemaUrl(file) != null;
  }

  private static boolean isProviderAvailable(final @Nonnull VirtualFile file, @Nonnull JsonSchemaFileProvider provider) {
    return provider.isAvailable(file);
  }

  private @Nullable VirtualFile resolveSchemaFromOtherSources(@Nonnull VirtualFile file) {
    try {
      return myCatalogManager.getSchemaFileForFile(file);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Exception e) {
      throw new RuntimeExceptionWithAttachments("Unable to resolve JSON schema from file " + file.getName(), e,
                                                new Attachment("Schema URL", file.getUrl()));
    }
  }

  @Override
  public void registerRemoteUpdateCallback(@Nonnull Runnable callback) {
    myCatalogManager.registerCatalogUpdateCallback(callback);
  }

  @Override
  public void unregisterRemoteUpdateCallback(@Nonnull Runnable callback) {
    myCatalogManager.unregisterCatalogUpdateCallback(callback);
  }

  private final List<Runnable> myResetActions = ContainerUtil.createConcurrentList();

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
    if (index >= 0) {
      ref = ref.substring(index + 1);
    }
    myRefs.add(ref);
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
    if (file == null) return false;
    for (JsonSchemaEnabler e : JsonSchemaEnabler.EXTENSION_POINT_NAME.getExtensionList()) {
      if (e.isEnabledForFile(file, myProject)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public @Nonnull JsonSchemaCatalogManager getCatalogManager() {
    return myCatalogManager;
  }

  private static final class MyState {
    private final @Nonnull Supplier<List<JsonSchemaFileProvider>> myFactory;
    private final @Nonnull Project myProject;
    private final @Nonnull SynchronizedClearableLazy<Map<VirtualFile, List<JsonSchemaFileProvider>>> myData;

    private MyState(final @Nonnull Supplier<List<JsonSchemaFileProvider>> factory, @Nonnull Project project) {
      myFactory = factory;
      myProject = project;
      myData = new SynchronizedClearableLazy<>(() -> createFileProviderMap(myFactory.get(), myProject));
    }

    public void reset() {
      myData.drop();
    }

    public void processProviders(@Nonnull Consumer<JsonSchemaFileProvider> consumer) {
      Map<VirtualFile, List<JsonSchemaFileProvider>> map = myData.getValue();
      if (map.isEmpty()) {
        return;
      }

      for (List<JsonSchemaFileProvider> providers : map.values()) {
        providers.forEach(consumer);
      }
    }

    public @Nonnull Set<VirtualFile> getFiles() {
      return myData.getValue().keySet();
    }

    public @Nullable JsonSchemaFileProvider getProvider(@Nonnull VirtualFile file) {
      List<JsonSchemaFileProvider> providers = myData.getValue().get(file);
      if (providers == null || providers.isEmpty()) {
        return null;
      }

      for (JsonSchemaFileProvider p : providers) {
        if (p.getSchemaType() == SchemaType.userSchema) {
          return p;
        }
      }
      return providers.get(0);
    }

    public boolean isComputed() {
      return myData.isInitialized();
    }

    private static @Nonnull Map<VirtualFile, List<JsonSchemaFileProvider>> createFileProviderMap(@Nonnull List<JsonSchemaFileProvider> list,
                                                                                                 @Nonnull Project project) {
      // if there are different providers with the same schema files,
      // stream API does not allow to collect same keys with Collectors.toMap(): throws duplicate key
      Map<VirtualFile, List<JsonSchemaFileProvider>> map = new HashMap<>();
      for (JsonSchemaFileProvider provider : list) {
        VirtualFile schemaFile;
        try {
          schemaFile = getSchemaForProvider(project, provider);
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          LOG.error(e);
          continue;
        }

        if (schemaFile != null) {
          map.computeIfAbsent(schemaFile, __ -> new SmartList<>()).add(provider);
        }
      }
      return map;
    }
  }

  private static @Nullable VirtualFile getSchemaForProvider(@Nonnull Project project, @Nonnull JsonSchemaFileProvider provider) {
    if (JsonSchemaCatalogProjectConfiguration.getInstance(project).isPreferRemoteSchemas()) {
      final String source = provider.getRemoteSource();
      if (source != null && !source.endsWith("!") && !JsonFileResolver.isSchemaUrl(source)) {
        return VirtualFileManager.getInstance().findFileByUrl(source);
      }
    }
    return provider.getSchemaFile();
  }

  @Override
  public @Nullable VirtualFile resolveSchemaFile(@Nonnull JsonSchemaObject schemaObject) {
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

    private volatile List<JsonSchemaFileProvider> myProviders;

    public @Nonnull List<JsonSchemaFileProvider> getProviders() {
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

    private @Nonnull List<JsonSchemaFileProvider> getDumbAwareProvidersAndUpdateRestWhenSmart() {
      List<JsonSchemaProviderFactory> readyFactories = new ArrayList<>();
      List<JsonSchemaProviderFactory> notReadyFactories = new ArrayList<>();
      for (JsonSchemaProviderFactory factory : getProviderFactories()) {
        if (DumbService.getInstance(myProject).isUsableInCurrentContext(factory)) {
          readyFactories.add(factory);
        }
        else {
          notReadyFactories.add(factory);
        }
      }
      List<JsonSchemaFileProvider> providers = getProvidersFromFactories(readyFactories);
      myProviders = providers;
      if (!notReadyFactories.isEmpty() && !LightEdit.owns(myProject)) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
          if (myProject.isDisposed()) return;
          DumbService.getInstance(myProject).runReadActionInSmartMode(() -> {
            if (myProviders == providers) {
              List<JsonSchemaFileProvider> newProviders = getProvidersFromFactories(notReadyFactories);
              if (!newProviders.isEmpty()) {
                List<JsonSchemaFileProvider> oldProviders = myProviders;
                myProviders = ContainerUtil.concat(oldProviders, newProviders);
                JsonSchemaServiceImpl.this.resetWithCurrentFactories();
              }
            }
          });
        });
      }
      return providers;
    }

    private @Nonnull List<JsonSchemaFileProvider> getProvidersFromFactories(@Nonnull List<JsonSchemaProviderFactory> factories) {
      List<JsonSchemaFileProvider> providers = new ArrayList<>();
      for (JsonSchemaProviderFactory factory : factories) {
        try {
          providers.addAll(factory.getProviders(myProject));
        }
        catch (ProcessCanceledException e) {
          throw e;
        }
        catch (Exception e) {
          PluginException.logPluginError(Logger.getInstance(JsonSchemaService.class), e.toString(), e, factory.getClass());
        }
      }
      return providers;
    }
  }
}
