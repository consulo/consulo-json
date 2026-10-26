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

import com.jetbrains.jsonSchema.JsonMappingKind;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import consulo.disposer.Disposable;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileChooser.FileChooserTextBoxBuilder;
import consulo.json.localize.JsonLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.TextComponentAccessor;
import consulo.ui.ex.dialog.DialogDescriptor;
import consulo.ui.util.FormBuilder;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 2026-10-26
 */
final class JsonMappingItemDialogDescriptor extends DialogDescriptor {
    private final Project myProject;
    private final JsonMappingKind myMappingKind;
    private String myPath;

    private @Nullable TextBox myPathField;

    JsonMappingItemDialogDescriptor(Project project, LocalizeValue title, UserDefinedJsonSchemaConfiguration.Item item) {
        super(title);
        myProject = project;
        myMappingKind = item.mappingKind;
        myPath = item.getPath();
    }

    @RequiredUIAccess
    @Override
    public Component createCenterComponent(Disposable uiDisposable) {
        TextBox pathField;
        if (myMappingKind == JsonMappingKind.Pattern) {
            pathField = TextBox.create(myPath);
        }
        else {
            FileChooserTextBoxBuilder builder = FileChooserTextBoxBuilder.create(myProject);
            builder.fileChooserDescriptor(createDescriptor(myMappingKind));
            builder.textBoxAccessor(new RelativePathAccessor(myProject));
            builder.disableCompletion();
            builder.uiDisposable(uiDisposable);
            FileChooserTextBoxBuilder.Controller controller = builder.build();
            controller.setValue(myPath, false);
            pathField = controller.getComponent();
        }
        pathField.addValueListener(event -> myPath = StringUtil.notNullize(event.getValue()));
        myPathField = pathField;

        Label label = Label.create(myMappingKind.getPrefix().map(text -> text.trim()));
        label.setImage(myMappingKind.getIcon());

        FormBuilder builder = FormBuilder.create();
        builder.addLabeled(label, pathField);
        builder.addBottom(Label.create(JsonLocalize.pathToFileOrDirectoryRelativeToProjectRootOrFileName()));
        return builder.build();
    }

    @RequiredUIAccess
    @Override
    public @Nullable Component getPreferredFocusedComponent() {
        return myPathField;
    }

    String getPath() {
        return myPath;
    }

    private static FileChooserDescriptor createDescriptor(JsonMappingKind mappingKind) {
        return mappingKind == JsonMappingKind.File
            ? FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor()
            : FileChooserDescriptorFactory.createSingleFolderDescriptor();
    }

    private static final class RelativePathAccessor implements TextComponentAccessor<TextBox> {
        private final Project myProject;

        private RelativePathAccessor(Project project) {
            myProject = project;
        }

        @RequiredUIAccess
        @Override
        public String getValue(TextBox component) {
            return StringUtil.notNullize(component.getValue());
        }

        @RequiredUIAccess
        @Override
        public void setValue(TextBox component, String text, boolean fireListeners) {
            component.setValue(toResultingText(text), fireListeners);
        }

        private String toResultingText(String chosenPath) {
            String basePath = myProject.getBasePath();
            if (basePath == null || StringUtil.isEmpty(chosenPath)) {
                return chosenPath;
            }
            String path = FileUtil.toSystemIndependentName(chosenPath);
            String base = FileUtil.toSystemIndependentName(basePath);
            if (!FileUtil.isAncestor(base, path, false)) {
                return chosenPath;
            }
            String relativePath = FileUtil.getRelativePath(base, path, '/');
            return relativePath != null ? relativePath : chosenPath;
        }
    }
}
