// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import com.jetbrains.jsonSchema.internal.JsonFileResolver;
import com.jetbrains.jsonSchema.internal.JsonSchemaReader;
import consulo.configurable.ConfigurationException;
import consulo.configurable.MasterDetailsConfigurable;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.execution.RuntimeConfigurationWarning;
import consulo.json.localize.JsonLocalize;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationService;
import consulo.ui.Component;
import consulo.ui.TextBox;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.image.Image;
import consulo.util.io.FileUtil;
import consulo.util.io.Urls;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.function.Function;

public final class JsonSchemaConfigurable implements MasterDetailsConfigurable<UserDefinedJsonSchemaConfiguration> {
    private final Project myProject;
    private final String mySchemaFilePath;
    private final UserDefinedJsonSchemaConfiguration mySchema;
    private final @Nullable TreeUpdater myTreeUpdater;
    private final Function<? super String, String> myNameCreator;
    private final Runnable myUpdateTree;
    private @Nullable JsonSchemaMappingsView myView;
    private @Nullable TextBox myNameField;
    private String myDisplayName;
    private LocalizeValue myError = LocalizeValue.empty();

    public JsonSchemaConfigurable(Project project,
                                  String schemaFilePath,
                                  UserDefinedJsonSchemaConfiguration schema,
                                  @Nullable TreeUpdater updateTree,
                                  Function<? super String, String> nameCreator) {
        myUpdateTree = () -> {
            if (updateTree != null) {
                updateTree.updateTree(true);
            }
        };
        myProject = project;
        mySchemaFilePath = schemaFilePath;
        mySchema = schema;
        myTreeUpdater = updateTree;
        myNameCreator = nameCreator;
        myDisplayName = mySchema.getName();
    }

    public UserDefinedJsonSchemaConfiguration getSchema() {
        return mySchema;
    }

    public void setDisplayName(String name) {
        myDisplayName = name;
    }

    @Override
    public @Nullable Image getIcon() {
        return null;
    }

    @Override
    public UserDefinedJsonSchemaConfiguration getEditableObject() {
        return mySchema;
    }

    @Override
    public LocalizeValue getBannerSlogan() {
        return LocalizeValue.of(mySchema.getName());
    }

    @RequiredUIAccess
    @Override
    public void updateName() {
        TextBox nameField = myNameField;
        if (nameField != null && !myDisplayName.equals(nameField.getValue())) {
            nameField.setValue(myDisplayName, false);
        }
        myUpdateTree.run();
    }

    @RequiredUIAccess
    @Override
    public Component createUIComponent(Disposable parentDisposable) {
        JsonSchemaMappingsView view = myView;
        if (view == null) {
            TextBox nameField = TextBox.create(myDisplayName);
            nameField.addValueListener(event -> {
                setDisplayName(StringUtil.notNullize(event.getValue()));
                myUpdateTree.run();
            });
            myNameField = nameField;

            TreeUpdater treeUpdater = myTreeUpdater != null ? myTreeUpdater : showWarning -> {
            };
            view = new JsonSchemaMappingsView(myProject, treeUpdater, (s, force) -> {
                if (force || isGeneratedName()) {
                    int lastSlash = Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\'));
                    if (lastSlash > 0 || force) {
                        String substring = lastSlash > 0 ? s.substring(lastSlash + 1) : s;
                        int dot = lastSlash > 0 ? substring.lastIndexOf('.') : -1;
                        if (dot != -1) {
                            substring = substring.substring(0, dot);
                        }
                        setDisplayName(myNameCreator.apply(substring));
                        updateName();
                    }
                }
            }, nameField);
            view.setError(myError, true);
            Disposer.register(parentDisposable, view);
            myView = view;
        }
        return view.getComponent();
    }

