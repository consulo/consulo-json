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
package com.jetbrains.jsonSchema;

import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.json.localize.JsonLocalize;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.util.xml.serializer.annotation.Tag;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@State(name = "JsonSchemaMappingsProjectConfiguration", storages = @Storage("jsonSchemas.xml"))
public class JsonSchemaMappingsProjectConfiguration implements PersistentStateComponent<JsonSchemaMappingsProjectConfiguration.MyState> {
    private final Project myProject;
    public volatile MyState myState = new MyState();

    public @Nullable UserDefinedJsonSchemaConfiguration findMappingBySchemaInfo(JsonSchemaInfo value) {
        for (UserDefinedJsonSchemaConfiguration configuration : myState.myState.values()) {
            if (areSimilar(value, configuration)) {
                return configuration;
            }
        }
        return null;
    }

    public boolean areSimilar(JsonSchemaInfo value, UserDefinedJsonSchemaConfiguration configuration) {
        return Objects.equals(normalizePath(value.getUrl(myProject)), normalizePath(configuration.getRelativePathToSchema()));
    }

    @Contract("null -> null; !null -> !null")
    public @Nullable String normalizePath(@Nullable String valueUrl) {
        if (valueUrl == null) {
            return null;
        }
        String path = valueUrl;
        if (StringUtil.contains(path, "..")) {
            path = new File(path).getAbsolutePath();
        }
        return path.replace('\\', '/');
    }

    public @Nullable UserDefinedJsonSchemaConfiguration findMappingForFile(VirtualFile file) {
        VirtualFile projectBaseDir = getProjectBaseDir();
        for (UserDefinedJsonSchemaConfiguration configuration : myState.myState.values()) {
            for (UserDefinedJsonSchemaConfiguration.Item pattern : configuration.patterns) {
                if (pattern.mappingKind != JsonMappingKind.File) {
                    continue;
                }
                VirtualFile relativeFile = VirtualFileUtil.findRelativeFile(projectBaseDir, pattern.getPathParts());
                if (Objects.equals(relativeFile, file) ||
                    file.getUrl().equals(UserDefinedJsonSchemaConfiguration.Item.neutralizePath(pattern.getPath()))) {
                    return configuration;
                }
            }
        }
        return null;
    }

    public static JsonSchemaMappingsProjectConfiguration getInstance(Project project) {
        return project.getInstance(JsonSchemaMappingsProjectConfiguration.class);
    }

    @Inject
    public JsonSchemaMappingsProjectConfiguration(Project project) {
        myProject = project;
    }

    @Override
    public @Nullable MyState getState() {
        return myState;
    }

    public void schemaFileMoved(Project project,
                                String oldRelativePath,
                                String newRelativePath) {
        Optional<UserDefinedJsonSchemaConfiguration> old = myState.myState.values().stream()
            .filter(schema -> FileUtil.pathsEqual(schema.getRelativePathToSchema(), oldRelativePath))
            .findFirst();
        old.ifPresent(configuration -> {
            configuration.setRelativePathToSchema(newRelativePath);
            JsonSchemaService.Impl.get(project).reset();
        });
    }

    public void removeConfiguration(UserDefinedJsonSchemaConfiguration configuration) {
        for (Map.Entry<String, UserDefinedJsonSchemaConfiguration> entry : myState.myState.entrySet()) {
            if (entry.getValue() == configuration) {
                myState.myState.remove(entry.getKey());
                return;
            }
        }
    }

    public void addConfiguration(UserDefinedJsonSchemaConfiguration configuration) {
        String name = configuration.getName();
        while (myState.myState.containsKey(name)) {
            name += "1";
        }
        myState.myState.put(name, configuration);
    }

    public Map<String, UserDefinedJsonSchemaConfiguration> getStateMap() {
        return Collections.unmodifiableMap(myState.myState);
    }

    @Override
    public void loadState(MyState state) {
        myState = state;
        JsonSchemaService.Impl.get(myProject).reset();
    }

    public void setState(Map<String, UserDefinedJsonSchemaConfiguration> state) {
        myState = new MyState(state);
    }

