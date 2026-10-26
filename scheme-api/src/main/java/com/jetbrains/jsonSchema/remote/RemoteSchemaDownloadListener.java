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

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.TopicAPI;
import consulo.annotation.component.TopicBroadcastDirection;

/**
 * Receives the download events of {@link JsonSchemaRemoteContentService} on the project message bus.
 */
@FunctionalInterface
@TopicAPI(value = ComponentScope.PROJECT, direction = TopicBroadcastDirection.NONE)
public interface RemoteSchemaDownloadListener {
    /**
     * Runs after a download ends and changes the cached state of {@code url} or its {@link JsonSchemaRemoteContentService#lastFailure}.
     *
     * @param url the value of the normalized {@link SchemaUrl}.
     */
    void downloadFinished(String url);
}
