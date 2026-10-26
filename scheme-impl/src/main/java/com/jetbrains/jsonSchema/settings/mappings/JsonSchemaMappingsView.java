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
package com.jetbrains.jsonSchema.settings.mappings;

import com.jetbrains.jsonSchema.JsonMappingKind;
import com.jetbrains.jsonSchema.JsonSchemaVersion;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import consulo.application.Application;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.disposer.Disposable;
import consulo.json.localize.JsonLocalize;
import consulo.localize.LocalizeValue;
import consulo.navigation.OpenFileDescriptorFactory;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.LightPopup;
import consulo.ui.PopupOptions;
import consulo.ui.Table;
import consulo.ui.TableItemRenderer;
import consulo.ui.UIAccess;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.CommonShortcuts;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.dialog.DialogService;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.PopupStep;
import consulo.ui.ex.toolbar.AddAction;
import consulo.ui.ex.toolbar.DownMoveAction;
import consulo.ui.ex.toolbar.EditAction;
import consulo.ui.ex.toolbar.RemoveAction;
import consulo.ui.ex.toolbar.ToolbarDecoratorBuilderFactory;
import consulo.ui.ex.toolbar.UpMoveAction;
import consulo.ui.image.Image;
import consulo.ui.model.FlatDataModel;
import consulo.ui.model.MutableFlatDataModel;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.StandardFileSystems;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.jetbrains.jsonSchema.internal.JsonFileResolver.isAbsoluteUrl;
import static com.jetbrains.jsonSchema.internal.JsonFileResolver.isHttpPath;

public final class JsonSchemaMappingsView implements Disposable {
    private final TreeUpdater myTreeUpdater;
    private final BiConsumer<String, Boolean> mySchemaPathChangedCallback;
    private final MutableFlatDataModel<UserDefinedJsonSchemaConfiguration.Item> myModel = FlatDataModel.of(List.of());
    private final Table<UserDefinedJsonSchemaConfiguration.Item> myTableView;
    private final JsonSchemaMappingsViewUi myUi;
    private final Project myProject;
    private boolean myInitialized;

    @RequiredUIAccess
    public JsonSchemaMappingsView(Project project,
                                  TreeUpdater treeUpdater,
                                  BiConsumer<String, Boolean> schemaPathChangedCallback) {
        this(project, treeUpdater, schemaPathChangedCallback, null);
    }

    @RequiredUIAccess
    public JsonSchemaMappingsView(Project project,
                                  TreeUpdater treeUpdater,
                                  BiConsumer<String, Boolean> schemaPathChangedCallback,
                                  @Nullable Component nameField) {
        myProject = project;
        myTreeUpdater = treeUpdater;
        mySchemaPathChangedCallback = schemaPathChangedCallback;

        myTableView = Table.create(myModel);
        myTableView.setShowHeader(false);
        myTableView.setAllowMultipleSelect(true);
        myTableView.addColumn(LocalizeValue.empty(), UserDefinedJsonSchemaConfiguration.Item::getPresentation)
            .setRenderer((TableItemRenderer<UserDefinedJsonSchemaConfiguration.Item, LocalizeValue>) (presentation, value, item) -> {
                presentation.withIcon(item.mappingKind.getIcon());
                presentation.append(item.getPresentation());

                LocalizeValue error = item.getError();
                if (error.isNotEmpty()) {
                    presentation.withSuffix(LocalizeValue.empty(), PlatformIconGroup.generalWarning());
                    presentation.withTooltip(error);
                }
            });
        myTableView.addDoubleClickListener(event -> {
            UserDefinedJsonSchemaConfiguration.Item item = myTableView.getSelectedItem();
            if (item != null) {
                editItem(item);
            }
        });

        Component decorator = ToolbarDecoratorBuilderFactory.getInstance()
            .create(myTableView)
            .addOrReplaceAction(new MyAddActionButtonRunnable())
            .addOrReplaceAction(new MyRemoveActionButtonRunnable())
            .addOrReplaceAction(new MyEditActionButtonRunnableImpl())
            .disableAction(UpMoveAction.class)
            .disableAction(DownMoveAction.class)
            .build();

        myUi = new JsonSchemaMappingsViewUi(project, mySchemaPathChangedCallback, decorator, nameField, this);
        attachNavigateToSchema();
    }

