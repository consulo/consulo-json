// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema.impl.light;

import com.intellij.json.JsonFileType;
import com.jetbrains.jsonSchema.internal.JsonFileResolver;
import consulo.language.file.light.LightVirtualFile;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.util.lang.lazy.LazyValue;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;

public abstract sealed class StandardJsonSchemaVocabulary permits StandardJsonSchemaVocabulary.Bundled, StandardJsonSchemaVocabulary.Remote {
    private final String myId;

    protected StandardJsonSchemaVocabulary(String id) {
        myId = id;
    }

    public String getId() {
        return myId;
    }

    public abstract @Nullable VirtualFile load(Project project);

    public static final class Bundled extends StandardJsonSchemaVocabulary {
        private static final Logger LOG = Logger.getInstance(Bundled.class);

        private final String myRemoteUrl;
        private final String myResourcePath;
        private final Supplier<@Nullable VirtualFile> myLoadedVocabularyFile = LazyValue.nullable(() -> {
            try {
                return loadBundledSchemaUnsafe();
            }
            catch (IOException exception) {
                LOG.warn("Unable to load bundled schema for vocabulary", exception);
                return null;
            }
        });

        public Bundled(String id, String remoteUrl, String resourcePath) {
            super(id);
            myRemoteUrl = remoteUrl;
            myResourcePath = resourcePath;
        }

        public String getRemoteUrl() {
            return myRemoteUrl;
        }

        public String getResourcePath() {
            return myResourcePath;
        }

        @Override
        public @Nullable VirtualFile load(Project project) {
            return myLoadedVocabularyFile.get();
        }

        private @Nullable LightVirtualFile loadBundledSchemaUnsafe() throws IOException {
            InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(myResourcePath);
            if (resourceStream == null) {
                return null;
            }
            String schemaText = FileUtil.loadTextAndClose(resourceStream);
            String schemaName = myResourcePath.substring(myResourcePath.lastIndexOf('/') + 1) + ".json";
            return new LightVirtualFile(schemaName, JsonFileType.INSTANCE, schemaText);
        }
    }

    public static final class Remote extends StandardJsonSchemaVocabulary {
        private final String myUrl;

        public Remote(String id, String url) {
            super(id);
            myUrl = url;
        }

        public String getUrl() {
            return myUrl;
        }

        @Override
        public @Nullable VirtualFile load(Project project) {
            return JsonFileResolver.urlToFile(myUrl);
        }
    }
}
