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
package com.jetbrains.jsonSchema.remote;

import com.jetbrains.jsonSchema.internal.JsonFileResolver;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

public final class SchemaUrl {
    private final String myValue;

    private SchemaUrl(String value) {
        myValue = value;
    }

    public String getValue() {
        return myValue;
    }

    public static SchemaUrl parse(String value) throws URISyntaxException {
        URI uri = new URI(Objects.requireNonNull(JsonFileResolver.replaceUnsafeSchemaStoreUrls(value))).normalize();
        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
        int port;
        if ("http".equals(scheme) && uri.getPort() == 80) {
            port = -1;
        }
        else if ("https".equals(scheme) && uri.getPort() == 443) {
            port = -1;
        }
        else {
            port = uri.getPort();
        }
        String normalized;
        if (host != null) {
            StringBuilder builder = new StringBuilder();
            builder.append(scheme).append("://");
            String rawUserInfo = uri.getRawUserInfo();
            if (rawUserInfo != null) {
                builder.append(rawUserInfo).append('@');
            }
            builder.append(host);
            if (port != -1) {
                builder.append(':').append(port);
            }
            String rawPath = uri.getRawPath();
            builder.append(rawPath == null || rawPath.isEmpty() ? "/" : rawPath);
            String rawQuery = uri.getRawQuery();
            if (rawQuery != null) {
                builder.append('?').append(rawQuery);
            }
            normalized = builder.toString();
        }
        else {
            normalized = scheme == null ? uri.getRawSchemeSpecificPart() : scheme + ":" + uri.getRawSchemeSpecificPart();
        }
        return new SchemaUrl(new URI(normalized).toASCIIString());
    }

    @Override
    public boolean equals(@Nullable Object o) {
        return this == o || o instanceof SchemaUrl other && myValue.equals(other.myValue);
    }

    @Override
    public int hashCode() {
        return myValue.hashCode();
    }

    @Override
    public String toString() {
        return "SchemaUrl(value=" + myValue + ")";
    }
}