    private boolean isGeneratedName() {
        return myDisplayName.equals(mySchema.getName()) && myDisplayName.equals(mySchema.getGeneratedName());
    }

    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.of(myDisplayName);
    }

    @Override
    public String getHelpTopic() {
        return JsonSchemaMappingsConfigurable.SETTINGS_JSON_SCHEMA;
    }

    @RequiredUIAccess
    @Override
    public boolean isModified() {
        JsonSchemaMappingsView view = myView;
        if (view == null) {
            return false;
        }
        if (!myDisplayName.equals(mySchema.getName())) {
            return true;
        }
        if (!FileUtil.toSystemDependentName(mySchema.getRelativePathToSchema()).equals(view.getSchemaSubPath())) {
            return true;
        }
        if (mySchema.getSchemaVersion() != view.getSchemaVersion()) {
            return true;
        }
        return !Comparing.equal(view.getData(), mySchema.getPatterns());
    }

    @RequiredUIAccess
    @Override
    public void apply() throws ConfigurationException {
        JsonSchemaMappingsView view = myView;
        if (view == null) {
            return;
        }
        doValidation(view);
        mySchema.setName(myDisplayName);
        mySchema.setSchemaVersion(view.getSchemaVersion());
        mySchema.setPatterns(view.getData());
        mySchema.setRelativePathToSchema(view.getSchemaSubPath());
    }

    public static boolean isValidURL(String url) {
        return JsonFileResolver.isHttpPath(url) && Urls.parse(url, false) != null;
    }

    @RequiredUIAccess
    private void doValidation(JsonSchemaMappingsView view) throws ConfigurationException {
        String schemaSubPath = view.getSchemaSubPath();

        if (StringUtil.isEmptyOrSpaces(schemaSubPath)) {
            throw new ConfigurationException(withDisplayNamePrefix(JsonLocalize.schemaConfigurationErrorEmptyPath()));
        }

        VirtualFile vFile = null;
        String filename;

        if (JsonFileResolver.isHttpPath(schemaSubPath)) {
            filename = schemaSubPath;

            if (!isValidURL(schemaSubPath)) {
                throw new ConfigurationException(withDisplayNamePrefix(JsonLocalize.schemaConfigurationErrorInvalidUrl()));
            }
        }
        else {
            File subPath = new File(schemaSubPath);
            File file = subPath.isAbsolute() ? subPath : new File(myProject.getBasePath(), schemaSubPath);
            if (file.exists()) {
                vFile = StandardFileSystems.local().refreshAndFindFileByPath(file.getAbsolutePath());
            }
            if (vFile == null) {
                throw new ConfigurationException(withDisplayNamePrefix(JsonLocalize.schemaConfigurationErrorFileDoesNotExist()));
            }
            filename = file.getName();
        }

        if (StringUtil.isEmptyOrSpaces(myDisplayName)) {
            String fileNamePrefix = filename;
            throw new ConfigurationException(JsonLocalize.schemaConfigurationErrorEmptyName().map(text -> fileNamePrefix + ": " + text));
        }

        // we don't validate remote schemas while in options dialog
        if (vFile == null) {
            return;
        }

        LocalizeValue error = JsonSchemaReader.checkIfValidJsonSchema(myProject, vFile);
        if (error.isNotEmpty()) {
            logErrorForUser(error);
            throw new RuntimeConfigurationWarning(error);
        }
    }

    private LocalizeValue withDisplayNamePrefix(LocalizeValue message) {
        if (StringUtil.isEmptyOrSpaces(myDisplayName)) {
            return message;
        }
        String displayName = myDisplayName;
        return message.map(text -> displayName + ": " + text);
    }

    private void logErrorForUser(LocalizeValue error) {
        NotificationService.getInstance()
            .newWarn(JsonSchemaNotificationGroupContributor.JSON_SCHEMA)
            .content(error)
            .notify(myProject);
    }

    @RequiredUIAccess
    @Override
    public void reset() {
        JsonSchemaMappingsView view = myView;
        if (view == null) {
            return;
        }
        view.setItems(mySchemaFilePath, mySchema.getSchemaVersion(), mySchema.getPatterns());
        setDisplayName(mySchema.getName());
        TextBox nameField = myNameField;
        if (nameField != null) {
            nameField.setValue(myDisplayName, false);
        }
    }

    @RequiredUIAccess
    public UserDefinedJsonSchemaConfiguration getUiSchema() {
        UserDefinedJsonSchemaConfiguration info = new UserDefinedJsonSchemaConfiguration();
        info.setApplicationDefined(mySchema.isApplicationDefined());
        JsonSchemaMappingsView view = myView;
        if (view != null && view.isInitialized()) {
            info.setName(myDisplayName);
            info.setSchemaVersion(view.getSchemaVersion());
            info.setPatterns(view.getData());
            info.setRelativePathToSchema(view.getSchemaSubPath());
        }
        else {
            info.setName(mySchema.getName());
            info.setSchemaVersion(mySchema.getSchemaVersion());
            info.setPatterns(mySchema.getPatterns());
            info.setRelativePathToSchema(mySchema.getRelativePathToSchema());
        }
        return info;
    }

    @RequiredUIAccess
    @Override
    public void disposeUIResources() {
        JsonSchemaMappingsView view = myView;
        if (view != null) {
            Disposer.dispose(view);
        }
        myView = null;
        myNameField = null;
    }

    @RequiredUIAccess
    public void setError(LocalizeValue error, boolean showWarning) {
        myError = error;
        JsonSchemaMappingsView view = myView;
        if (view != null) {
            view.setError(error, showWarning);
        }
    }
}
