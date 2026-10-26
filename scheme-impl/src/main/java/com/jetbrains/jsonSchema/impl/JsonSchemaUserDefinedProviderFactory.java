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

import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import com.jetbrains.jsonSchema.JsonSchemaVersion;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaImportedProviderMarker;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.internal.JsonFileResolver;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.function.PairProcessor;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileSystem;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.List;
import java.util.Map;

import static com.jetbrains.jsonSchema.internal.JsonFileResolver.isAbsoluteUrl;
import static com.jetbrains.jsonSchema.internal.JsonFileResolver.isHttpPath;

@ExtensionImpl
public class JsonSchemaUserDefinedProviderFactory implements JsonSchemaProviderFactory, DumbAware {
    @Override
    public List<JsonSchemaFileProvider> getProviders(Project project) {
        JsonSchemaMappingsProjectConfiguration configuration = JsonSchemaMappingsProjectConfiguration.getInstance(project);

        Map<String, UserDefinedJsonSchemaConfiguration> map = configuration.getStateMap();
        List<JsonSchemaFileProvider> providers = ContainerUtil.map(map.values(), schema -> createProvider(project, schema));

        return providers;
    }

    public MyProvider createProvider(Project project, UserDefinedJsonSchemaConfiguration schema) {
        String relPath = schema.getRelativePathToSchema();
        return new MyProvider(
            project,
            schema.getSchemaVersion(),
            schema.getName(),
            isAbsoluteUrl(relPath) || new File(relPath).isAbsolute()
                ? relPath
                : new File(project.getBasePath(), relPath).getAbsolutePath(),
            schema.getCalculatedPatterns()
        );
    }

    static final class MyProvider implements JsonSchemaFileProvider, JsonSchemaImportedProviderMarker {
        private final Project myProject;
        private final JsonSchemaVersion myVersion;
        private final String myName;
        private final String myFile;
        private @Nullable VirtualFile myVirtualFile;
        private final List<? extends PairProcessor<Project, VirtualFile>> myPatterns;

        MyProvider(
            Project project,
            JsonSchemaVersion version,
            String name,
            String file,
            List<? extends PairProcessor<Project, VirtualFile>> patterns
        ) {
            myProject = project;
            myVersion = version;
            myName = name;
            myFile = file;
            myPatterns = patterns;
        }

        @Override
        public JsonSchemaVersion getSchemaVersion() {
            return myVersion;
        }

        @Override
        public @Nullable VirtualFile getSchemaFile() {
            VirtualFile virtualFile = myVirtualFile;
            if (virtualFile != null && virtualFile.isValid()) {
                return virtualFile;
            }
            if (isHttpPath(myFile)) {
                return null;
            }

            if (isAbsoluteUrl(myFile)) {
                virtualFile = JsonFileResolver.urlToFile(myFile);
            }
            else {
                VirtualFileSystem lfs = LocalFileSystem.getInstance();
                virtualFile = lfs.findFileByPath(myFile);
                if (virtualFile == null) {
                    virtualFile = lfs.refreshAndFindFileByPath(myFile);
                }
            }
            myVirtualFile = virtualFile;
            return virtualFile;
        }

        @Override
        public SchemaType getSchemaType() {
            return SchemaType.userSchema;
        }

        @Override
        public String getName() {
            return myName;
        }

        @Override
        public boolean isAvailable(VirtualFile file) {
            //noinspection SimplifiableIfStatement
            if (myPatterns.isEmpty() || file.isDirectory() || !file.isValid()) {
                return false;
            }
            return myPatterns.stream().anyMatch(processor -> processor.process(myProject, file));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            MyProvider provider = (MyProvider) o;

            if (!myName.equals(provider.myName)) {
                return false;
            }
            return FileUtil.pathsEqual(myFile, provider.myFile);
        }

        @Override
        public int hashCode() {
            int result = myName.hashCode();
            result = 31 * result + FileUtil.pathHashCode(myFile);
            return result;
        }

        @Override
        public @Nullable String getRemoteSource() {
            return isHttpPath(myFile) ? myFile : null;
        }
    }
}
