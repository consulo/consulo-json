// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.json.JsonBundle;
import com.intellij.openapi.options.BoundConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogPanel;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.dsl.builder.Panel;
import jakarta.annotation.Nonnull;

public class JsonSchemaCatalogConfigurable extends BoundConfigurable {
  private final Project project;
  private JBCheckBox remoteCheckBox;
  private JBCheckBox catalogCheckBox;
  private JBCheckBox preferRemoteCheckBox;

  public JsonSchemaCatalogConfigurable(Project project) {
    super(JsonBundle.message("configurable.JsonSchemaCatalogConfigurable.display.name"), "settings.json.schema.catalog");
    this.project = project;
  }

  @Nonnull
  @Override
  protected DialogPanel createPanel() {
    return Panel.panel(panel -> {
      panel.row(row -> {
        remoteCheckBox = row.checkBox(JsonBundle.message("checkbox.allow.downloading.json.schemas.from.remote.sources"))
          .onChanged(checkbox -> {
            if (!checkbox.isSelected()) {
              catalogCheckBox.setSelected(false);
              preferRemoteCheckBox.setSelected(false);
            }
          })
          .focused()
          .getComponent();
        return null;
      });

      panel.indent(indent -> {
        indent.row(row -> {
          catalogCheckBox = row.checkBox(JsonBundle.message("checkbox.use.schemastore.org.json.schema.catalog"))
            .comment(JsonBundle.message("schema.catalog.hint"))
            .getComponent();
          return null;
        });

        indent.row(row -> {
          preferRemoteCheckBox = row.checkBox(JsonBundle.message("checkbox.always.download.the.most.recent.version.of.schemas"))
            .comment(JsonBundle.message("schema.catalog.remote.hint"))
            .getComponent();
          return null;
        });
        return null;
      }).enabledIf(() -> remoteCheckBox.isSelected());

      return null;
    });
  }

  @Override
  public boolean isModified() {
    JsonSchemaCatalogProjectConfiguration.MyState state = JsonSchemaCatalogProjectConfiguration.getInstance(project).getState();
    return super.isModified() || state == null
           || state.myIsCatalogEnabled != catalogCheckBox.isSelected()
           || state.myIsPreferRemoteSchemas != preferRemoteCheckBox.isSelected()
           || state.myIsRemoteActivityEnabled != remoteCheckBox.isSelected();
  }

  @Override
  public void reset() {
    super.reset();

    JsonSchemaCatalogProjectConfiguration.MyState state = JsonSchemaCatalogProjectConfiguration.getInstance(project).getState();
    remoteCheckBox.setSelected(state == null || state.myIsRemoteActivityEnabled);
    catalogCheckBox.setSelected(state == null || state.myIsCatalogEnabled);
    preferRemoteCheckBox.setSelected(state == null || state.myIsPreferRemoteSchemas);
  }

  @Override
  public void apply() {
    super.apply();

    JsonSchemaCatalogProjectConfiguration.getInstance(project).setState(
      catalogCheckBox.isSelected(),
      remoteCheckBox.isSelected(),
      preferRemoteCheckBox.isSelected()
    );
  }
}
