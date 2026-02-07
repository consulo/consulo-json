// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.http.DefaultRemoteContentProvider;
import jakarta.annotation.Nonnull;

import java.io.File;
import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.time.Duration;

public final class JsonSchemaRemoteContentProvider extends DefaultRemoteContentProvider {
    private static final int DEFAULT_CONNECT_TIMEOUT = 10000;
    private static final long UPDATE_DELAY = Duration.ofHours(4).toMillis();
    static final String STORE_URL_PREFIX_HTTP = "http://json.schemastore.org";
    static final String STORE_URL_PREFIX_HTTPS = "https://schemastore.azurewebsites.net";
    private static final String SCHEMA_URL_PREFIX = "http://json-schema.org/";
    private static final String SCHEMA_URL_PREFIX_HTTPS = "https://json-schema.org/";
    private static final String ETAG_HEADER = "ETag";
    private static final String LAST_MODIFIED_HEADER = "Last-Modified";

    private long myLastUpdateTime = 0;

    @Override
    public boolean canProvideContent(@Nonnull String externalForm) {
        return externalForm.startsWith(STORE_URL_PREFIX_HTTP)
            || externalForm.startsWith(STORE_URL_PREFIX_HTTPS)
            || externalForm.startsWith(SCHEMA_URL_PREFIX)
            || externalForm.startsWith(SCHEMA_URL_PREFIX_HTTPS)
            || externalForm.endsWith(".json");
    }


    @Override
    public void saveContent(String url, @Nonnull File file, @Nonnull DownloadingCallback callback) {
        super.saveContent(url, file, callback);
    }

//    @Override TODO
//    protected void saveAdditionalData(@Nonnull HttpRequests.Request request, @Nonnull File file) throws IOException {
//        URLConnection connection = request.getConnection();
//        if (saveTag(file, connection, ETAG_HEADER)) {
//            return;
//        }
//        saveTag(file, connection, LAST_MODIFIED_HEADER);
//    }
//
//    @Override  TODO
//    protected @Nullable FileType adjustFileType(@Nullable FileType type, @Nonnull Url url) {
//        if (type == null) {
//            String fullUrl = url.toExternalForm();
//            if (fullUrl.startsWith(SCHEMA_URL_PREFIX) || fullUrl.startsWith(SCHEMA_URL_PREFIX_HTTPS)) {
//                // json-schema.org doesn't provide a mime-type for schemas
//                return JsonFileType.INSTANCE;
//            }
//        }
//        return super.adjustFileType(type, url);
//    }

    private static boolean saveTag(@Nonnull File file, @Nonnull URLConnection connection, @Nonnull String header) throws IOException {
        String tag = connection.getHeaderField(header);
        if (tag != null) {
            String path = file.getAbsolutePath();
            if (!path.endsWith(".json")) {
                path += ".json";
            }
            File tagFile = new File(path + "." + header);
            saveToFile(tagFile, tag);
            return true;
        }
        return false;
    }

    private static void saveToFile(@Nonnull File tagFile, @Nonnull String headerValue) throws IOException {
        if (!tagFile.exists()) {
            if (!tagFile.createNewFile()) {
                return;
            }
        }
        Files.write(tagFile.toPath(), ContainerUtil.createMaybeSingletonList(headerValue));
    }

    @Override
    public boolean isUpToDate(@Nonnull String url, @Nonnull VirtualFile local) {
        long now = System.currentTimeMillis();
        // don't update more frequently than once in 4 hours
        if (now - myLastUpdateTime < UPDATE_DELAY) {
            return true;
        }

        myLastUpdateTime = now;
        String path = local.getPath();

        if (now - new File(path).lastModified() < UPDATE_DELAY) {
            return true;
        }

// TODO
//        if (checkUpToDate(url, path, ETAG_HEADER)) {
//            return true;
//        }
//        if (checkUpToDate(url, path, LAST_MODIFIED_HEADER)) {
//            return true;
//        }

        return false;
    }
//
//    private boolean checkUpToDate(@Nonnull String url, @Nonnull String path, @Nonnull String header) {
//        File file = new File(path + "." + header);
//        try {
//            return isUpToDate(url, file, header);
//        }
//        catch (IOException e) {
//            // in case of an error, don't bother with update for the next UPDATE_DELAY milliseconds
//            //noinspection ResultOfMethodCallIgnored
//            new File(path).setLastModified(System.currentTimeMillis());
//            return true;
//        }
//    }

    protected int getDefaultConnectionTimeout() {
        return DEFAULT_CONNECT_TIMEOUT;
    }

/*    protected <T> T connect(@NotNull Url url, @NotNull RequestBuilder requestBuilder,
                            @NotNull HttpRequests.RequestProcessor<T> processor) throws IOException {
        return addRequestTuner(url, requestBuilder)
            .connectTimeout(getDefaultConnectionTimeout())
            .productNameAsUserAgent()
            .connect(processor);
    }

    public static @NotNull RequestBuilder addRequestTuner(@NotNull Url url, @NotNull RequestBuilder requestBuilder) {
        BuiltInServerManager builtInServerManager = BuiltInServerManager.getInstance();
        if (builtInServerManager.isOnBuiltInWebServer(url)) {
            requestBuilder.tuner(builtInServerManager::configureRequestToWebServer);
        }
        return requestBuilder;
    }

    private boolean isUpToDate(@Nonnull String url, @Nonnull File file, @Nonnull String header) throws IOException {
        List<String> strings = file.exists() ? Files.readAllLines(file.toPath()) : List.of();

        String currentTag = !strings.isEmpty() ? strings.get(0) : null;
        if (currentTag == null) {
            return false;
        }

        String remoteTag = connect(url, HttpRequests.head(url).,
            r -> r.getConnection().getHeaderField(header));

        return currentTag.equals(remoteTag);
    }
    */
}
