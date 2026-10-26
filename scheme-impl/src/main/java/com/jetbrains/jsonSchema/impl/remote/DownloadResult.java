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
import org.jspecify.annotations.Nullable;

/**
 * {@link Error} is a cacheable negative result (e.g., 404) for a non-retriable response or policy violation;
 * {@link Rejected} means the initial URL is invalid or unsupported and no request is sent, nothing to cache;
 * {@link Failed} is a request or response failure (e.g., 500) that may be retried and must not be cached;
 */
public sealed interface DownloadResult {
    record Success(SchemaContent content, @Nullable String etag, @Nullable String lastModified, String finalUrl) implements DownloadResult {
    }

    record Error(int code, String message) implements DownloadResult {
        public static final Error OVERSIZED_CONTENT = new Error(-1, "Remote schema exceeds the configured size limit");
        public static final Error REDIRECT_LIMIT_EXCEEDED = new Error(-2, "Remote schema redirect limit exceeded");
        public static final Error FORBIDDEN_REDIRECT_TARGET = new Error(-3, "Remote schema redirected to a forbidden URL");
        public static final Error INVALID_REDIRECT_TARGET = new Error(-4, "Invalid remote schema redirect");
    }

    final class NotModified implements DownloadResult {
        public static final NotModified INSTANCE = new NotModified();

        private NotModified() {
        }

        @Override
        public String toString() {
            return "NotModified";
        }
    }

    record Rejected(String reason) implements DownloadResult {
    }

    record Failed(Throwable cause) implements DownloadResult {
    }
}
