// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote;

import com.jetbrains.jsonSchema.JsonSchemaCatalogEntry;
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonCachedValues;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.logging.Logger;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.impl.CollectionFactory;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.http.HttpVirtualFile;
import consulo.virtualFileSystem.http.RemoteFileInfo;
import consulo.virtualFileSystem.http.RemoteFileManager;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.nio.file.*;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.PatternSyntaxException;

public final class JsonSchemaCatalogManager {
  private static final Logger LOG = Logger.getInstance(JsonSchemaCatalogManager.class);

  static final String DEFAULT_CATALOG = "http://schemastore.org/api/json/catalog.json";
  static final String DEFAULT_CATALOG_HTTPS = "https://schemastore.org/api/json/catalog.json";
  private static final Set<String> SCHEMA_URL_PREFIXES_WITH_TOO_MANY_VARIANTS = Set.of(
    // To match changing schema URLs for azure-pipelines:
    // - https://raw.githubusercontent.com/microsoft/azure-pipelines-vscode/master/service-schema.json
    // - https://raw.githubusercontent.com/microsoft/azure-pipelines-vscode/v1.174.2/service-schema.json
    "https://raw.githubusercontent.com/microsoft/azure-pipelines-vscode/"
  );

  private final @Nonnull Project myProject;
  private final @Nonnull JsonSchemaRemoteContentProvider myRemoteContentProvider;
  private @Nullable VirtualFile myCatalog = null;
  private final @Nonnull ConcurrentMap<VirtualFile, String> myResolvedMappings = ContainerUtil.createConcurrentSoftMap();
  private static final String NO_CACHE = "$_$_WS_NO_CACHE_$_$";
  private static final String EMPTY = "$_$_WS_EMPTY_$_$";
  private VirtualFile myTestSchemaStoreFile;

  private final Map<Runnable, FileDownloadingAdapter> myDownloadingAdapters = CollectionFactory.createConcurrentWeakMap();

  public JsonSchemaCatalogManager(@Nonnull Project project) {
    myProject = project;
    myRemoteContentProvider = new JsonSchemaRemoteContentProvider();
  }

  public void startUpdates() {
    JsonSchemaCatalogProjectConfiguration.getInstance(myProject).addChangeHandler(() -> {
      update();
      JsonSchemaService.Impl.get(myProject).reset();
    });
    RemoteFileManager instance = RemoteFileManager.getInstance();
    instance.addRemoteContentProvider(myRemoteContentProvider);
    update();
  }

  private void update() {
    Application application = ApplicationManager.getApplication();
    if (application != null && application.isUnitTestMode()) {
      myCatalog = myTestSchemaStoreFile;
      return;
    }
    // ignore schema catalog when remote activity is disabled (when we're in tests, or it is off in settings)
    myCatalog = !JsonFileResolver.isRemoteEnabled(myProject) ? null : JsonFileResolver.urlToFile(DEFAULT_CATALOG);
  }

  @TestOnly
  public void registerTestSchemaStoreFile(@Nonnull VirtualFile testSchemaStoreFile, @Nonnull Disposable testDisposable) {
    myTestSchemaStoreFile = testSchemaStoreFile;
    Disposer.register(testDisposable, () -> {
      myTestSchemaStoreFile = null;
      update();
    });
    update();
  }

  public @Nullable VirtualFile getSchemaFileForFile(@Nonnull VirtualFile file) {
    if (!JsonSchemaCatalogProjectConfiguration.getInstance(myProject).isCatalogEnabled()) {
      return null;
    }

    if (JsonSchemaCatalogExclusion.EP_NAME.findFirstSafe(exclusion -> exclusion.isExcluded(file)) != null) {
      return null;
    }

    String schemaUrl = myResolvedMappings.get(file);
    if (EMPTY.equals(schemaUrl)) {
      return null;
    }
    if (schemaUrl == null && myCatalog != null) {
      schemaUrl = resolveSchemaFile(file, myCatalog, myProject);
      if (NO_CACHE.equals(schemaUrl)) return null;
      myResolvedMappings.put(file, StringUtil.notNullize(schemaUrl, EMPTY));
    }

    if (schemaUrl == null || isIgnoredAsHavingTooManyVariants(schemaUrl)) {
      return null;
    }
    return JsonFileResolver.resolveSchemaByReference(file, schemaUrl);
  }

  private static boolean isIgnoredAsHavingTooManyVariants(@Nonnull String schemaUrl) {
    return ContainerUtil.exists(SCHEMA_URL_PREFIXES_WITH_TOO_MANY_VARIANTS, prefix -> schemaUrl.startsWith(prefix));
  }

  public List<JsonSchemaCatalogEntry> getAllCatalogEntries() {
    if (myCatalog != null) {
      final List<JsonSchemaCatalogEntry> catalog = JsonCachedValues.getSchemaCatalog(myCatalog, myProject);
      return catalog == null ? ContainerUtil.emptyList() : catalog;
    }
    return ContainerUtil.emptyList();
  }

