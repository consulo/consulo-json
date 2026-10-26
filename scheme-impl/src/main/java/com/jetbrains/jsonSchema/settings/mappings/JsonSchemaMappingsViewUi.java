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
package com.jetbrains.jsonSchema.settings.mappings;

import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.JsonSchemaVersion;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.widget.JsonSchemaInfoPopupStep;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.json.localize.JsonLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.HtmlLabel;
import consulo.ui.Label;
import consulo.ui.LightPopup;
import consulo.ui.PopupOptions;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.layout.DockLayout;
import consulo.ui.layout.HorizontalLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.style.ComponentColors;
import consulo.ui.util.FormBuilder;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BiConsumer;

final class JsonSchemaMappingsViewUi {
    private final Project myProject;
    private final BiConsumer<String, Boolean> mySchemaPathChangedCallback;
    private final Disposable myDisposable;

    private final FileChooserTextBoxBuilder.Controller mySchemaField;
    private final ComboBox<JsonSchemaVersion> mySchemaVersionComboBox;
    private final HorizontalLayout myErrorRow;
    private final HtmlLabel myErrorLabel;
    private LocalizeValue myErrorText = LocalizeValue.empty();

    private final Component myPanel;

    @RequiredUIAccess
    JsonSchemaMappingsViewUi(Project project,
                             BiConsumer<String, Boolean> schemaPathChangedCallback,
                             Component tableDecorator,
                             @Nullable Component nameField,
                             Disposable disposable) {
        myProject = project;
        mySchemaPathChangedCallback = schemaPathChangedCallback;
        myDisposable = disposable;

        mySchemaField = FileChooserTextBoxBuilder.create(project)
            .fileChooserDescriptor(FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor())
            .dialogTitle(JsonLocalize.jsonSchemaAddSchemaChooserTitle())
            .lastActions(new RemoteSchemaAction())
            .uiDisposable(disposable)
            .build();
        mySchemaField.getComponent().addValueListener(event -> mySchemaPathChangedCallback.accept(mySchemaField.getValue(), false));

        mySchemaVersionComboBox = ComboBox.create(JsonSchemaVersion.values());
        mySchemaVersionComboBox.setTextRenderer(version -> version == null ? JsonLocalize.schemaUnknown() : LocalizeValue.of(version.toString()));

        FormBuilder formBuilder = FormBuilder.create();
        if (nameField != null) {
            formBuilder.addLabeled(JsonLocalize.jsonSchemaNameTitle(), nameField);
        }
        formBuilder.addLabeled(JsonLocalize.jsonSchemaFileSelectorTitle(), mySchemaField.getComponent());
        formBuilder.addLabeled(JsonLocalize.jsonSchemaVersionSelectorTitle(), mySchemaVersionComboBox);

        Label errorIcon = Label.create();
        errorIcon.setImage(PlatformIconGroup.generalBalloonwarning());
        myErrorLabel = HtmlLabel.create(JsonLocalize.jsonSchemaConflictingMappings());
        myErrorLabel.addHyperlinkListener(event -> showErrorDetails());
        myErrorRow = HorizontalLayout.create();
        myErrorRow.add(errorIcon);
        myErrorRow.add(myErrorLabel);
        myErrorRow.setVisible(false);

        VerticalLayout top = VerticalLayout.create();
        top.add(formBuilder.build());
        top.add(myErrorRow);

        Label comment = Label.create(JsonLocalize.pathToFileOrDirectoryRelativeToProjectRootOrFileName());
        comment.setForegroundColor(ComponentColors.DISABLED_TEXT);

        DockLayout root = DockLayout.create();
        root.top(top);
        root.center(tableDecorator);
        root.bottom(comment);
        myPanel = root;
    }

    FileChooserTextBoxBuilder.Controller getSchemaField() {
        return mySchemaField;
    }

    ComboBox<JsonSchemaVersion> getSchemaVersionComboBox() {
        return mySchemaVersionComboBox;
    }

    Component getPanel() {
        return myPanel;
    }

    @RequiredUIAccess
    void setError(LocalizeValue text, boolean showWarning) {
        myErrorText = text;
        myErrorRow.setVisible(showWarning && text.isNotEmpty());
    }

    @RequiredUIAccess
    private void showErrorDetails() {
        LocalizeValue errorText = myErrorText;
        if (errorText.isEmpty()) {
            return;
        }

        VerticalLayout content = VerticalLayout.create();
        for (String line : StringUtil.splitByLines(errorText.get())) {
            content.add(Label.create(LocalizeValue.of(line)));
        }

        LightPopup popup = LightPopup.create(PopupOptions.builder().arrow().build());
        popup.setContent(content);
        Disposer.register(myDisposable, popup);
        popup.showBy(myErrorLabel);
    }

    private final class RemoteSchemaAction extends DumbAwareAction {
        private RemoteSchemaAction() {
            super(JsonLocalize.schemaConfigurationMappingRemote(), LocalizeValue.empty(), PlatformIconGroup.generalWeb());
        }

        @RequiredUIAccess
        @Override
        public void actionPerformed(AnActionEvent e) {
            JsonSchemaService service = JsonSchemaService.Impl.get(myProject);
            List<JsonSchemaInfo> schemas = service.getAllUserVisibleSchemas();
            JBPopupFactory.getInstance().createListPopup(
                new JsonSchemaInfoPopupStep(schemas, myProject, null, service, JsonLocalize.schemaConfigurationMappingRemote().get()) {
                    @Override
                    protected void setMapping(@Nullable JsonSchemaInfo selectedValue, @Nullable VirtualFile virtualFile, Project project) {
                        if (selectedValue != null) {
                            mySchemaField.setValue(selectedValue.getUrl(project));
                            // force updating name
                            mySchemaPathChangedCallback.accept(selectedValue.getDescription(), true);
                        }
                    }
                }).showUnderneathOf(e);
        }
    }
}
