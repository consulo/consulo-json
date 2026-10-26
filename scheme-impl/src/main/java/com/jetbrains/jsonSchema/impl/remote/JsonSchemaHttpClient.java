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

import com.jetbrains.jsonSchema.remote.SchemaContent;
import com.jetbrains.jsonSchema.remote.SchemaUrl;
import consulo.annotation.component.ServiceImpl;
import consulo.application.concurrent.ApplicationConcurrency;
import consulo.http.HttpMethod;
import consulo.http.HttpRequest;
import consulo.http.HttpRequestBuilder;
import consulo.http.HttpRequestBuilderFactory;
import consulo.http.HttpVersion;
import consulo.http.localize.HttpLocalize;
import consulo.logging.Logger;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.channels.UnresolvedAddressException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Downloads remote schemas with the platform HTTP client.
 * The client uses the IDE proxy settings, the proxy credentials, and the IDE trust store.
 */
@Singleton
@ServiceImpl
public class JsonSchemaHttpClient implements SchemaHttpTransport {
    private static final Logger LOG = Logger.getInstance(JsonSchemaHttpClient.class);

    private static final int REQUEST_TIMEOUT = 10_000;
    private static final int ATTEMPTS = 5;
    private static final long RETRY_DELAY_MILLIS = 1_000;
    private static final int MAX_SCHEMA_BYTES = 16 * 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;

    private static final int READ_BUFFER_SIZE = 64 * 1024;

    private final HttpRequestBuilderFactory myRequestBuilderFactory;
    private final Executor myExecutor;
    private final int myTimeout = REQUEST_TIMEOUT;

    @Inject
    public JsonSchemaHttpClient(HttpRequestBuilderFactory requestBuilderFactory, ApplicationConcurrency concurrency) {
        myRequestBuilderFactory = requestBuilderFactory;
        myExecutor = concurrency.executor();
    }

    @Override
    public CompletableFuture<DownloadResult> download(SchemaUrl url) {
        return download(url, null, null);
    }

    @Override
    public CompletableFuture<DownloadResult> refresh(SchemaUrl url, @Nullable String etag, @Nullable String lastModified) {
        return download(url, etag, lastModified);
    }

    private CompletableFuture<DownloadResult> download(SchemaUrl url, @Nullable String etag, @Nullable String lastModified) {
        return download(url, etag, lastModified, 0, Math.max(ATTEMPTS, 1));
    }

