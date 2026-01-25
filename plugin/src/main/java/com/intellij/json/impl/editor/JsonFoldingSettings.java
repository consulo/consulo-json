// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.editor.folding;

import com.intellij.application.options.editor.CodeFoldingOptionsProvider;
import com.intellij.json.JsonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.options.BeanConfigurable;
import com.intellij.ui.dsl.builder.Panel;
import com.intellij.util.xmlb.XmlSerializerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@State(name = "JsonFoldingSettings", storages = @Storage("editor.xml"), category = SettingsCategory.CODE)
public class JsonFoldingSettings implements PersistentStateComponent<JsonFoldingSettings> {

  public boolean showKeyCount = true;
  public boolean showFirstKey = false;

  @Nullable
  @Override
  public JsonFoldingSettings getState() {
    return this;
  }

  @Override
  public void loadState(@Nonnull JsonFoldingSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Nonnull
  public static JsonFoldingSettings getInstance() {
    JsonFoldingSettings service = ApplicationManager.getApplication().getService(JsonFoldingSettings.class);
    if (service == null) {
      throw new IllegalStateException("JsonFoldingSettings service is not available");
    }
    return service;
  }
}

class JsonFoldingOptionsProvider extends BeanConfigurable<JsonFoldingSettings> implements CodeFoldingOptionsProvider {

  public JsonFoldingOptionsProvider() {
    super(JsonFoldingSettings.getInstance(), JsonBundle.message("JsonFoldingSettings.title"));
  }

  @Override
  protected void createContent(@Nonnull Panel panel) {
    panel.group(JsonBundle.message("JsonFoldingSettings.title"), group -> {
      group.row(row -> {
        row.checkBox(JsonBundle.message("JsonFoldingSettings.show.key.count"))
          .bindSelected(() -> getInstance().showKeyCount, value -> getInstance().showKeyCount = value);
        return null;
      });
      group.row(row -> {
        row.checkBox(JsonBundle.message("JsonFoldingSettings.show.first.key"))
          .bindSelected(() -> getInstance().showFirstKey, value -> getInstance().showFirstKey = value)
          .comment(JsonBundle.message("JsonFoldingSettings.show.first.key.description"));
        return null;
      });
      return null;
    });
  }
}
