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

import com.jetbrains.jsonSchema.remote.SchemaUrl;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

@ServiceAPI(ComponentScope.APPLICATION)
public interface SchemaHttpTransport {
    CompletableFuture<DownloadResult> download(SchemaUrl url);

    default CompletableFuture<DownloadResult> refresh(SchemaUrl url, @Nullable String etag, @Nullable String lastModified) {
        return download(url);
    }
}