    @Override
    public void dispose() {
    }

    @RequiredUIAccess
    public void setError(LocalizeValue text, boolean showWarning) {
        myUi.setError(text, showWarning);
    }

    @RequiredUIAccess
    private void attachNavigateToSchema() {
        DumbAwareAction.create(e -> {
            String pathToSchema = myUi.getSchemaField().getValue();
            if (StringUtil.isEmptyOrSpaces(pathToSchema) || isHttpPath(pathToSchema)) {
                return;
            }
            UIAccess uiAccess = UIAccess.current();
            String absolutePath = new File(pathToSchema).getAbsolutePath();
            CompletableFuture.supplyAsync(() -> StandardFileSystems.local().refreshAndFindFileByPath(absolutePath),
                    AppExecutorUtil.getAppExecutorService())
                .whenCompleteAsync((virtualFile, error) -> {
                    if (myProject.isDisposed()) {
                        return;
                    }
                    if (virtualFile == null) {
                        showFileNotFound();
                        return;
                    }
                    OpenFileDescriptorFactory.getInstance(myProject).newBuilder(virtualFile).build().navigate(true);
                }, uiAccess);
        }).registerCustomShortcutSet(CommonShortcuts.getEditSource(), myUi.getSchemaField().getComponent(), this);
    }

    @RequiredUIAccess
    private void showFileNotFound() {
        Label label = Label.create(JsonLocalize.jsonSchemaFileNotFound());
        label.setImage(PlatformIconGroup.generalBalloonerror());

        LightPopup popup = LightPopup.create(PopupOptions.builder().arrow().disableRequestFocus().build());
        popup.setContent(label);
        popup.showBy(myUi.getSchemaField().getComponent());
    }

    public List<UserDefinedJsonSchemaConfiguration.Item> getData() {
        List<UserDefinedJsonSchemaConfiguration.Item> result = new ArrayList<>();
        for (UserDefinedJsonSchemaConfiguration.Item item : myModel) {
            if (item.mappingKind == JsonMappingKind.Directory || !StringUtil.isEmpty(item.path)) {
                result.add(new UserDefinedJsonSchemaConfiguration.Item(item.path, item.mappingKind));
            }
        }
        return result;
    }

    @RequiredUIAccess
    public void setItems(String schemaFilePath,
                         JsonSchemaVersion version,
                         List<UserDefinedJsonSchemaConfiguration.Item> data) {
        myInitialized = true;
        myUi.getSchemaField().setValue(schemaFilePath, false);
        myUi.getSchemaVersionComboBox().setValue(version);
        List<UserDefinedJsonSchemaConfiguration.Item> items = new ArrayList<>();
        for (UserDefinedJsonSchemaConfiguration.Item item : data) {
            items.add(new UserDefinedJsonSchemaConfiguration.Item(item.path, item.mappingKind));
        }
        myModel.replaceAll(items);
    }

    public boolean isInitialized() {
        return myInitialized;
    }

    @RequiredUIAccess
    public JsonSchemaVersion getSchemaVersion() {
        JsonSchemaVersion version = myUi.getSchemaVersionComboBox().getValue();
        return version == null ? JsonSchemaVersion.SCHEMA_4 : version;
    }

    @RequiredUIAccess
    public String getSchemaSubPath() {
        String schemaFieldText = myUi.getSchemaField().getValue();
        if (isAbsoluteUrl(schemaFieldText)) {
            return schemaFieldText;
        }
        return FileUtil.toSystemDependentName(JsonSchemaInfo.getRelativePath(myProject, schemaFieldText));
    }

