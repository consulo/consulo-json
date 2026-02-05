// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.widget;

import consulo.json.localize.JsonLocalize;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.impl.status.widget.StatusBarEditorBasedWidgetFactory;
import jakarta.annotation.Nonnull;
import kotlinx.coroutines.CoroutineScope;

public final class JsonSchemaStatusWidgetFactory extends StatusBarEditorBasedWidgetFactory {
  @Override
  public @Nonnull String getId() {
    return JsonSchemaStatusWidget.ID;
  }

  @Override
  public @Nonnull String getDisplayName() {
    return JsonLocalize.schemaWidgetDisplayName().get();
  }

  @Override
  public boolean canBeEnabledOn(@Nonnull StatusBar statusBar) {
    Project project = statusBar.getProject();
    if (project == null) {
      return false;
    }

    FileEditor editor = getFileEditor(statusBar);
    return JsonSchemaStatusWidget.isAvailableOnFile(project, editor != null ? editor.getFile() : null);
  }

  @Override
  public @Nonnull StatusBarWidget createWidget(@Nonnull Project project, @Nonnull CoroutineScope scope) {
    return new JsonSchemaStatusWidget(project, scope);
  }
}
