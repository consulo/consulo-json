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

import com.jetbrains.jsonSchema.internal.JsonFileResolver;
import com.jetbrains.jsonSchema.remote.JsonSchemaRemoteContentService;
import com.jetbrains.jsonSchema.remote.RemoteDownloadFailure;
import com.jetbrains.jsonSchema.remote.RemoteSchemaDownloadListener;
import com.jetbrains.jsonSchema.remote.SchemaContent;
import com.jetbrains.jsonSchema.remote.SchemaOrigin;
import com.jetbrains.jsonSchema.remote.SchemaUrl;
import consulo.annotation.component.ServiceImpl;
import consulo.application.concurrent.ApplicationConcurrency;
import consulo.disposer.Disposable;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.TestOnly;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Singleton
@ServiceImpl
public class JsonSchemaRemoteContentServiceImpl implements JsonSchemaRemoteContentService, Disposable {
    private static final Logger LOG = Logger.getInstance(JsonSchemaRemoteContentServiceImpl.class);

    private static final boolean REMOTE_REFS_ENABLED = true;
    private static final int MAX_CONCURRENT_DOWNLOADS = 32;
    private static final long TEST_DOWNLOAD_TIMEOUT_MS = 10_000L;

    private final Project myProject;
    private final SchemaContentCache myCache;
    private final SchemaHttpTransport myTransport;
    private final ExecutorService myExecutor;

    private final Map<SchemaUrl, CompletableFuture<?>> myInFlight = new ConcurrentHashMap<>();
    private final Map<SchemaUrl, RemoteDownloadFailure> myLastFailures = new ConcurrentHashMap<>();

    @Inject
    public JsonSchemaRemoteContentServiceImpl(Project project,
                                              SchemaContentCache cache,
                                              SchemaHttpTransport transport,
                                              ApplicationConcurrency concurrency) {
        myProject = project;
        myCache = cache;
        myTransport = transport;
        myExecutor = concurrency.createBoundedApplicationPoolExecutor("Json Schema Remote Downloads",
            concurrency.executor(),
            Math.max(MAX_CONCURRENT_DOWNLOADS, 1),
            this);
    }

    @Override
    public boolean isAllowed() {
        return JsonFileResolver.isRemoteEnabled(myProject) && REMOTE_REFS_ENABLED;
    }

    @Override
    public @Nullable SchemaContent getCached(SchemaUrl url) {
        if (!isAllowed()) {
            return null;
        }
        if (!(myCache.read(url) instanceof CachedSchema.Content cached)) {
            return null;
        }
        if (cached.isStale()) {
            prefetch(url);
        }
        return cached.content();
    }

    @Override
    public @Nullable VirtualFile getCachedFile(SchemaUrl url) {
        if (!isAllowed()) {
            return null;
        }
        CachedFile cached = myCache.lookupFile(url);
        if (cached instanceof CachedFile.Available available) {
            if (available.isStale()) {
                prefetch(url);
            }
            return available.file();
        }
        if (cached instanceof CachedFile.Unavailable) {
            return null;
        }
        prefetch(url);
        return null;
    }

    @Override
    public @Nullable VirtualFile getCachedFile(String url) {
        SchemaUrl parsed = JsonSchemaRemoteContentService.parseOrNull(url);
        return parsed == null ? null : getCachedFile(parsed);
    }

    @Override
    public String getSchemaIri(VirtualFile file) {
        String origin = file.getUserData(SchemaOrigin.URL_KEY);
        if (origin != null) {
            return origin;
        }
        String recovered = myCache instanceof JsonSchemaFileCache fileCache ? fileCache.getOriginalUrl(file) : null;
        if (recovered == null) {
            return file.getUrl();
        }
        file.putUserData(SchemaOrigin.URL_KEY, recovered);
        return recovered;
    }

    @Override
    public boolean prefetch(SchemaUrl url) {
        if (!isAllowed()) {
            return false;
        }
        if (myLastFailures.get(url) == null) {
            scheduleDownload(url, false);
        }
        return true;
    }

    @Override
    public boolean prefetch(String url) {
        SchemaUrl parsed = JsonSchemaRemoteContentService.parseOrNull(url);
        return parsed != null && prefetch(parsed);
    }

    @Override
    public void retryDownload(SchemaUrl url) {
        if (!isAllowed()) {
            return;
        }
        myLastFailures.remove(url);
        scheduleDownload(url, true);
    }

    @Override
    public void retryDownload(String url) {
        SchemaUrl parsed = JsonSchemaRemoteContentService.parseOrNull(url);
        if (parsed != null) {
            retryDownload(parsed);
        }
    }

    /**
     * @param force skips a cached error and revalidates fresh content.
     */
    private CompletableFuture<?> scheduleDownload(SchemaUrl url, boolean force) {
        CompletableFuture<Void> candidate = new CompletableFuture<>();
        CompletableFuture<?> existing = myInFlight.putIfAbsent(url, candidate);
        if (existing != null) {
            return existing;
        }
        candidate.whenComplete((result, error) -> myInFlight.remove(url, candidate));
        try {
            CompletableFuture.runAsync(() -> download(url, force), myExecutor).whenComplete((result, error) -> {
                if (error != null) {
                    candidate.completeExceptionally(error);
                }
                else {
                    candidate.complete(null);
                }
            });
        }
        catch (RuntimeException e) {
            candidate.completeExceptionally(e);
        }
        return candidate;
    }