  public void registerCatalogUpdateCallback(@Nonnull Runnable callback) {
    if (myCatalog instanceof HttpVirtualFile) {
      RemoteFileInfo info = ((HttpVirtualFile)myCatalog).getFileInfo();
      if (info != null) {
        FileDownloadingAdapter adapter = new FileDownloadingAdapter() {
          @Override
          public void fileDownloaded(@Nonnull VirtualFile localFile) {
            callback.run();
          }
        };
        myDownloadingAdapters.put(callback, adapter);
        info.addDownloadingListener(adapter);
      }
    }
  }

  public void unregisterCatalogUpdateCallback(@Nonnull Runnable callback) {
    if (!myDownloadingAdapters.containsKey(callback)) return;

    if (myCatalog instanceof HttpVirtualFile) {
      RemoteFileInfo info = ((HttpVirtualFile)myCatalog).getFileInfo();
      if (info != null) {
        info.removeDownloadingListener(myDownloadingAdapters.get(callback));
      }
    }
  }

  public void triggerUpdateCatalog(Project project) {
    JsonFileResolver.startFetchingHttpFileIfNeeded(myCatalog, project);
  }

  private static @Nullable String resolveSchemaFile(@Nonnull VirtualFile file, @Nonnull VirtualFile catalogFile, @Nonnull Project project) {
    JsonFileResolver.startFetchingHttpFileIfNeeded(catalogFile, project);

    List<JsonSchemaCatalogEntry> schemaCatalog = JsonCachedValues.getSchemaCatalog(catalogFile, project);
    if (schemaCatalog == null) return catalogFile instanceof HttpVirtualFile ? NO_CACHE : null;

    List<FileMatcher> fileMatchers = ContainerUtil.map(schemaCatalog, entry -> new FileMatcher(entry));

    String fileRelativePathStr = getRelativePath(file, project);
    String url = findMatchedUrl(fileMatchers, fileRelativePathStr);
    if (url == null) {
      String fileName = file.getName();
      if (!fileName.equals(fileRelativePathStr)) {
        url = findMatchedUrl(fileMatchers, fileName);
      }
    }
    return url;
  }

  private static @Nullable String findMatchedUrl(@Nonnull List<FileMatcher> matchers, @Nullable String filePath) {
    if (filePath == null) return null;
    Path path;
    try {
      path = Paths.get(filePath);
    }
    catch (InvalidPathException e) {
      LOG.debug("Unable to process invalid path '" + filePath + "'", e);
      return null;
    }

    for (FileMatcher matcher : matchers) {
      try {
        if (matcher.matches(path)) {
          return matcher.myEntry.getUrl();
        }
      }
      catch (PatternSyntaxException pse) {
        LOG.warn("Unable to process matches for path '" + path + "' with matcher URL '" + matcher.myEntry.getUrl() + "'", pse);
      }
    }

    return null;
  }

  private static @Nullable String getRelativePath(@Nonnull VirtualFile file, @Nonnull Project project) {
    String basePath = project.getBasePath();
    if (basePath != null) {
      basePath = StringUtil.trimEnd(basePath, VfsUtilCore.VFS_SEPARATOR_CHAR) + VfsUtilCore.VFS_SEPARATOR_CHAR;
      String filePath = file.getPath();
      if (filePath.startsWith(basePath)) {
        return filePath.substring(basePath.length());
      }
    }
    VirtualFile contentRoot = ReadAction.compute(() -> {
      if (project.isDisposed() || !file.isValid()) return null;
      return ProjectFileIndex.getInstance(project).getContentRootForFile(file, false);
    });
    return contentRoot != null ? VfsUtilCore.findRelativePath(contentRoot, file, VfsUtilCore.VFS_SEPARATOR_CHAR) : null;
  }

  private static final class FileMatcher {
    private final JsonSchemaCatalogEntry myEntry;
    private PathMatcher myMatcher;

    private FileMatcher(@Nonnull JsonSchemaCatalogEntry entry) {
      myEntry = entry;
    }

    private boolean matches(@Nonnull Path filePath) {
      if (myMatcher == null) {
        myMatcher = buildPathMatcher(myEntry.getFileMasks());
      }
      return myMatcher.matches(filePath);
    }

    private static @Nonnull PathMatcher buildPathMatcher(@Nonnull Collection<String> fileMasks) {
      List<String> refinedFileMasks = ContainerUtil.map(fileMasks, fileMask -> StringUtil.trimStart(fileMask, "**/"));
      if (refinedFileMasks.size() == 1) {
        return FileSystems.getDefault().getPathMatcher("glob:" + ContainerUtil.getFirstItem(refinedFileMasks));
      }
      if (!refinedFileMasks.isEmpty()) {
        return FileSystems.getDefault().getPathMatcher("glob:{" + StringUtil.join(refinedFileMasks, ",") + "}");
      }
      return path -> false;
    }
  }
}
