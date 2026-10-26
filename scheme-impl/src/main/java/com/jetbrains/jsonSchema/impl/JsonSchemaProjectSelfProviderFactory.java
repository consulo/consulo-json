// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.JsonSchemaVersion;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.json.localize.JsonLocalize;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.List;

@ExtensionImpl
public class JsonSchemaProjectSelfProviderFactory implements JsonSchemaProviderFactory, DumbAware {
    @Override
    public List<JsonSchemaFileProvider> getProviders(Project project) {
        return List.of(
            new MyJsonSchemaFileProvider(project, new BundledJsonSchemaInfo(
                JsonSchemaVersion.SCHEMA_4,
                "schema.json",
                "4",
                "http://json-schema.org/draft-04/schema"
            )),
            new MyJsonSchemaFileProvider(project, new BundledJsonSchemaInfo(
                JsonSchemaVersion.SCHEMA_6,
                "schema06.json",
                "6",
                "http://json-schema.org/draft-06/schema"
            )),
            new MyJsonSchemaFileProvider(project, new BundledJsonSchemaInfo(
                JsonSchemaVersion.SCHEMA_7,
                "schema07.json",
                "7",
                "http://json-schema.org/draft-07/schema"
            )),
            new MyJsonSchemaFileProvider(project, new BundledJsonSchemaInfo(
                JsonSchemaVersion.SCHEMA_2019_09,
                "schema201909.json",
                "2019-09",
                "https://json-schema.org/draft/2019-09/schema"
            )),
            new MyJsonSchemaFileProvider(project, new BundledJsonSchemaInfo(
                JsonSchemaVersion.SCHEMA_2020_12,
                "schema202012.json",
                "2020-12",
                "https://json-schema.org/draft/2020-12/schema"
            ))
        );
    }

    public record BundledJsonSchemaInfo(
        JsonSchemaVersion version,
        String bundledResourceFileName,
        String presentableSchemaId,
        String remoteSourceUrl
    ) {
    }

    public static class MyJsonSchemaFileProvider implements JsonSchemaFileProvider {
        private final Project myProject;
        private final BundledJsonSchemaInfo myBundledSchema;

        public MyJsonSchemaFileProvider(Project project, BundledJsonSchemaInfo bundledSchema) {
            myProject = project;
            myBundledSchema = bundledSchema;
        }

        @Override
        public boolean isAvailable(VirtualFile file) {
            if (myProject.isDisposed()) {
                return false;
            }
            JsonSchemaService service = JsonSchemaService.get(myProject);
            if (!service.isApplicableToFile(file)) {
                return false;
            }
            JsonSchemaVersion instanceSchemaVersion = service.getSchemaVersion(file);
            if (instanceSchemaVersion == null) {
                return false;
            }
            return instanceSchemaVersion == myBundledSchema.version();
        }

        @Override
        public JsonSchemaVersion getSchemaVersion() {
            return myBundledSchema.version();
        }

        @Override
        public @Nullable VirtualFile getSchemaFile() {
            return JsonSchemaProviderFactory.getResourceFile(
                JsonSchemaProjectSelfProviderFactory.class,
                "/json-schema/" + myBundledSchema.bundledResourceFileName()
            );
        }

        @Override
        public SchemaType getSchemaType() {
            return SchemaType.schema;
        }

        @Override
        public String getRemoteSource() {
            return myBundledSchema.remoteSourceUrl();
        }

        @Override
        public String getPresentableName() {
            return JsonLocalize.schemaOfVersion(myBundledSchema.presentableSchemaId()).get();
        }

        @Override
        public String getName() {
            return getPresentableName();
        }
    }
}
