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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.jetbrains.jsonSchema.remote.SchemaContent;
import com.jetbrains.jsonSchema.remote.SchemaOrigin;
import com.jetbrains.jsonSchema.remote.SchemaUrl;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.container.boot.ContainerPathManager;
import consulo.logging.Logger;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.TestOnly;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * NIO store for downloaded remote schemas ({@code json-schema-cache/<hash>.json} + {@code .meta}), with an in-memory layer in front of it.
 * <p>
 * The read side ({@link #read}, {@link #readStatus}, {@link #lookupFile}) uses the memory only. It takes no lock, does no IO, and is safe under
 * the read lock. A memory miss is the same as a schema that is not downloaded: the caller schedules a download, and the
 * download job calls {@link #load} first.
 * <p>
 * The write side ({@link #load}, {@link #write}, {@link #refreshTimestamp}) does the disk IO, the VFS adoption and the memory update under the
 * stripe of the URL. It must run without read access. The readers never take a stripe, so a sync VFS refresh under a stripe
 * cannot deadlock with a reader that holds the read lock.
 * <p>
 * The schema bytes come from the adopted {@link VirtualFile}, so the VFS content cache holds them. An entry without an adopted
 * file is a miss for {@link #read} and {@link #lookupFile}.
 */
@Singleton
@ServiceImpl
public class JsonSchemaFileCache implements SchemaContentCache {
    private static final Logger LOG = Logger.getInstance(JsonSchemaFileCache.class);

    private static final int CACHE_STALE_HOURS = 4;
    private static final int CACHE_ERROR_HOURS = 4;

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path myRoot = cacheRoot();
    private final StripedMutex myLocks = new StripedMutex(32);
    private final Map<SchemaUrl, Entry> myEntries = new ConcurrentHashMap<>();

    @Inject
    public JsonSchemaFileCache() {
    }

    @Override
    public @Nullable CachedSchema read(SchemaUrl url) {
        Entry entry = liveEntry(url);
        if (entry instanceof Entry.Available available) {
            VirtualFile file = available.file();
            if (file == null || !file.isValid()) {
                return null;
            }
            try {
                return new CachedSchema.Content(new SchemaContent(file.contentsToByteArray()),
                    available.metadata().etag(),
                    available.metadata().lastModified(),
                    isStale(available));
            }
            catch (IOException e) {
                LOG.debug("Unable to read the cached remote schema " + file.getPath(), e);
                return null;
            }
        }
        if (entry instanceof Entry.Unavailable unavailable) {
            return unavailable.schema();
        }
        return null;
    }

    @Override
    public @Nullable CachedStatus readStatus(SchemaUrl url) {
        Entry entry = liveEntry(url);
        if (entry instanceof Entry.Available available) {
            return available.status(isStale(available));
        }
        if (entry instanceof Entry.Unavailable unavailable) {
            return unavailable.status();
        }
        return null;
    }

    @Override
    public @Nullable CachedFile lookupFile(SchemaUrl url) {
        Entry entry = liveEntry(url);
        if (entry instanceof Entry.Available available) {
            VirtualFile file = available.file();
            return file != null && file.isValid() ? available.cachedFile(isStale(available)) : null;
        }
        if (entry instanceof Entry.Unavailable unavailable) {
            return unavailable.cachedFile();
        }
        return null;
    }

    @Override
    public boolean load(SchemaUrl url) {
        assertNoReadAccess();
        return Boolean.TRUE.equals(withLock(url, () -> {
            Entry previous = myEntries.get(url);
            Entry entry = loadFromDisk(url);
            if (entry == null) {
                myEntries.remove(url);
            }
            else {
                myEntries.put(url, entry);
            }
            return !Objects.equals(entry, previous);
        }));
    }

    @Override
    public void write(SchemaUrl url, DownloadResult result) {
        assertNoReadAccess();
        withLock(url, () -> {
            CacheMetadata.Status status;
            if (result instanceof DownloadResult.Success) {
                status = CacheMetadata.Ok.INSTANCE;
            }
            else if (result instanceof DownloadResult.Error error) {
                status = new CacheMetadata.Error(error.code(), error.message());
            }
            else {
                return null;
            }
            DownloadResult.Success success = result instanceof DownloadResult.Success s ? s : null;
            Files.createDirectories(myRoot);
            String key = digestKey(url);
            CachePaths paths = paths(url, success != null ? success.finalUrl() : null);
            for (String extension : List.of("json", "yaml")) {
                Path stale = myRoot.resolve(key + "." + extension);
                if (!stale.equals(paths.content())) {
                    Files.deleteIfExists(stale);
                }
            }
            CacheMetadata metadata = new CacheMetadata(
                url.getValue(),
                status,
                System.currentTimeMillis(),
                success != null ? success.etag() : null,
                success != null ? success.lastModified() : null,
                success != null ? success.finalUrl() : null
            );
            if (success != null) {
                writeAtomically(paths.content(), success.content().getBytes());
            }
            else {
                Files.deleteIfExists(paths.content());
            }
            writeAtomically(paths.metadata(), encode(metadata));
            if (status instanceof CacheMetadata.Error error) {
                myEntries.put(url, new Entry.Unavailable(metadata, error.code(), error.message()));
            }
            else {
                myEntries.put(url, new Entry.Available(metadata,
                    paths.content(),
                    adopt(paths.content(), metadata.url(), Objects.requireNonNull(metadata.finalUrl()), true)));
            }
            return null;
        });
    }

    public @Nullable String getOriginalUrl(VirtualFile file) {
        Path path;
        try {
            path = Path.of(file.getPath());
        }
        catch (Exception e) {
            LOG.debug("Unable to convert cached schema path " + file.getPath() + " to a local path", e);
            return null;
        }
        if (!myRoot.equals(path.getParent())) {
            return null;
        }
        for (Entry entry : myEntries.values()) {
            if (entry instanceof Entry.Available available && file.equals(available.file())) {
                String finalUrl = available.metadata().finalUrl();
                return finalUrl != null ? finalUrl : available.metadata().url();
            }
        }
        assertNoReadAccess();
        String fileName = path.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        Path metadata = myRoot.resolve((dot < 0 ? fileName : fileName.substring(0, dot)) + ".meta");
        if (!Files.isRegularFile(metadata)) {
            return null;
        }
        try {
            CacheMetadata parsed = decode(Files.readString(metadata, StandardCharsets.UTF_8));
            return parsed.finalUrl() != null ? parsed.finalUrl() : parsed.url();
        }
        catch (Exception e) {
            LOG.debug("Unable to recover the origin URL for " + file.getUrl(), e);
            return null;
        }
    }

    @Override
    public void refreshTimestamp(SchemaUrl url) {
        assertNoReadAccess();
        withLock(url, () -> {
            CachePaths rejectedPaths = paths(url);
            if (!Files.isRegularFile(rejectedPaths.metadata())) {
                return null;
            }
            CacheMetadata metadata;
            try {
                metadata = decode(Files.readString(rejectedPaths.metadata(), StandardCharsets.UTF_8));
            }
            catch (Exception e) {
                LOG.debug("Discarding corrupt remote schema cache entry for " + url.getValue(), e);
                reject(rejectedPaths);
                myEntries.remove(url);
                return null;
            }
            CachePaths paths = paths(url, metadata.finalUrl());
            if (!Files.isRegularFile(paths.content()) || !metadata.url().equals(url.getValue()) || metadata.status() != CacheMetadata.Ok.INSTANCE) {
                return null;
            }
            CacheMetadata refreshed = metadata.withTimestamp(System.currentTimeMillis());
            writeAtomically(paths.metadata(), encode(refreshed));
            myEntries.computeIfPresent(url, (u, entry) -> {
                if (entry instanceof Entry.Available available && available.content().equals(paths.content())) {
                    return available.withMetadata(refreshed);
                }
                return entry;
            });
            return null;
        });
    }

    /**
     * The URL that served the cached content of {@code url}. Uses the memory only.
     */
    public @Nullable String getRetrievalUrl(SchemaUrl url) {
        Entry entry = myEntries.get(url);
        if (entry == null) {
            return null;
        }
        String finalUrl = entry.metadata().finalUrl();
        return finalUrl != null ? finalUrl : entry.metadata().url();
    }

    /**
     * Drops the in-memory layer, so that only {@link #load} makes the disk entries visible again.
     */
    @TestOnly
    public void clearMemoryForTest() {
        myEntries.clear();
    }

    @TestOnly
    public static Path rootForTest() {
        return cacheRoot();
    }

    private @Nullable Entry liveEntry(SchemaUrl url) {
        Entry entry = myEntries.get(url);
        if (entry == null) {
            return null;
        }
        return entry instanceof Entry.Unavailable && age(entry.metadata()).compareTo(errorTtl()) > 0 ? null : entry;
    }

    private boolean isStale(Entry.Available entry) {
        return age(entry.metadata()).compareTo(contentTtl()) > 0;
    }

    private static Duration age(CacheMetadata metadata) {
        return Duration.ofMillis(System.currentTimeMillis() - metadata.timestamp());
    }

    /**
     * Reads and validates the disk entry of {@code url}. Deletes an invalid entry.
     */
    private @Nullable Entry loadFromDisk(SchemaUrl url) throws IOException {
        CacheMetadata metadata = readMetadata(url);
        if (metadata == null) {
            return rejectIfMetadataPresent(paths(url));
        }
        CachePaths paths = paths(url, metadata.finalUrl());
        if (!metadata.url().equals(url.getValue())) {
            return reject(paths);
        }
        if (metadata.status() instanceof CacheMetadata.Error error) {
            return age(metadata).compareTo(errorTtl()) > 0 ? reject(paths) : new Entry.Unavailable(metadata, error.code(), error.message());
        }
        String finalUrl = metadata.finalUrl();
        if (finalUrl == null || !Files.isRegularFile(paths.content())) {
            return reject(paths);
        }
        return new Entry.Available(metadata, paths.content(), adopt(paths.content(), metadata.url(), finalUrl, false));
    }

    /**
     * Makes {@code content} known to the VFS and loads its bytes into the VFS content cache.
     *
     * @param reloadContent refreshes a file that the VFS already knows, because {@code refreshAndFind} does not reload its content.
     */
    private @Nullable VirtualFile adopt(Path content, String requestUrl, String retrievalUrl, boolean reloadContent) {
        assertNoReadAccess();
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(content);
        if (file == null) {
            return null;
        }
        if (reloadContent) {
            VirtualFileUtil.markDirtyAndRefresh(false, false, false, file);
        }
        file.putUserData(SchemaOrigin.URL_KEY, retrievalUrl);
        file.putUserData(SchemaOrigin.REQUEST_URL_KEY, requestUrl);
        try {
            file.contentsToByteArray();
            return file;
        }
        catch (IOException e) {
            LOG.debug("Unable to load the cached remote schema " + content + " into the VFS", e);
            return null;
        }
    }

    private @Nullable CacheMetadata readMetadata(SchemaUrl url) {
        assertNoReadAccess();
        Path metadata = myRoot.resolve(digestKey(url) + ".meta");
        if (!Files.isRegularFile(metadata)) {
            return null;
        }
        try {
            return decode(Files.readString(metadata, StandardCharsets.UTF_8));
        }
        catch (Exception e) {
            LOG.debug("Corrupt remote schema cache metadata for " + url.getValue(), e);
            return null;
        }
    }

    private @Nullable Entry reject(CachePaths paths) throws IOException {
        assertNoReadAccess();
        String key = StringUtil.trimEnd(paths.metadata().getFileName().toString(), ".meta");
        Files.deleteIfExists(myRoot.resolve(key + ".json"));
        Files.deleteIfExists(myRoot.resolve(key + ".yaml"));
        Files.deleteIfExists(paths.metadata());
        return null;
    }

    private @Nullable Entry rejectIfMetadataPresent(CachePaths paths) throws IOException {
        assertNoReadAccess();
        if (Files.isRegularFile(paths.metadata())) {
            reject(paths);
        }
        return null;
    }

    private CachePaths paths(SchemaUrl url) {
        return paths(url, null);
    }

    private CachePaths paths(SchemaUrl url, @Nullable String retrievalUrl) {
        String key = digestKey(url);
        return new CachePaths(myRoot.resolve(key + "." + contentExtension(retrievalUrl != null ? retrievalUrl : url.getValue())),
            myRoot.resolve(key + ".meta"));
    }

    private static String digestKey(SchemaUrl url) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(url.getValue().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String contentExtension(String url) {
        String fileName;
        try {
            String path = new URI(url).getPath();
            fileName = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        }
        catch (Exception e) {
            LOG.debug("Unable to infer the cached schema extension from " + url, e);
            fileName = "";
        }
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "yaml", "yml" -> "yaml";
            default -> "json";
        };
    }

    private static void writeAtomically(Path target, byte[] content) throws IOException {
        assertNoReadAccess();
        Path temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try {
            Files.write(temp, content);
            atomicReplace(temp, target);
        }
        finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void atomicReplace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, REPLACE_EXISTING);
        }
    }

    private static Duration contentTtl() {
        return Duration.ofHours(CACHE_STALE_HOURS);
    }

    private static Duration errorTtl() {
        return Duration.ofHours(CACHE_ERROR_HOURS);
    }

    private static void assertNoReadAccess() {
        LOG.assertTrue(!Application.get().isReadAccessAllowed(), "Must not be called under read access");
    }

    private <T> @Nullable T withLock(SchemaUrl url, IOSupplier<T> action) {
        ReentrantLock lock = myLocks.getLock(url);
        lock.lock();
        try {
            return action.get();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        finally {
            lock.unlock();
        }
    }

    private static Path cacheRoot() {
        return ContainerPathManager.get().getSystemDir().resolve("json-schema-cache");
    }

    private static byte[] encode(CacheMetadata metadata) throws IOException {
        ObjectNode node = JSON.createObjectNode();
        node.put("url", metadata.url());
        ObjectNode status = node.putObject("status");
        if (metadata.status() instanceof CacheMetadata.Error error) {
            status.put("type", "Error");
            status.put("code", error.code());
            status.put("message", error.message());
        }
        else {
            status.put("type", "OK");
        }
        node.put("timestamp", metadata.timestamp());
        putNullable(node, "etag", metadata.etag());
        putNullable(node, "lastModified", metadata.lastModified());
        putNullable(node, "finalUrl", metadata.finalUrl());
        return JSON.writeValueAsBytes(node);
    }

    private static void putNullable(ObjectNode node, String name, @Nullable String value) {
        if (value != null) {
            node.put(name, value);
        }
    }

    private static CacheMetadata decode(String text) throws IOException {
        JsonNode node = JSON.readTree(text);
        JsonNode status = required(node, "status");
        CacheMetadata.Status parsedStatus = switch (required(status, "type").asString()) {
            case "OK" -> CacheMetadata.Ok.INSTANCE;
            case "Error" -> new CacheMetadata.Error(required(status, "code").asInt(), required(status, "message").asString());
            default -> throw new IOException("Unknown cache status " + status);
        };
        return new CacheMetadata(
            required(node, "url").asString(),
            parsedStatus,
            required(node, "timestamp").asLong(),
            optionalText(node, "etag"),
            optionalText(node, "lastModified"),
            optionalText(node, "finalUrl")
        );
    }

    private static JsonNode required(JsonNode node, String name) throws IOException {
        JsonNode value = node.get(name);
        if (value == null || value.isNull()) {
            throw new IOException("Missing field " + name);
        }
        return value;
    }

    private static @Nullable String optionalText(JsonNode node, String name) {
        JsonNode value = node.get(name);
        return value == null || value.isNull() ? null : value.asString();
    }

    @FunctionalInterface
    private interface IOSupplier<T> {
        @Nullable T get() throws IOException;
    }

    private record CachePaths(Path content, Path metadata) {
    }

    /**
     * An immutable memory entry. It keeps the ready results, so a lookup only compares the age with the TTL.
     */
    private sealed interface Entry {
        CacheMetadata metadata();

        final class Available implements Entry {
            private final CacheMetadata myMetadata;
            private final Path myContent;
            private final @Nullable VirtualFile myFile;
            private final CachedStatus myFreshStatus;
            private final CachedStatus myStaleStatus;
            private final @Nullable CachedFile myFreshFile;
            private final @Nullable CachedFile myStaleFile;

            Available(CacheMetadata metadata, Path content, @Nullable VirtualFile file) {
                myMetadata = metadata;
                myContent = content;
                myFile = file;
                myFreshStatus = new CachedStatus.Available(false, metadata.etag(), metadata.lastModified());
                myStaleStatus = new CachedStatus.Available(true, metadata.etag(), metadata.lastModified());
                myFreshFile = file == null ? null : new CachedFile.Available(file, false);
                myStaleFile = file == null ? null : new CachedFile.Available(file, true);
            }

            @Override
            public CacheMetadata metadata() {
                return myMetadata;
            }

            Path content() {
                return myContent;
            }

            @Nullable VirtualFile file() {
                return myFile;
            }

            CachedStatus status(boolean isStale) {
                return isStale ? myStaleStatus : myFreshStatus;
            }

            @Nullable CachedFile cachedFile(boolean isStale) {
                return isStale ? myStaleFile : myFreshFile;
            }

            Available withMetadata(CacheMetadata metadata) {
                return new Available(metadata, myContent, myFile);
            }

            @Override
            public boolean equals(@Nullable Object o) {
                return this == o || o instanceof Available other
                    && myMetadata.equals(other.myMetadata)
                    && myContent.equals(other.myContent)
                    && Objects.equals(myFile, other.myFile);
            }

            @Override
            public int hashCode() {
                return Objects.hash(myMetadata, myContent, myFile);
            }
        }

        final class Unavailable implements Entry {
            private final CacheMetadata myMetadata;
            private final int myCode;
            private final String myMessage;
            private final CachedStatus myStatus;
            private final CachedFile myCachedFile;
            private final CachedSchema mySchema;

            Unavailable(CacheMetadata metadata, int code, String message) {
                myMetadata = metadata;
                myCode = code;
                myMessage = message;
                myStatus = new CachedStatus.Unavailable(code, message);
                myCachedFile = new CachedFile.Unavailable(code, message);
                mySchema = new CachedSchema.Unavailable(code, message);
            }

            @Override
            public CacheMetadata metadata() {
                return myMetadata;
            }

            CachedStatus status() {
                return myStatus;
            }

            CachedFile cachedFile() {
                return myCachedFile;
            }

            CachedSchema schema() {
                return mySchema;
            }

            @Override
            public boolean equals(@Nullable Object o) {
                return this == o || o instanceof Unavailable other
                    && myMetadata.equals(other.myMetadata)
                    && myCode == other.myCode
                    && myMessage.equals(other.myMessage);
            }

            @Override
            public int hashCode() {
                return Objects.hash(myMetadata, myCode, myMessage);
            }
        }
    }

    private record CacheMetadata(String url,
                                 Status status,
                                 long timestamp,
                                 @Nullable String etag,
                                 @Nullable String lastModified,
                                 @Nullable String finalUrl) {
        CacheMetadata withTimestamp(long newTimestamp) {
            return new CacheMetadata(url, status, newTimestamp, etag, lastModified, finalUrl);
        }

        private sealed interface Status {
        }

        private enum Ok implements Status {
            INSTANCE
        }

        private record Error(int code, String message) implements Status {
        }
    }

    private static final class StripedMutex {
        private final ReentrantLock[] myLocks;
        private final int myMask;

        StripedMutex(int stripeCount) {
            if (stripeCount <= 0) {
                throw new IllegalArgumentException("Stripe count must be positive");
            }
            myMask = stripeCount - 1;
            if ((stripeCount & myMask) != 0) {
                throw new IllegalArgumentException("Stripe count must be a power of two");
            }
            myLocks = new ReentrantLock[stripeCount];
            for (int i = 0; i < stripeCount; i++) {
                myLocks[i] = new ReentrantLock();
            }
        }

        ReentrantLock getLock(SchemaUrl url) {
            return myLocks[url.getValue().hashCode() & Integer.MAX_VALUE & myMask];
        }
    }
}
