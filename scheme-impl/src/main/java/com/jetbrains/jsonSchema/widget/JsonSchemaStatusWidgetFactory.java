// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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

import consulo.annotation.component.ExtensionImpl;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.statusBar.StatusBarEditorBasedWidgetFactory;
import consulo.json.localize.JsonLocalize;
import consulo.project.Project;
import consulo.project.ui.wm.StatusBar;
import consulo.project.ui.wm.StatusBarWidget;

@ExtensionImpl(id = JsonSchemaStatusWidget.ID, order = "after codeStyleWidget, before readOnlyWidget")
public final class JsonSchemaStatusWidgetFactory extends StatusBarEditorBasedWidgetFactory {
    @Override
    public String getId() {
        return JsonSchemaStatusWidget.ID;
    }

    @Override
    public String getDisplayName() {
        return JsonLocalize.schemaWidgetDisplayName().get();
    }

    @Override
    public boolean canBeEnabledOn(StatusBar statusBar) {
        Project project = statusBar.getProject();
        if (project == null) {
            return false;
        }

        FileEditor editor = getFileEditor(statusBar);
        return JsonSchemaStatusWidget.isAvailableOnFile(project, editor != null ? editor.getFile() : null);
    }

    @Override
    public StatusBarWidget createWidget(Project project) {
        return JsonSchemaStatusWidget.create(project, this);
    }
}
