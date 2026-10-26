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
package com.jetbrains.jsonSchema.widget;

import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.settings.mappings.JsonSchemaMappingsConfigurable;
import consulo.application.util.HtmlBuilder;
import consulo.application.util.HtmlChunk;
import consulo.fileEditor.FileEditorManager;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.json.icon.JsonIconGroup;
import consulo.json.localize.JsonLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.PopupStep;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.jsonSchema.widget.JsonSchemaStatusPopup.ADD_MAPPING;
import static com.jetbrains.jsonSchema.widget.JsonSchemaStatusPopup.EDIT_MAPPINGS;
import static com.jetbrains.jsonSchema.widget.JsonSchemaStatusPopup.IGNORE_FILE;
import static com.jetbrains.jsonSchema.widget.JsonSchemaStatusPopup.LOAD_REMOTE;
import static com.jetbrains.jsonSchema.widget.JsonSchemaStatusPopup.STOP_IGNORE_FILE;

public class JsonSchemaInfoPopupStep extends BaseListPopupStep<JsonSchemaInfo> {
    private final Project myProject;
    private final @Nullable VirtualFile myVirtualFile;
    private final JsonSchemaService myService;
    private static final Image EMPTY_ICON = Image.empty(PlatformIconGroup.generalAdd().getWidth());

    public JsonSchemaInfoPopupStep(List<JsonSchemaInfo> allSchemas,
                                   Project project,
                                   @Nullable VirtualFile virtualFile,
                                   JsonSchemaService service,
                                   @Nullable String title) {
        super(title, withSeparators(allSchemas));
        myProject = project;
        myVirtualFile = virtualFile;
        myService = service;
    }

    @Override
    public String getTextFor(JsonSchemaInfo value) {
        return value == null ? "" : value.getDescription();
    }

    @Override
    public @Nullable Image getIconFor(JsonSchemaInfo value) {
        if (value instanceof SeparatorInfo) {
            return null;
        }

        if (value == ADD_MAPPING) {
            return PlatformIconGroup.generalAdd();
        }

        if (value == EDIT_MAPPINGS) {
            return PlatformIconGroup.actionsEdit();
        }

        if (value == LOAD_REMOTE) {
            return PlatformIconGroup.actionsRefresh();
        }

        if (value == IGNORE_FILE) {
            return PlatformIconGroup.filetypesIgnored();
        }

        if (value == STOP_IGNORE_FILE) {
            return PlatformIconGroup.actionsAddfile();
        }

        return JsonIconGroup.jsonschema();
    }

    @Override
    public boolean isSeparator(JsonSchemaInfo value) {
        return value instanceof SeparatorInfo;
    }

    @Override
    public boolean isSelectable(JsonSchemaInfo value) {
        return !(value instanceof SeparatorInfo);
    }

    private static List<JsonSchemaInfo> withSeparators(List<JsonSchemaInfo> values) {
        List<JsonSchemaInfo> result = new ArrayList<>(values.size() + 2);
        for (int index = 0; index < values.size(); index++) {
            JsonSchemaInfo value = values.get(index);
            LocalizeValue separator = getSeparatorAbove(values, index);
            if (separator != null) {
                result.add(new SeparatorInfo(separator));
            }
            result.add(value);
        }
        return result;
    }

    private static @Nullable LocalizeValue getSeparatorAbove(List<JsonSchemaInfo> values, int index) {
        JsonSchemaInfo value = values.get(index);
        if (index - 1 >= 0) {
            JsonSchemaInfo info = values.get(index - 1);
            if (info == EDIT_MAPPINGS || info == ADD_MAPPING) {
                return JsonLocalize.schemaWidgetRegisteredSchemas();
            }
            if (value.getProvider() == null && info.getProvider() != null) {
                return JsonLocalize.schemaWidgetStoreSchemas();
            }
        }
        return null;
    }