    @TestOnly
    @Override
    public boolean hasInFlight(SchemaUrl url) {
        return myInFlight.containsKey(url);
    }

    @Override
    public boolean hasInFlight(String url) {
        SchemaUrl parsed = JsonSchemaRemoteContentService.parseOrNull(url);
        return parsed != null && myInFlight.containsKey(parsed);
    }

    @Override
    public boolean isUnavailable(SchemaUrl url) {
        return myCache.readStatus(url) instanceof CachedStatus.Unavailable;
    }

    @Override
    public boolean isUnavailable(String url) {
        SchemaUrl parsed = JsonSchemaRemoteContentService.parseOrNull(url);
        return parsed != null && isUnavailable(parsed);
    }

    @Override
    public @Nullable RemoteDownloadFailure lastFailure(SchemaUrl url) {
        return myLastFailures.get(url);
    }

    @Override
    public @Nullable RemoteDownloadFailure lastFailure(String url) {
        SchemaUrl parsed = JsonSchemaRemoteContentService.parseOrNull(url);
        return parsed == null ? null : myLastFailures.get(parsed);
    }

    @Override
    public @Nullable VirtualFile peekCachedFile(SchemaUrl url) {
        if (!isAllowed()) {
            return null;
        }
        return myCache.lookupFile(url) instanceof CachedFile.Available available ? available.file() : null;
    }

    @Override
    public @Nullable VirtualFile peekCachedFile(String url) {
        SchemaUrl parsed = JsonSchemaRemoteContentService.parseOrNull(url);
        return parsed == null ? null : peekCachedFile(parsed);
    }

    @TestOnly
    @Override
    public @Nullable VirtualFile downloadAndWaitForTest(SchemaUrl url) {
        if (!isAllowed()) {
            return null;
        }
        CachedFile cached = myCache.lookupFile(url);
        if (cached instanceof CachedFile.Available available) {
            if (available.isStale()) {
                prefetch(url);
            }
            return available.file();
        }
        if (cached instanceof CachedFile.Unavailable) {
            return null;
        }

        CompletableFuture<?> future = scheduleDownload(url, false);
        try {
            future.get(TEST_DOWNLOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
        catch (ExecutionException | TimeoutException e) {
            LOG.debug("Remote schema download did not finish for " + url.getValue(), e);
        }
        return myCache.lookupFile(url) instanceof CachedFile.Available available ? available.file() : null;
    }

    private void notifyDownloadFinished(SchemaUrl url) {
        if (myProject.isDisposed()) {
            return;
        }
        myProject.getMessageBus().syncPublisher(RemoteSchemaDownloadListener.class).downloadFinished(url.getValue());
    }

    private void download(SchemaUrl url, boolean force) {
        try {
            downloadOrThrow(url, force);
        }
        catch (CancellationException e) {
            throw e;
        }
        catch (Exception e) {
            // Nobody awaits the future, so an exception from it is lost. Without a failure, each prefetch would start a new download.
            LOG.warn("Remote schema download failed unexpectedly for " + url.getValue(), e);
            recordFailure(url);
        }
    }

    private void downloadOrThrow(SchemaUrl url, boolean force) throws Exception {
        if (!isAllowed()) {
            return;
        }
        boolean loaded = myCache.load(url);
        CachedStatus cached = myCache.readStatus(url);
        boolean isFinal;
        if (cached instanceof CachedStatus.Available available) {
            isFinal = !available.isStale() && !force;
        }
        else if (cached instanceof CachedStatus.Unavailable) {
            isFinal = !force;
        }
        else {
            isFinal = false;
        }
        if (isFinal) {
            if (loaded) {
                notifyDownloadFinished(url);
            }
            return;
        }
        if (!isAllowed()) {
            return;
        }
        DownloadResult result;
        try {
            if (cached instanceof CachedStatus.Available available) {
                result = myTransport.refresh(url, available.etag(), available.lastModified()).join();
            }
            else {
                result = myTransport.download(url).join();
            }
        }
        catch (CompletionException e) {
            Throwable cause = e.getCause();
            throw cause instanceof Exception exception ? exception : e;
        }
        if (!isAllowed()) {
            return;
        }
        switch (result) {
            case DownloadResult.Success success -> {
                myLastFailures.remove(url);
                myCache.write(url, success);
                notifyDownloadFinished(url);
            }
            case DownloadResult.Error error -> {
                myLastFailures.remove(url);
                myCache.write(url, error);
                notifyDownloadFinished(url);
            }
            case DownloadResult.NotModified notModified -> {
                myLastFailures.remove(url);
                myCache.refreshTimestamp(url);
                if (myCache.load(url)) {
                    notifyDownloadFinished(url);
                }
            }
            case DownloadResult.Failed failed -> {
                LOG.debug("Remote schema download failed for " + url.getValue(), failed.cause());
                recordFailure(url);
            }
            case DownloadResult.Rejected rejected -> {
                LOG.debug("Remote schema download rejected for " + url.getValue() + ": " + rejected.reason());
                myLastFailures.put(url, RemoteDownloadFailure.Rejected);
                notifyDownloadFinished(url);
            }
        }
    }

    private void recordFailure(SchemaUrl url) {
        myLastFailures.put(url, RemoteDownloadFailure.Failed);
        notifyDownloadFinished(url);
    }

    @Override
    public void dispose() {
        for (CompletableFuture<?> future : myInFlight.values()) {
            future.cancel(false);
        }
        myInFlight.clear();
    }
}
