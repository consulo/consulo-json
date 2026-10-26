// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import com.jetbrains.jsonSchema.JsonSchemaService;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.WriteAction;
import consulo.application.dumb.PossiblyDumbAware;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.net.URL;
import java.util.List;

/**
 * Implement to contribute JSON Schemas for particular JSON documents to enable validation/completion based on JSON Schema.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface JsonSchemaProviderFactory extends PossiblyDumbAware {
    Logger LOG = Logger.getInstance(JsonSchemaProviderFactory.class);

    /**
     * Called in smart mode by default. Implement {@link consulo.application.dumb.DumbAware} to be called in dumb mode.
     */
    List<JsonSchemaFileProvider> getProviders(Project project);

    /**
     * Finds a {@link VirtualFile} instance corresponding to a specified resource path (relative or absolute).
     *
     * @param resourcePath  String identifying a resource (relative or absolute)
     *                      See {@link Class#getResource(String)} for more details
     * @return VirtualFile instance, or null if not found
     */
    static @Nullable VirtualFile getResourceFile(Class<?> baseClass, String resourcePath) {
        URL url = baseClass.getResource(resourcePath);
        if (url == null) {
            LOG.error("Cannot find resource " + resourcePath);
            return null;
        }
        VirtualFile file = VirtualFileUtil.findFileByURL(url);
        if (file != null) {
            return file;
        }
        LOG.info("File not found by " + url + ", performing refresh...");
        WriteAction.runLater(() -> {
            VirtualFileManager virtualFileManager = VirtualFileManager.getInstance();
            VirtualFile refreshed = virtualFileManager.refreshAndFindFileByUrl(VirtualFileUtil.convertFromUrl(url, virtualFileManager));
            if (refreshed != null) {
                LOG.info("Refreshed " + url + " successfully");
                for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                    JsonSchemaService service = project.getInstance(JsonSchemaService.class);
                    service.reset();
                }
            }
            else {
                LOG.error("Cannot refresh and find file by " + resourcePath);
            }
        });
        return null;
    }
}
