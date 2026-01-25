// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote;

import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration;
import consulo.application.ApplicationManager;
import consulo.application.util.registry.Registry;
import consulo.project.Project;
import consulo.util.concurrent.SameThreadExecutor;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.util.io.Url;
import consulo.util.io.Urls;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.TempFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.http.HttpVirtualFile;
import consulo.virtualFileSystem.http.RemoteFileInfo;
import consulo.virtualFileSystem.http.RemoteFileState;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Contract;
import jakarta.annotation.Nullable;

import java.io.File;
import java.util.concurrent.TimeUnit;

public final class JsonFileResolver {

  private static final Key<Boolean> DOWNLOAD_STARTED = Key.create("DOWNLOAD_STARTED");

  public static boolean isRemoteEnabled(Project project) {
    return !ApplicationManager.getApplication().isUnitTestMode() &&
           JsonSchemaCatalogProjectConfiguration.getInstance(project).isRemoteActivityEnabled();
  }

  public static @Nullable VirtualFile urlToFile(@Nonnull String urlString) {
    if (urlString.startsWith(TEMP_URL)) {
      return TempFileSystem.getInstance().findFileByPath(urlString.substring(TEMP_URL.length() - 1));
    }
    return VirtualFileManager.getInstance().findFileByUrl(FileUtil.toSystemIndependentName(replaceUnsafeSchemaStoreUrls(urlString)));
  }

  @Contract("null -> null; !null -> !null")
  public static @Nullable String replaceUnsafeSchemaStoreUrls(@Nullable String urlString) {
    if (urlString == null) return null;
    if (urlString.equals(JsonSchemaCatalogManager.DEFAULT_CATALOG)) {
      return JsonSchemaCatalogManager.DEFAULT_CATALOG_HTTPS;
    }
    if (StringUtil.startsWithIgnoreCase(urlString, JsonSchemaRemoteContentProvider.STORE_URL_PREFIX_HTTP)) {
      String newUrl = StringUtil.replace(urlString, "http://json.schemastore.org/", "https://schemastore.azurewebsites.net/schemas/json/");
      return newUrl.endsWith(".json") ? newUrl : newUrl + ".json";
    }
    return urlString;
  }

  public static @Nullable VirtualFile resolveSchemaByReference(@Nullable VirtualFile currentFile,
                                                                                  @Nullable String schemaUrl) {
    if (schemaUrl == null || StringUtil.isEmpty(schemaUrl)) return null;

    boolean isHttpPath = isHttpPath(schemaUrl);

    if (!isHttpPath && currentFile instanceof HttpVirtualFile) {
      // relative http paths
      String url = StringUtil.trimEnd(currentFile.getUrl(), "/");
      int lastSlash = url.lastIndexOf('/');
      assert lastSlash != -1;
      schemaUrl = url.substring(0, lastSlash) + "/" + schemaUrl;
    }
    else if (StringUtil.startsWithChar(schemaUrl, '.') || !isHttpPath) {
      // relative path
      VirtualFile parent = currentFile == null ? null : currentFile.getParent();
      schemaUrl = parent == null ? null :
                  parent.getUrl().startsWith(TEMP_URL) ? ("temp:///" + parent.getPath() + "/" + schemaUrl) :
                  VirtualFileUtil.pathToUrl(parent.getPath() + File.separator + schemaUrl);
    }

    if (StringUtil.isEmpty(schemaUrl)) {
      return null;
    }
    else if (!schemaUrl.startsWith("http") || !Registry.is("json.schema.object.v2")) {
      return urlToFile(schemaUrl);
    }
    else {
      return getOrComputeVirtualFileForValidUrlOrNull(schemaUrl);
    }
  }

  private static @Nullable VirtualFile getOrComputeVirtualFileForValidUrlOrNull(@Nonnull String maybeUrl) {
    return urlValidityCache.get(maybeUrl);
  }

  // Expirable cache used for cases when:
  //  - user is typing the url
  //  - url became invalid by the time
  private static final LoadingCache<String, VirtualFile> urlValidityCache =
    Caffeine.newBuilder()
      .expireAfterAccess(Registry.intValue("remote.schema.cache.validity.duration", 1), TimeUnit.MINUTES)
      .maximumSize(1000)
      .executor(SameThreadExecutor.INSTANCE)
      .build(JsonFileResolver::computeVirtualFileForValidUrlOrNull);

  private static @Nullable VirtualFile computeVirtualFileForValidUrlOrNull(@Nonnull String url) {
    Url parse = Urls.parse(url, false);
    if (parse == null || StringUtil.isEmpty(parse.getAuthority()) || StringUtil.isEmpty(parse.getPath())) return null;
    return urlToFile(url);
  }

  public static void startFetchingHttpFileIfNeeded(@Nullable VirtualFile path, Project project) {
    if (!(path instanceof HttpVirtualFile)) return;

    // don't resolve http paths in tests
    if (!isRemoteEnabled(project)) return;

    RemoteFileInfo info = ((HttpVirtualFile)path).getFileInfo();
    if (info == null || info.getState() == RemoteFileState.DOWNLOADING_NOT_STARTED) {
      if (path.getUserData(DOWNLOAD_STARTED) != Boolean.TRUE) {
        path.putUserData(DOWNLOAD_STARTED, Boolean.TRUE);
        path.refresh(true, false);
      }
    }
  }

  public static boolean isHttpPath(@Nonnull String schemaFieldText) {
    return schemaFieldText.startsWith("http://") || schemaFieldText.startsWith("https://");
  }

  public static boolean isAbsoluteUrl(@Nonnull String path) {
    return isHttpPath(path) ||
           path.startsWith(TEMP_URL) ||
           FileUtil.toSystemIndependentName(path).startsWith(JarFileSystem.PROTOCOL_PREFIX);
  }

  private static final String MOCK_URL = "mock:///";
  public static final String TEMP_URL = "temp:///";

  public static boolean isTempOrMockUrl(@Nonnull String path) {
    return path.startsWith(TEMP_URL) || path.startsWith(MOCK_URL);
  }

  public static boolean isSchemaUrl(@Nullable String url) {
    return url != null && url.startsWith("http://json-schema.org/") && (url.endsWith("/schema") || url.endsWith("/schema#"));
  }
}