    @Override
    public @Nullable PopupStep onChosen(JsonSchemaInfo selectedValue, boolean finalChoice) {
        if (finalChoice) {
            if (selectedValue == EDIT_MAPPINGS || selectedValue == ADD_MAPPING) {
                return doFinalStep(() -> runSchemaEditorForCurrentFile());
            }
            else if (selectedValue == LOAD_REMOTE) {
                return doFinalStep(() -> myService.triggerUpdateRemote());
            }
            else if (selectedValue == IGNORE_FILE) {
                markIgnored(myVirtualFile, myProject);
                return doFinalStep(() -> updateFile());
            }
            else if (selectedValue == STOP_IGNORE_FILE) {
                unmarkIgnored(myVirtualFile, myProject);
                return doFinalStep(() -> updateFile());
            }
            else {
                setMapping(selectedValue, myVirtualFile, myProject);
                return doFinalStep(() -> updateFile());
            }
        }
        return PopupStep.FINAL_CHOICE;
    }

    @RequiredUIAccess
    private void updateFile() {
        myService.reset();
        if (myVirtualFile != null) {
            FileEditorManager.getInstance(myProject).updateFilePresentation(myVirtualFile);
        }
    }

    @RequiredUIAccess
    protected void runSchemaEditorForCurrentFile() {
        VirtualFile virtualFile = myVirtualFile;
        assert virtualFile != null : "override this method to do without a virtual file!";
        ShowSettingsUtil.getInstance().showAndSelect(myProject, JsonSchemaMappingsConfigurable.class, (configurable) -> {
            // For some reason, JsonSchemaMappingsConfigurable.reset is called right after this callback, leading to resetting the customization.
            // Workaround: move this logic inside JsonSchemaMappingsConfigurable.reset.
            configurable.setInitializer(() -> {
                JsonSchemaMappingsProjectConfiguration mappings = JsonSchemaMappingsProjectConfiguration.getInstance(myProject);
                UserDefinedJsonSchemaConfiguration configuration = mappings.findMappingForFile(virtualFile);
                if (configuration == null) {
                    configuration = configurable.addProjectSchema();
                    VirtualFile baseDir = myProject.getBaseDir();
                    String relativePath = baseDir == null ? null : VirtualFileUtil.getRelativePath(virtualFile, baseDir);
                    configuration.patterns.add(new UserDefinedJsonSchemaConfiguration.Item(
                        relativePath == null ? virtualFile.getUrl() : relativePath, false, false));
                }
                configurable.selectInTree(configuration);
            });
        });
    }

    @Override
    public boolean isSpeedSearchEnabled() {
        return true;
    }

    public @Nullable String getTooltipTextFor(JsonSchemaInfo value) {
        return getDoc(value);
    }

    private static @Nullable String getDoc(@Nullable JsonSchemaInfo schema) {
        if (schema == null) {
            return null;
        }
        if (schema.getName() == null) {
            return schema.getDocumentation();
        }
        if (schema.getDocumentation() == null) {
            return schema.getName();
        }
        return new HtmlBuilder()
            .append(HtmlChunk.tag("b").addText(schema.getName()))
            .append(HtmlChunk.br())
            .appendRaw(schema.getDocumentation()).toString();
    }

    private static void markIgnored(@Nullable VirtualFile virtualFile, Project project) {
        if (virtualFile == null) {
            return;
        }
        JsonSchemaMappingsProjectConfiguration configuration = JsonSchemaMappingsProjectConfiguration.getInstance(project);
        configuration.markAsIgnored(virtualFile);
    }

    private static void unmarkIgnored(@Nullable VirtualFile virtualFile, Project project) {
        if (virtualFile == null) {
            return;
        }
        JsonSchemaMappingsProjectConfiguration configuration = JsonSchemaMappingsProjectConfiguration.getInstance(project);
        if (!configuration.isIgnoredFile(virtualFile)) {
            return;
        }
        configuration.unmarkAsIgnored(virtualFile);
    }

    protected void setMapping(@Nullable JsonSchemaInfo selectedValue, @Nullable VirtualFile virtualFile, Project project) {
        assert virtualFile != null : "override this method to do without a virtual file!";
        JsonSchemaMappingsProjectConfiguration.getInstance(project).setSchemaForFile(selectedValue, virtualFile);
    }

    private static final class SeparatorInfo extends JsonSchemaInfo {
        private final LocalizeValue myText;

        private SeparatorInfo(LocalizeValue text) {
            super("");
            myText = text;
        }

        @Override
        public String getDescription() {
            return myText.get();
        }
    }
}
