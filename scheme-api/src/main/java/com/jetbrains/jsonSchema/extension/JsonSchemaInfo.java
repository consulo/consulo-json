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
package com.jetbrains.jsonSchema.extension;

import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.JsonSchemaVersion;
import com.jetbrains.jsonSchema.internal.JsonSchemaVfsUtil;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveFileSystem;
import consulo.virtualFileSystem.http.HttpVirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

public class JsonSchemaInfo {
    private final @Nullable JsonSchemaFileProvider myProvider;
    private final @Nullable String myUrl;
    private @Nullable
    String myName = null;
    private @Nullable String myDocumentation = null;
    private static final Set<String> myDumbNames = Set.of(
        "schema",
        "lib",
        "cli",
        "packages",
        "master",
        "format",
        "angular", // the only angular-related schema is the 'angular-cli', so we skip the repo name
        "config");

    // Strange cases such as meaningless 'config' as a name, etc.
    private static final Map<String, String> myWeirdNames = Map.of(
        "http://json.schemastore.org/config", "asp.net config",
        "https://schemastore.azurewebsites.net/schemas/json/config.json", "asp.net config",
        "http://json.schemastore.org/2.0.0-csd.2.beta.2018-10-10.json", "sarif-2.0.0-csd.2.beta.2018-10-10",
        "https://schemastore.azurewebsites.net/schemas/json/2.0.0-csd.2.beta.2018-10-10.json", "sarif-2.0.0-csd.2.beta.2018-10-10"
    );

    public JsonSchemaInfo(JsonSchemaFileProvider provider) {
        myProvider = provider;
        myUrl = null;
    }

    public JsonSchemaInfo(String url) {
        myUrl = url;
        myProvider = null;
    }

    public @Nullable JsonSchemaFileProvider getProvider() {
        return myProvider;
    }

    public String getUrl(Project project) {
        if (myProvider != null) {
            String remoteSource = myProvider.getRemoteSource();
            if (remoteSource != null) {
                return remoteSource;
            }

            VirtualFile schemaFile = myProvider.getSchemaFile();
            if (schemaFile == null) {
                return "";
            }

            if (schemaFile instanceof HttpVirtualFile) {
                return schemaFile.getUrl();
            }

            if (schemaFile.getFileSystem() instanceof ArchiveFileSystem) {
                return schemaFile.getUrl();
            }

            return getRelativePath(project, schemaFile.getPath());
        }
        else {
            assert myUrl != null;
            return myUrl;
        }
    }

    public String getDescription() {
        if (myProvider != null) {
            String providerName = myProvider.getPresentableName();
            return sanitizeName(providerName);
        }

        if (getName() != null) {
            return getName();
        }

        assert myUrl != null;

        if (myWeirdNames.containsKey(myUrl)) {
            return myWeirdNames.get(myUrl);
        }

        String url = myUrl.replace('\\', '/');

        return ContainerUtil.reverse(StringUtil.split(url, "/"))
            .stream()
            .filter(p -> !isVeryDumbName(p))
            .findFirst().orElse(myUrl);
    }

    public @Nullable String getDocumentation() {
        return myDocumentation;
    }

    public void setDocumentation(@Nullable String documentation) {
        myDocumentation = documentation;
    }

    public @Nullable String getName() {
        return myName;
    }

    public void setName(@Nullable String name) {
        myName = name;
    }

    public static boolean isVeryDumbName(@Nullable String possibleName) {
        if (StringUtil.isEmptyOrSpaces(possibleName) || myDumbNames.contains(possibleName)) {
            return true;
        }
        return StringUtil.split(possibleName, ".").stream().allMatch(s -> JsonSchemaType.isInteger(s));
    }

    private static String sanitizeName(String providerName) {
        return StringUtil.trimEnd(StringUtil.trimEnd(StringUtil.trimEnd(providerName, ".json"), "-schema"), ".schema");
    }

    public JsonSchemaVersion getSchemaVersion() {
        return myProvider != null ? myProvider.getSchemaVersion() : JsonSchemaVersion.SCHEMA_4;
    }

    public static String getRelativePath(Project project, String text) {
        text = text.trim();
        if (project.isDefault() || project.getBasePath() == null || StringUtil.isEmptyOrSpaces(text)) {
            return text;
        }

        Path file = Paths.get(text);
        if (!file.isAbsolute()) {
            return text;
        }

        String relativePath = FileUtil.getRelativePath(project.getBasePath(), FileUtil.toSystemIndependentName(file.toString()), '/');
        if (relativePath != null) {
            return relativePath;
        }

        VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByNioFile(file);
        if (virtualFile == null) {
            return text;
        }

        VirtualFile projectBaseDir = LocalFileSystem.getInstance().findFileByPath(project.getBasePath());
        if (projectBaseDir == null) {
            return text;
        }

        if (isMeaningfulAncestor(VirtualFileUtil.getCommonAncestor(virtualFile, projectBaseDir))) {
            String path = JsonSchemaVfsUtil.findRelativePath(projectBaseDir, virtualFile, File.separatorChar);
            if (path != null) {
                return path;
            }
        }
        return text;
    }

    private static boolean isMeaningfulAncestor(@Nullable VirtualFile ancestor) {
        if (ancestor == null) {
            return false;
        }
        VirtualFile homeDir = VirtualFileUtil.getUserHomeDir();
        return homeDir != null && VirtualFileUtil.isAncestor(homeDir, ancestor, true);
    }
}
