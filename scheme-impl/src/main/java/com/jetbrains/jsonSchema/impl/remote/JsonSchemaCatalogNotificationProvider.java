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

import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.internal.JsonSchemaCatalogEntry;
import com.jetbrains.jsonSchema.internal.JsonSchemaCatalogProjectConfiguration;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.fileEditor.EditorNotificationBuilder;
import consulo.fileEditor.EditorNotificationProvider;
import consulo.fileEditor.EditorNotifications;
import consulo.fileEditor.FileEditor;
import consulo.json.localize.JsonLocalize;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

@ExtensionImpl
public class JsonSchemaCatalogNotificationProvider implements EditorNotificationProvider, DumbAware {
    private final Project myProject;

    @Inject
    public JsonSchemaCatalogNotificationProvider(Project project) {
        myProject = project;
    }

    @Override
    public String getId() {
        return "json-schema-catalog";
    }

    @RequiredReadAction
    @Override
    public @Nullable EditorNotificationBuilder buildNotification(VirtualFile file,
                                                                 FileEditor fileEditor,
                                                                 Supplier<EditorNotificationBuilder> builderFactory) {
        JsonSchemaService service = JsonSchemaService.get(myProject);
        if (!service.isApplicableToFile(file)) {
            return null;
        }

        JsonSchemaCatalogNotificationUpdateService.getInstance(myProject).ensureCallbacksRegistered();

        JsonSchemaMappingsProjectConfiguration mappingsConfiguration = JsonSchemaMappingsProjectConfiguration.getInstance(myProject);
        if (mappingsConfiguration.findMappingForFile(file) != null) {
            return null;
        }
        if (!service.getSchemaFilesForFile(file).isEmpty()) {
            return null;
        }

        JsonSchemaCatalogEntry entry = service.getCatalogManager().getSchemaCatalogEntryForFile(file);
        if (entry == null) {
            return null;
        }
        JsonSchemaInfo schemaInfo = createSchemaInfo(entry);
        return builderFactory.get()
            .asInfo()
            .withText(JsonLocalize.schemaCatalogSuggestionText(schemaInfo.getDescription()))
            .withAction(JsonLocalize.schemaCatalogSuggestionApply(), event -> applySchema(myProject, file, schemaInfo))
            .withAction(JsonLocalize.schemaWidgetNoMapping(), event -> ignoreSchema(myProject, file))
            .withAction(JsonLocalize.schemaCatalogSuggestionDisable(), event -> disableCatalogSuggestions(myProject));
    }

    private static JsonSchemaInfo createSchemaInfo(JsonSchemaCatalogEntry entry) {
        JsonSchemaInfo info = new JsonSchemaInfo(entry.getUrl());
        info.setName(entry.getName());
        info.setDocumentation(entry.getDescription());
        return info;
    }

    private static void applySchema(Project project, VirtualFile file, JsonSchemaInfo schemaInfo) {
        JsonSchemaMappingsProjectConfiguration.getInstance(project).setSchemaForFile(schemaInfo, file);
        JsonSchemaService.get(project).reset();
        EditorNotifications.getInstance(project).updateNotifications(file);
    }

    private static void ignoreSchema(Project project, VirtualFile file) {
        JsonSchemaMappingsProjectConfiguration.getInstance(project).markAsIgnored(file);
        JsonSchemaService.get(project).reset();
        EditorNotifications.getInstance(project).updateNotifications(file);
    }

    private static void disableCatalogSuggestions(Project project) {
        JsonSchemaCatalogProjectConfiguration.getInstance(project).setCatalogEnabled(false);
        JsonSchemaService.get(project).reset();
        EditorNotifications.getInstance(project).updateAllNotifications();
    }
}
