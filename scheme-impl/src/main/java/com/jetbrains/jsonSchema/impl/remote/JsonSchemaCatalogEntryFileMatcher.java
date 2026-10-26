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

import com.jetbrains.jsonSchema.internal.JsonSchemaCatalogEntry;
import com.jetbrains.jsonSchema.internal.JsonSchemaVfsUtil;
import consulo.application.ReadAction;
import consulo.logging.Logger;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.regex.PatternSyntaxException;

public class JsonSchemaCatalogEntryFileMatcher {
    private static final Logger LOG = Logger.getInstance(JsonSchemaCatalogEntryFileMatcher.class);

    private final JsonSchemaCatalogEntry myEntry;
    private @Nullable PathMatcher myMatcher;

    public JsonSchemaCatalogEntryFileMatcher(JsonSchemaCatalogEntry entry) {
        myEntry = entry;
    }

    public JsonSchemaCatalogEntry getEntry() {
        return myEntry;
    }

    public boolean matches(VirtualFile file, Project project) {
        String relativePath = getRelativePath(file, project);
        if (matches(relativePath)) {
            return true;
        }

        String fileName = file.getName();
        return !fileName.equals(relativePath) && matches(fileName);
    }

    private boolean matches(@Nullable String filePath) {
        if (filePath == null) {
            return false;
        }

        Path path;
        try {
            path = Paths.get(filePath);
        }
        catch (InvalidPathException e) {
            LOG.debug("Unable to process invalid path '" + filePath + "'", e);
            return false;
        }

        try {
            PathMatcher matcher = myMatcher;
            if (matcher == null) {
                matcher = buildPathMatcher(myEntry.getFileMasks());
                myMatcher = matcher;
            }
            return matcher.matches(path);
        }
        catch (PatternSyntaxException e) {
            LOG.warn("Unable to process matches for path '" + path + "' with matcher URL '" + myEntry.getUrl() + "'", e);
            return false;
        }
    }

    public static @Nullable String getRelativePath(VirtualFile file, Project project) {
        String basePath = project.getBasePath();
        if (basePath != null) {
            String prefix = StringUtil.trimEnd(basePath, VirtualFileUtil.VFS_SEPARATOR_CHAR) + VirtualFileUtil.VFS_SEPARATOR_CHAR;
            String filePath = file.getPath();
            if (filePath.startsWith(prefix)) {
                return filePath.substring(prefix.length());
            }
        }

        VirtualFile contentRoot = ReadAction.compute(() -> {
            if (project.isDisposed() || !file.isValid()) {
                return null;
            }
            return ProjectFileIndex.getInstance(project).getContentRootForFile(file, false);
        });
        return contentRoot == null ? null : JsonSchemaVfsUtil.findRelativePath(contentRoot, file, VirtualFileUtil.VFS_SEPARATOR_CHAR);
    }

    private static PathMatcher buildPathMatcher(Collection<String> fileMasks) {
        List<String> refinedFileMasks = ContainerUtil.map(fileMasks, fileMask -> StringUtil.trimStart(fileMask, "**/"));
        if (refinedFileMasks.size() == 1) {
            return FileSystems.getDefault().getPathMatcher("glob:" + ContainerUtil.getFirstItem(refinedFileMasks));
        }
        if (!refinedFileMasks.isEmpty()) {
            return FileSystems.getDefault().getPathMatcher("glob:{" + StringUtil.join(refinedFileMasks, ",") + "}");
        }
        return path -> false;
    }
}