    public static final class MyState {
        @Tag("state")
        public Map<String, UserDefinedJsonSchemaConfiguration> myState = new TreeMap<>();

        MyState() {
        }

        MyState(Map<String, UserDefinedJsonSchemaConfiguration> state) {
            myState = state;
        }
    }

    public boolean isIgnoredFile(VirtualFile virtualFile) {
        UserDefinedJsonSchemaConfiguration mappingForFile = findMappingForFile(virtualFile);
        return mappingForFile != null && mappingForFile.isIgnoredFile();
    }

    public void setSchemaForFile(@Nullable JsonSchemaInfo selectedValue, VirtualFile virtualFile) {
        VirtualFile projectBaseDir = getProjectBaseDir();

        UserDefinedJsonSchemaConfiguration mappingForFile = findMappingForFile(virtualFile);
        if (mappingForFile != null) {
            for (UserDefinedJsonSchemaConfiguration.Item pattern : mappingForFile.patterns) {
                if (Objects.equals(VirtualFileUtil.findRelativeFile(projectBaseDir, pattern.getPathParts()), virtualFile)
                    || virtualFile.getUrl().equals(UserDefinedJsonSchemaConfiguration.Item.neutralizePath(pattern.getPath()))) {
                    mappingForFile.patterns.remove(pattern);
                    if (mappingForFile.patterns.isEmpty() && mappingForFile.isApplicationDefined()) {
                        removeConfiguration(mappingForFile);
                    }
                    else {
                        mappingForFile.refreshPatterns();
                    }
                    break;
                }
            }
        }

        if (selectedValue == null) {
            return;
        }

        String path = projectBaseDir == null ? null : VirtualFileUtil.getRelativePath(virtualFile, projectBaseDir);
        if (path == null) {
            path = virtualFile.getUrl();
        }

        UserDefinedJsonSchemaConfiguration existing = findMappingBySchemaInfo(selectedValue);
        UserDefinedJsonSchemaConfiguration.Item item = new UserDefinedJsonSchemaConfiguration.Item(path, false, false);
        if (existing != null) {
            if (!existing.patterns.contains(item)) {
                existing.patterns.add(item);
                existing.refreshPatterns();
            }
        }
        else {
            addConfiguration(new UserDefinedJsonSchemaConfiguration(selectedValue.getDescription(),
                selectedValue.getSchemaVersion(),
                selectedValue.getUrl(myProject),
                true,
                Collections.singletonList(item)));
        }
    }

    private @Nullable VirtualFile getProjectBaseDir() {
        String basePath = myProject.getBasePath();
        if (basePath == null) {
            return null;
        }
        return StandardFileSystems.local().findFileByPath(FileUtil.toSystemIndependentName(basePath));
    }

    public void markAsIgnored(VirtualFile virtualFile) {
        UserDefinedJsonSchemaConfiguration existingMapping = findMappingForFile(virtualFile);
        if (existingMapping != null) {
            removeConfiguration(existingMapping);
        }
        addConfiguration(createIgnoreSchema(virtualFile.getUrl()));
    }

    public void unmarkAsIgnored(VirtualFile virtualFile) {
        if (isIgnoredFile(virtualFile)) {
            UserDefinedJsonSchemaConfiguration existingMapping = findMappingForFile(virtualFile);
            if (existingMapping != null) {
                removeConfiguration(existingMapping);
            }
        }
    }

    private static UserDefinedJsonSchemaConfiguration createIgnoreSchema(String ignoredFileUrl) {
        UserDefinedJsonSchemaConfiguration schemaConfiguration = new UserDefinedJsonSchemaConfiguration(
            JsonLocalize.schemaWidgetNoSchemaLabel().get(),
            JsonSchemaVersion.SCHEMA_4,
            "",
            true,
            Collections.singletonList(new UserDefinedJsonSchemaConfiguration.Item(ignoredFileUrl, false, false))
        );
        schemaConfiguration.setIgnoredFile(true);
        return schemaConfiguration;
    }
}
