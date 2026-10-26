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
package com.jetbrains.jsonSchema.impl;

import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.impl.remote.JsonSchemaCatalogEntryFileMatcher;
import com.jetbrains.jsonSchema.internal.JsonCachedValues;
import com.jetbrains.jsonSchema.internal.JsonSchemaCatalogEntry;
import com.jetbrains.jsonSchema.internal.JsonSchemaCatalogProjectConfiguration;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl(order = "last")
public class JsonSchemaCatalogImplicitProviderFactory implements JsonSchemaProviderFactory, DumbAware {
    private static final String IMPLICIT_CATALOG_PATH = "/json-schema/implicitSchemas.json";

    @Override
    public List<JsonSchemaFileProvider> getProviders(Project project) {
        if (!JsonSchemaCatalogProjectConfiguration.getInstance(project).isImplicitSchemasEnabled()) {
            return List.of();
        }

        VirtualFile catalogFile = JsonSchemaProviderFactory.getResourceFile(getClass(), IMPLICIT_CATALOG_PATH);
        if (catalogFile == null) {
            return List.of();
        }
        List<JsonSchemaCatalogEntry> catalogEntries = JsonCachedValues.getSchemaCatalog(catalogFile, project);
        if (catalogEntries == null) {
            return List.of();
        }
        List<JsonSchemaFileProvider> providers = new ArrayList<>();
        for (JsonSchemaCatalogEntry entry : catalogEntries) {
            if (!entry.getFileMasks().isEmpty()) {
                providers.add(new ImplicitSchemaProvider(project, entry));
            }
        }
        return providers;
    }

    private static final class ImplicitSchemaProvider implements JsonSchemaFileProvider {
        private final Project myProject;
        private final JsonSchemaCatalogEntry myEntry;
        private final JsonSchemaCatalogEntryFileMatcher myMatcher;

        private ImplicitSchemaProvider(Project project, JsonSchemaCatalogEntry entry) {
            myProject = project;
            myEntry = entry;
            myMatcher = new JsonSchemaCatalogEntryFileMatcher(entry);
        }

        @Override
        public boolean isAvailable(VirtualFile file) {
            return file.isValid() && !file.isDirectory() && myMatcher.matches(file, myProject);
        }

        @Override
        public String getName() {
            return getPresentableName();
        }

        @Override
        public @Nullable VirtualFile getSchemaFile() {
            return null;
        }

        @Override
        public SchemaType getSchemaType() {
            return SchemaType.remoteSchema;
        }

        @Override
        public boolean isUserVisible() {
            return false;
        }

        @Override
        public String getPresentableName() {
            return StringUtil.notNullize(myEntry.getName(), StringUtil.notNullize(myEntry.getDescription(), myEntry.getUrl()));
        }

        @Override
        public String getRemoteSource() {
            return myEntry.getUrl();
        }
    }
}