    public Component getComponent() {
        return myUi.getPanel();
    }

    @RequiredUIAccess
    private void editItem(UserDefinedJsonSchemaConfiguration.Item item) {
        showItemDialog(JsonLocalize.settingsJsonSchemaEditMapping(), item, path -> {
            item.setPath(path);
            myModel.update(item);
            myTreeUpdater.updateTree(true);
        });
    }

    @RequiredUIAccess
    private void showItemDialog(LocalizeValue title,
                                UserDefinedJsonSchemaConfiguration.Item item,
                                @RequiredUIAccess Consumer<String> onOk) {
        JsonMappingItemDialogDescriptor descriptor = new JsonMappingItemDialogDescriptor(myProject, title, item);
        DialogService dialogService = Application.get().getInstance(DialogService.class);
        UIAccess uiAccess = UIAccess.current();
        dialogService.build(myTableView, descriptor).showAsync().whenCompleteAsync((value, error) -> {
            if (error != null || value == null) {
                return;
            }
            onOk.accept(descriptor.getPath());
        }, uiAccess);
    }

    private final class MyAddActionButtonRunnable extends AddAction<UserDefinedJsonSchemaConfiguration.Item> {
        MyAddActionButtonRunnable() {
            getTemplatePresentation().setText(JsonLocalize.settingsJsonSchemaAddMapping());
        }

        @RequiredUIAccess
        @Override
        protected void doAdd(AnActionEvent e) {
            JBPopupFactory.getInstance().createListPopup(new BaseListPopupStep<>(null, JsonMappingKind.values()) {
                @Override
                public String getTextFor(JsonMappingKind value) {
                    return JsonLocalize.schemaAddMappingKindText(StringUtil.capitalizeWords(value.getDescription().get(), true)).get();
                }

                @Override
                public Image getIconFor(JsonMappingKind value) {
                    return value.getIcon();
                }

                @Override
                public @Nullable PopupStep onChosen(JsonMappingKind selectedValue, boolean finalChoice) {
                    if (finalChoice) {
                        return doFinalStep(() -> doRun(selectedValue));
                    }
                    return FINAL_CHOICE;
                }
            }).showUnderneathOf(e);
        }

        @RequiredUIAccess
        private void doRun(JsonMappingKind mappingKind) {
            UserDefinedJsonSchemaConfiguration.Item currentItem = new UserDefinedJsonSchemaConfiguration.Item("", mappingKind);
            showItemDialog(JsonLocalize.settingsJsonSchemaAddMapping(), currentItem, path -> {
                currentItem.setPath(path);
                myModel.add(currentItem);
                myTableView.select(currentItem);

                myTreeUpdater.updateTree(true);
            });
        }
    }

    private final class MyEditActionButtonRunnableImpl extends EditAction<UserDefinedJsonSchemaConfiguration.Item> {
        MyEditActionButtonRunnableImpl() {
            getTemplatePresentation().setText(JsonLocalize.settingsJsonSchemaEditMapping());
        }

        @RequiredUIAccess
        @Override
        protected void doEdit(UserDefinedJsonSchemaConfiguration.Item value, AnActionEvent e) {
            editItem(value);
        }
    }

    private final class MyRemoveActionButtonRunnable extends RemoveAction<UserDefinedJsonSchemaConfiguration.Item> {
        MyRemoveActionButtonRunnable() {
            getTemplatePresentation().setText(JsonLocalize.settingsJsonSchemaRemoveMapping());
        }

        @RequiredUIAccess
        @Override
        public void actionPerformed(AnActionEvent e) {
            List<UserDefinedJsonSchemaConfiguration.Item> rows = getSelectedValues(e);
            if (!rows.isEmpty()) {
                for (UserDefinedJsonSchemaConfiguration.Item row : rows) {
                    myModel.remove(row);
                }
                myTreeUpdater.updateTree(true);
            }
        }
    }
}
