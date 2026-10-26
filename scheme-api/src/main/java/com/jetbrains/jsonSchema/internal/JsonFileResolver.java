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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.jetbrains.jsonSchema.JsonSchemaCatalogManager;
import com.jetbrains.jsonSchema.remote.JsonSchemaRemoteContentService;
import com.jetbrains.jsonSchema.remote.SchemaOrigin;
import consulo.application.Application;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.util.io.PathUtil;
import consulo.util.io.Url;
import consulo.util.io.Urls;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.TempFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.TestOnly;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class JsonFileResolver {

    private static final String STORE_URL_PREFIX_HTTP = "http://json.schemastore.org";
    private static final Logger LOG = Logger.getInstance(JsonFileResolver.class);

    private static final int REMOTE_SCHEMA_CACHE_VALIDITY_DURATION_MINUTES = 1;

    /**
     * Allows remote schema activity in unit test mode. Set it as the user data of the application.
     */
    public static final Key<Boolean> REMOTE_ENABLED_IN_TESTS = Key.create("json.schema.remote.enabled.in.tests");

    public static boolean isRemoteEnabled(Project project) {
        Application application = Application.get();
        return (!application.isUnitTestMode() || Boolean.TRUE.equals(application.getUserData(REMOTE_ENABLED_IN_TESTS))) &&
            JsonSchemaCatalogProjectConfiguration.getInstance(project).isRemoteActivityEnabled();
    }

    public static @Nullable VirtualFile urlToFile(String urlString) {
        String tempPath = tempVfsPath(urlString);
        if (tempPath != null) {
            return TempFileSystem.getInstance().findFileByPath(tempPath);
        }
        return VirtualFileManager.getInstance().findFileByUrl(PathUtil.toSystemIndependentName(replaceUnsafeSchemaStoreUrls(urlString)));
    }

    @Contract("null -> null; !null -> !null")
    public static @Nullable String replaceUnsafeSchemaStoreUrls(@Nullable String urlString) {
        if (urlString == null) {
            return null;
        }
        if (urlString.equals(JsonSchemaCatalogManager.DEFAULT_CATALOG)) {
            return JsonSchemaCatalogManager.DEFAULT_CATALOG_HTTPS;
        }
        if (StringUtil.startsWithIgnoreCase(urlString, STORE_URL_PREFIX_HTTP)) {
            String newUrl = StringUtil.replace(urlString, "http://json.schemastore.org/", "https://schemastore.azurewebsites.net/schemas/json/");
            return newUrl.endsWith(".json") ? newUrl : newUrl + ".json";
        }
        return urlString;
    }

    @TestOnly
    public static @Nullable VirtualFile resolveSchemaByReference(@Nullable VirtualFile currentFile,
                                                                 @Nullable String schemaUrl) {
        return resolveSchemaByReference(currentFile, schemaUrl, null);
    }

    public static @Nullable VirtualFile resolveSchemaByReference(@Nullable VirtualFile currentFile,
                                                                 @Nullable String schemaUrl,
                                                                 @Nullable Project project) {
        String resolvedUrl = resolveSchemaUrlByReference(currentFile, schemaUrl);
        if (resolvedUrl == null) {
            return null;
        }

        if (!resolvedUrl.startsWith("http")) {
            return urlToFile(resolvedUrl);
        }
        if (project != null) {
            return JsonSchemaRemoteContentService.getInstance(project).getCachedFile(resolvedUrl);
        }
        return getOrComputeVirtualFileForValidUrlOrNull(resolvedUrl);
    }

    public static @Nullable String resolveSchemaUrlByReference(@Nullable VirtualFile currentFile,
                                                               @Nullable String schemaUrl) {
        if (schemaUrl == null || StringUtil.isEmpty(schemaUrl)) {
            return null;
        }

        if (isAbsoluteUrl(schemaUrl)) {
            return schemaUrl;
        }

        if (currentFile == null) {
            return schemaUrl;
        }

        String originUrl = currentFile.getUserData(SchemaOrigin.URL_KEY);
        if (originUrl == null && isHttpPath(currentFile.getUrl())) {
            originUrl = currentFile.getUrl();
        }
        String resolved = originUrl != null ? resolveAgainstRemoteUrl(originUrl, schemaUrl) : resolveAgainstFile(currentFile, schemaUrl);
        return StringUtil.isEmpty(resolved) ? null : resolved;
    }

    private static @Nullable String resolveAgainstRemoteUrl(String originUrl, String reference) {
        try {
            // URI rejects a literal space, but a hand-written $ref can contain one.
            return URI.create(originUrl).resolve(StringUtil.replace(reference, " ", "%20")).normalize().toString();
        }
        catch (IllegalArgumentException e) {
            LOG.debug("Unable to resolve schema reference '" + reference + "' against origin '" + originUrl + "'", e);
            return null;
        }
    }

    /**
     * Resolves on the VFS path instead of a {@link URI}.
     * A VFS URL is not URI-encoded, so a space in a directory name breaks {@link URI#create},
     * and {@link URI#getPath} drops the Windows drive of {@code file://C:/...}.
     */
    private static String resolveAgainstFile(VirtualFile file, String reference) {
        int fragmentStart = reference.indexOf('#');
        String referencePath = fragmentStart < 0 ? reference : reference.substring(0, fragmentStart);
        String path;
        if (referencePath.isEmpty()) {
            path = file.getPath();
        }
        else if (referencePath.startsWith("/")) {
            path = FileUtil.toCanonicalPath(referencePath);
        }
        else {
            path = FileUtil.toCanonicalPath(PathUtil.getParentPath(file.getPath()) + "/" + referencePath);
        }
        String url = VirtualFileManager.constructUrl(file.getFileSystem().getProtocol(), StringUtil.notNullize(path));
        return fragmentStart < 0 ? url : url + reference.substring(fragmentStart);
    }

    private static @Nullable VirtualFile getOrComputeVirtualFileForValidUrlOrNull(String maybeUrl) {
        Optional<VirtualFile> file = urlValidityCache.getUnchecked(maybeUrl);
        if (file.isEmpty()) {
            urlValidityCache.invalidate(maybeUrl);
        }
        return file.orElse(null);
    }

    // Expirable cache used for cases when:
    //  - user is typing the url
    //  - url became invalid by the time
    private static final LoadingCache<String, Optional<VirtualFile>> urlValidityCache =
        CacheBuilder.newBuilder()
            .expireAfterAccess(REMOTE_SCHEMA_CACHE_VALIDITY_DURATION_MINUTES, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build(CacheLoader.from(url -> Optional.ofNullable(computeVirtualFileForValidUrlOrNull(url))));

    private static @Nullable VirtualFile computeVirtualFileForValidUrlOrNull(String url) {
        Url parse = Urls.parse(url, false);
        if (parse == null || StringUtil.isEmpty(parse.getAuthority()) || StringUtil.isEmpty(parse.getPath())) {
            return null;
        }
        return urlToFile(url);
    }

    public static boolean isHttpPath(String schemaFieldText) {
        return schemaFieldText.startsWith("http://") || schemaFieldText.startsWith("https://");
    }

    public static boolean isAbsoluteUrl(String path) {
        return VirtualFileManager.extractProtocol(path) != null;
    }

    private static final String MOCK_URL = "mock:///";
    public static final String TEMP_URL = "temp:///";
    private static final String TEMP_SCHEME_PREFIX = "temp:";

    private static @Nullable String tempVfsPath(String urlString) {
        if (!urlString.startsWith(TEMP_SCHEME_PREFIX)) {
            return null;
        }
        String path = urlString.substring(TEMP_SCHEME_PREFIX.length());
        int fragment = path.indexOf('#');
        if (fragment >= 0) {
            path = path.substring(0, fragment);
        }
        return "/" + StringUtil.trimStart(path, "/");
    }

    public static boolean isTempOrMockUrl(String path) {
        return path.startsWith(TEMP_URL) || path.startsWith(MOCK_URL);
    }

    public static boolean isSchemaUrl(@Nullable String url) {
        return url != null && url.startsWith("http://json-schema.org/") && (url.endsWith("/schema") || url.endsWith("/schema#"));
    }
}