    private CompletableFuture<DownloadResult> download(SchemaUrl url,
                                                       @Nullable String etag,
                                                       @Nullable String lastModified,
                                                       int attempt,
                                                       int attemptCount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DownloadResult result = downloadOnce(url, etag, lastModified, 0);
                if (!(result instanceof DownloadResult.Failed) || attempt == attemptCount - 1) {
                    return result;
                }
                return null;
            }
            catch (IOException | UnresolvedAddressException e) {
                if (attempt == attemptCount - 1) {
                    return new DownloadResult.Failed(e);
                }
                return null;
            }
        }, myExecutor).thenCompose(result -> {
            if (result != null) {
                return CompletableFuture.completedFuture(result);
            }
            Executor delayed = CompletableFuture.delayedExecutor(attempt * RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS, myExecutor);
            return CompletableFuture.runAsync(() -> {
            }, delayed).thenCompose(ignored -> download(url, etag, lastModified, attempt + 1, attemptCount));
        });
    }

    private DownloadResult downloadOnce(SchemaUrl url, @Nullable String etag, @Nullable String lastModified, int redirects)
        throws IOException {
        if (!isValidRemoteUrl(url)) {
            return new DownloadResult.Rejected("Only HTTP(S) URLs without user information are allowed");
        }
        HttpRequestBuilder builder = myRequestBuilderFactory.newBuilder(url.getValue(), HttpMethod.GET)
            .version(HttpVersion.HTTP_1_1)
            .connectTimeout(myTimeout)
            .readTimeout(myTimeout)
            .redirectLimit(Math.max(MAX_REDIRECTS - redirects, 0) + 1)
            .allowErrorCodes(true);
        if (redirects == 0) {
            builder.header("If-None-Match", etag);
            builder.header("If-Modified-Since", lastModified);
        }

        Response response;
        try {
            response = builder.connect(request -> handleResponse(request, url, redirects));
        }
        catch (IOException e) {
            if (HttpLocalize.errorConnectionFailedRedirects().get().equals(e.getMessage())) {
                return DownloadResult.Error.REDIRECT_LIMIT_EXCEEDED;
            }
            throw e;
        }

        SchemaUrl redirect = response.redirect();
        if (redirect != null) {
            // etag/lastModified are dropped because conditional headers are only ever valid for the original (non-redirected) request.
            return downloadOnce(redirect, null, null, redirects + 1);
        }
        return Objects.requireNonNull(response.result());
    }

    private Response handleResponse(HttpRequest request, SchemaUrl url, int redirects) throws IOException {
        SchemaUrl current = url;
        String finalUrl = request.getURL();
        if (!finalUrl.equals(url.getValue())) {
            try {
                current = SchemaUrl.parse(finalUrl);
            }
            catch (Exception e) {
                LOG.info("Unable to parse redirect url " + finalUrl, e);
                return new Response(DownloadResult.Error.INVALID_REDIRECT_TARGET, null);
            }
            if (!isValidRemoteUrl(current)) {
                return new Response(DownloadResult.Error.FORBIDDEN_REDIRECT_TARGET, null);
            }
        }

        int status = request.statusCode();
        if (status == 304) {
            return new Response(DownloadResult.NotModified.INSTANCE, null);
        }
        if (status == 200) {
            return new Response(handleSuccess(request, current), null);
        }
        if (status == 404) {
            return new Response(new DownloadResult.Error(status, statusMessage(status)), null);
        }
        if (status >= 300 && status <= 399) {
            return handleRedirect(request, current, redirects);
        }
        if (status >= 500) {
            return new Response(new DownloadResult.Failed(new IOException("Remote schema server returned HTTP " + status)), null);
        }
        if (status >= 201 && status <= 299) {
            return new Response(new DownloadResult.Failed(new IOException("Remote schema server returned unexpected HTTP " + status)), null);
        }
        return new Response(new DownloadResult.Error(status, statusMessage(status)), null);
    }

    private DownloadResult handleSuccess(HttpRequest request, SchemaUrl url) throws IOException {
        int maxSchemaBytes = Math.max(MAX_SCHEMA_BYTES, 1);
        String declaredLength = header(request, "Content-Length");
        if (declaredLength != null) {
            try {
                if (Long.parseLong(declaredLength.trim()) > maxSchemaBytes) {
                    return DownloadResult.Error.OVERSIZED_CONTENT;
                }
            }
            catch (NumberFormatException ignored) {
            }
        }
        byte[] bytes = readBody(request.getInputStream(), maxSchemaBytes);
        if (bytes == null) {
            return DownloadResult.Error.OVERSIZED_CONTENT;
        }
        return new DownloadResult.Success(
            new SchemaContent(bytes),
            header(request, "ETag"),
            header(request, "Last-Modified"),
            url.getValue()
        );
    }

    /**
     * Returns {@code null} when the body exceeds {@code maxBytes}.
     */
    private static byte @Nullable [] readBody(InputStream body, int maxBytes) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[READ_BUFFER_SIZE];
        while (true) {
            int read = body.read(buffer);
            if (read < 0) {
                return result.toByteArray();
            }
            if (result.size() + read > maxBytes) {
                return null;
            }
            result.write(buffer, 0, read);
        }
    }

    // Followed manually for the responses which the platform client does not follow, so each redirect target
    // is checked for an HTTP(S) scheme, a host, and absent user info before it is requested.
    private Response handleRedirect(HttpRequest request, SchemaUrl current, int redirects) throws IOException {
        if (redirects >= MAX_REDIRECTS) {
            return new Response(DownloadResult.Error.REDIRECT_LIMIT_EXCEEDED, null);
        }
        String location = header(request, "Location");
        if (location == null) {
            return new Response(new DownloadResult.Failed(new IOException("Redirect response has no Location header")), null);
        }
        SchemaUrl redirected;
        try {
            redirected = SchemaUrl.parse(new URI(current.getValue()).resolve(location).toString());
        }
        catch (Exception e) {
            LOG.info("Unable to parse redirect url " + location, e);
            return new Response(DownloadResult.Error.INVALID_REDIRECT_TARGET, null);
        }
        if (!isValidRemoteUrl(redirected)) {
            // Error instead of Rejected because URL is valid, results it not
            return new Response(DownloadResult.Error.FORBIDDEN_REDIRECT_TARGET, null);
        }
        return new Response(null, redirected);
    }

    private static @Nullable String header(HttpRequest request, String name) throws IOException {
        for (Map.Entry<String, List<String>> entry : request.responseHeaders().entrySet()) {
            String key = entry.getKey();
            List<String> values = entry.getValue();
            if (key != null && key.equalsIgnoreCase(name) && values != null && !values.isEmpty()) {
                return values.getFirst();
            }
        }
        return null;
    }

    /**
     * The JDK client does not expose the reason phrase of a response.
     */
    private static String statusMessage(int status) {
        return "HTTP " + status;
    }

    private static boolean isValidRemoteUrl(SchemaUrl url) {
        URI uri;
        try {
            uri = new URI(url.getValue());
        }
        catch (Exception e) {
            return false;
        }
        String scheme = uri.getScheme();
        return ("http".equals(scheme) || "https".equals(scheme)) && uri.getHost() != null && uri.getUserInfo() == null;
    }

    private record Response(@Nullable DownloadResult result, @Nullable SchemaUrl redirect) {
    }
}
