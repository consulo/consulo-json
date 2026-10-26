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

import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.JsonSchemaVersion;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.Configurable;
import consulo.configurable.ConfigurationException;
import consulo.configurable.MasterDetailsConfigurable;
import consulo.configurable.NonDefaultProjectConfigurable;
import consulo.configurable.ProjectConfigurable;
import consulo.configurable.SearchableConfigurable;
import consulo.configurable.StandardConfigurableIds;
import consulo.configurable.UnifiedMasterDetailsComponent;
import consulo.fileEditor.EditorNotifications;
import consulo.fileEditor.FileEditorManager;
import consulo.json.localize.JsonLocalize;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.platform.base.localize.CommonLocalize;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.ui.TextItemPresentation;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.util.collection.MultiMap;
import consulo.util.lang.ThreeState;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import static com.jetbrains.jsonSchema.internal.JsonFileResolver.isAbsoluteUrl;

@ExtensionImpl
public final class JsonSchemaMappingsConfigurable extends UnifiedMasterDetailsComponent
    implements ProjectConfigurable, SearchableConfigurable, NonDefaultProjectConfigurable, Configurable.NoMargin, Configurable.NoScroll {
    public static final String SETTINGS_JSON_SCHEMA = "settings.json.schema";
    private @Nullable Runnable myInitializer = null;

    private static final Comparator<UserDefinedJsonSchemaConfiguration> COMPARATOR = (o1, o2) -> {
        if (o1.isApplicationDefined() != o2.isApplicationDefined()) {
            return o1.isApplicationDefined() ? 1 : -1;
        }
        return o1.getName().compareToIgnoreCase(o2.getName());
    };
    static final LocalizeValue STUB_SCHEMA_NAME = JsonLocalize.newSchema();
    private LocalizeValue myError = LocalizeValue.empty();

    private final Project myProject;
    private final TreeUpdater myTreeUpdater = showWarning -> {
        refreshNodes();
        updateWarningText(showWarning);
    };

    private final Function<String, String> myNameCreator = s -> createUniqueName(s);

    private boolean myLoaded;
    private boolean myInReset;
    private @Nullable Object myValueToSelect;

    @Inject
    public JsonSchemaMappingsConfigurable(Project project) {
        myProject = project;
    }

    @Override
    protected LocalizeValue getEmptySelectionText() {
        return getChildren(null).isEmpty()
            ? JsonLocalize.schemaConfigurationMappingEmptyAreaAltString()
            : JsonLocalize.schemaConfigurationMappingEmptyAreaString();
    }

    @Override
    protected List<AnAction> createActions() {
        List<AnAction> result = new ArrayList<>();
        result.add(new MyAddAction());
        result.add(new MyDeleteAction());
        result.add(new MyCopyAction());
        return result;
    }

    @RequiredUIAccess
    public UserDefinedJsonSchemaConfiguration addProjectSchema() {
        UserDefinedJsonSchemaConfiguration configuration =
            new UserDefinedJsonSchemaConfiguration(createUniqueName(STUB_SCHEMA_NAME.get()), JsonSchemaVersion.SCHEMA_4, "", false, null);
        configuration.setGeneratedName(configuration.getName());
        addCreatedMappings(configuration);
        return configuration;
    }

    @SuppressWarnings("SameParameterValue")
    private String createUniqueName(String s) {
        int max = -1;
        for (Object element : getChildren(null)) {
            if (!(element instanceof JsonSchemaConfigurable configurable)) {
                continue;
            }
            String displayName = configurable.getDisplayName().get();
            if (displayName.startsWith(s)) {
                String lastPart = displayName.substring(s.length()).trim();
                if (lastPart.isEmpty() && max == -1) {
                    max = 1;
                    continue;
                }
                int i = tryParseInt(lastPart);
                if (i == -1) {
                    continue;
                }
                max = Math.max(i, max);
            }
        }
        return max == -1 ? s : (s + " " + (max + 1));
    }

    private static int tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        }
        catch (NumberFormatException e) {
            return -1;
        }
    }

    @RequiredUIAccess
    private void addCreatedMappings(UserDefinedJsonSchemaConfiguration info) {
        addCreatedMappings(info, "");
    }

    @RequiredUIAccess
    private void addCreatedMappings(UserDefinedJsonSchemaConfiguration info, String schemaFilePath) {
        JsonSchemaConfigurable configurable = new JsonSchemaConfigurable(myProject, schemaFilePath, info, myTreeUpdater, myNameCreator);
        configurable.setError(myError, true);
        addValue(null, configurable);
        if (myInReset) {
            myValueToSelect = configurable;
        }
        else {
            refreshTree(configurable);
        }
    }

    @RequiredUIAccess
    @Override
    protected void loadTree() {
        if (myProject.isDefault()) {
            return;
        }

        List<UserDefinedJsonSchemaConfiguration> list = getStoredList();
        for (UserDefinedJsonSchemaConfiguration info : list) {
            JsonSchemaConfigurable configurable =
                new JsonSchemaConfigurable(myProject, getSchemaFilePath(info.getRelativePathToSchema()), info, myTreeUpdater, myNameCreator);
            configurable.setError(myError, true);
            addValue(null, configurable);
        }
    }

    @RequiredUIAccess
    @Override
    protected @Nullable MasterDetailsConfigurable<?> createConfigurable(Object value) {
        return value instanceof JsonSchemaConfigurable configurable ? configurable : null;
    }

    @Override
    protected void renderValue(Object value, TextItemPresentation presentation) {
        presentation.append(getValueText(value));
    }

    @Override
    protected String getValueText(Object value) {
        if (value instanceof JsonSchemaConfigurable configurable) {
            return configurable.getDisplayName().get();
        }
        return String.valueOf(value);
    }

    @Override
    protected @Nullable Comparator<Object> getValueComparator() {
        return (o1, o2) -> {
            if (o1 instanceof JsonSchemaConfigurable c1 && o2 instanceof JsonSchemaConfigurable c2) {
                if (c1.getSchema().isApplicationDefined() != c2.getSchema().isApplicationDefined()) {
                    return c1.getSchema().isApplicationDefined() ? 1 : -1;
                }
            }
            return getValueText(o1).compareToIgnoreCase(getValueText(o2));
        };
    }

    private String getSchemaFilePath(String pathToSchema) {
        return isAbsoluteUrl(pathToSchema) || new File(pathToSchema).isAbsolute()
            ? pathToSchema
            : new File(myProject.getBasePath(), pathToSchema).getPath();
    }

    private List<UserDefinedJsonSchemaConfiguration> getStoredList() {
        List<UserDefinedJsonSchemaConfiguration> list = new ArrayList<>();
        Map<String, UserDefinedJsonSchemaConfiguration> projectState = JsonSchemaMappingsProjectConfiguration
            .getInstance(myProject).getStateMap();
        list.addAll(projectState.values());

        list.sort(COMPARATOR);
        return list;
    }

    @RequiredUIAccess
    @Override
    public void apply() throws ConfigurationException {
        List<UserDefinedJsonSchemaConfiguration> uiList = getUiList(true);
        validate(uiList);
        Map<String, UserDefinedJsonSchemaConfiguration> projectMap = new HashMap<>();
        for (UserDefinedJsonSchemaConfiguration info : uiList) {
            projectMap.put(info.getName(), info);
        }

        JsonSchemaMappingsProjectConfiguration.getInstance(myProject).setState(projectMap);
        Project[] projects = ProjectManager.getInstance().getOpenProjects();
        for (Project project : projects) {
            JsonSchemaService service = JsonSchemaService.Impl.get(project);
            service.reset();
        }
        DaemonCodeAnalyzer.getInstance(myProject).restart();
        EditorNotifications.getInstance(myProject).updateAllNotifications();
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(myProject);
        for (VirtualFile file : fileEditorManager.getOpenFiles()) {
            fileEditorManager.updateFilePresentation(file);
        }
    }

    private static void validate(List<UserDefinedJsonSchemaConfiguration> list) throws ConfigurationException {
        Set<String> set = new HashSet<>();
        for (UserDefinedJsonSchemaConfiguration info : list) {
            if (set.contains(info.getName())) {
                throw new ConfigurationException(JsonLocalize.schemaConfigurationErrorDuplicateName(info.getName()));
            }
            set.add(info.getName());
        }
    }

    @RequiredUIAccess
    @Override
    public boolean isModified() {
        List<UserDefinedJsonSchemaConfiguration> storedList = getStoredList();
        List<UserDefinedJsonSchemaConfiguration> uiList;
        try {
            uiList = getUiList(false);
        }
        catch (ConfigurationException e) {
            //will not happen
            return false;
        }
        return !storedList.equals(uiList);
    }

    @RequiredUIAccess
    private void refreshNodes() {
        for (Object value : getChildren(null)) {
            refreshValue(value);
        }
    }

    @RequiredUIAccess
    private void updateWarningText(boolean showWarning) {
        MultiMap<String, UserDefinedJsonSchemaConfiguration.Item> patternsMap = new MultiMap<>();
        List<LocalizeValue> conflicts = new ArrayList<>();
        List<UserDefinedJsonSchemaConfiguration> list;
        try {
            list = getUiList(false);
        }
        catch (ConfigurationException e) {
            // will not happen
            return;
        }
        for (UserDefinedJsonSchemaConfiguration info : list) {
            info.refreshPatterns();
            JsonSchemaPatternComparator comparator = new JsonSchemaPatternComparator(myProject);
            List<UserDefinedJsonSchemaConfiguration.Item> patterns = info.getPatterns();
            for (UserDefinedJsonSchemaConfiguration.Item pattern : patterns) {
                for (Map.Entry<String, Collection<UserDefinedJsonSchemaConfiguration.Item>> entry : patternsMap.entrySet()) {
                    for (UserDefinedJsonSchemaConfiguration.Item item : entry.getValue()) {
                        ThreeState similar = comparator.isSimilar(pattern, item);
                        if (ThreeState.NO.equals(similar)) {
                            continue;
                        }

                        conflicts.add(JsonLocalize.schemaConfigurationErrorConflictingMappingsDesc(
                            pattern.getPresentation(),
                            info.getName(),
                            item.getPresentation(),
                            entry.getKey()
                        ));
                    }
                }
            }
            patternsMap.put(info.getName(), patterns);
        }
        if (!conflicts.isEmpty()) {
            myError = JsonLocalize.schemaConfigurationErrorConflictingMappingsTitle(
                LocalizeValue.join("\n", conflicts.toArray(LocalizeValue[]::new))
            );
        }
        else {
            myError = LocalizeValue.empty();
        }
        for (Object o : getChildren(null)) {
            if (o instanceof JsonSchemaConfigurable configurable) {
                configurable.setError(myError, showWarning);
            }
        }
    }

    @RequiredUIAccess
    public void selectInTree(UserDefinedJsonSchemaConfiguration configuration) {
        for (Object value : getChildren(null)) {
            if (value instanceof JsonSchemaConfigurable configurable && Objects.equals(configurable.getUiSchema(), configuration)) {
                if (myInReset) {
                    myValueToSelect = configurable;
                }
                else {
                    select(configurable);
                }
            }
        }
    }

    @RequiredUIAccess
    private List<UserDefinedJsonSchemaConfiguration> getUiList(boolean applyChildren) throws ConfigurationException {
        List<UserDefinedJsonSchemaConfiguration> uiList = new ArrayList<>();
        for (Object value : getChildren(null)) {
            if (!(value instanceof JsonSchemaConfigurable configurable)) {
                continue;
            }
            if (applyChildren) {
                configurable.apply();
                uiList.add(configurable.getSchema());
            }
            else {
                uiList.add(configurable.getUiSchema());
            }
        }
        uiList.sort(COMPARATOR);
        return uiList;
    }

    @RequiredUIAccess
    public void setInitializer(Runnable initializer) {
        if (myLoaded && getTree() != null) {
            initializer.run();
            return;
        }
        myInitializer = initializer;
    }

    @RequiredUIAccess
    @Override
    public void reset() {
        myInReset = true;
        myValueToSelect = null;
        try {
            super.reset();
            updateWarningText(true);
            Runnable initializer = myInitializer;
            if (initializer != null) {
                myInitializer = null;
                initializer.run();
            }
        }
        finally {
            myInReset = false;
        }

        Object toSelect = myValueToSelect;
        myValueToSelect = null;
        if (toSelect == null) {
            Comparator<Object> comparator = getValueComparator();
            List<Object> children = getChildren(null);
            if (!children.isEmpty() && comparator != null) {
                toSelect = children.stream().min(comparator).orElse(null);
            }
        }
        myLoaded = true;
        refreshTree(toSelect);
    }

    @RequiredUIAccess
    @Override
    public void disposeUIResources() {
        myLoaded = false;
        super.disposeUIResources();
    }

    @Override
    public LocalizeValue getDisplayName() {
        return JsonLocalize.configurableJsonschemamappingsconfigurableDisplayName();
    }

    @Override
    public String getId() {
        return SETTINGS_JSON_SCHEMA;
    }

    @Override
    public @Nullable String getParentId() {
        return StandardConfigurableIds.EDITOR_GROUP;
    }

    @Override
    public String getHelpTopic() {
        return SETTINGS_JSON_SCHEMA;
    }

    private final class MyAddAction extends DumbAwareAction {
        private MyAddAction() {
            super(
                JsonLocalize.actionDumbawareJsonschemamappingsconfigurableTextAdd(),
                JsonLocalize.actionDumbawareJsonschemamappingsconfigurableDescriptionAdd(),
                PlatformIconGroup.generalAdd()
            );
        }

        @RequiredUIAccess
        @Override
        public void actionPerformed(AnActionEvent e) {
            addProjectSchema();
        }
    }

    private final class MyDeleteAction extends DumbAwareAction implements AnActionWithSyncUpdate {
        private MyDeleteAction() {
            super(CommonLocalize.buttonRemove(), LocalizeValue.empty(), PlatformIconGroup.generalRemove());
        }

        @RequiredUIAccess
        @Override
        public void actionPerformed(AnActionEvent e) {
            if (getSelectedValues().isEmpty()) {
                return;
            }
            removeSelected();
            updateWarningText(true);
        }

        @Override
        public void update(AnActionEvent e) {
            e.getPresentation().setEnabled(!getSelectedValues().isEmpty());
        }
    }

    private final class MyCopyAction extends DumbAwareAction implements AnActionWithSyncUpdate {
        private MyCopyAction() {
            super(CommonLocalize.buttonCopy().removeEllipsis(), LocalizeValue.empty(), PlatformIconGroup.actionsCopy());
        }

        @RequiredUIAccess
        @Override
        public void actionPerformed(AnActionEvent e) {
            if (!(getSelectedValue() instanceof JsonSchemaConfigurable selected)) {
                return;
            }
            UserDefinedJsonSchemaConfiguration source = selected.getUiSchema();
            List<UserDefinedJsonSchemaConfiguration.Item> patterns = new ArrayList<>();
            for (UserDefinedJsonSchemaConfiguration.Item item : source.getPatterns()) {
                patterns.add(new UserDefinedJsonSchemaConfiguration.Item(item.path, item.mappingKind));
            }
            UserDefinedJsonSchemaConfiguration copy = new UserDefinedJsonSchemaConfiguration(
                createUniqueName(source.getName()),
                source.getSchemaVersion(),
                source.getRelativePathToSchema(),
                false,
                patterns
            );
            addCreatedMappings(copy, getSchemaFilePath(copy.getRelativePathToSchema()));
            updateWarningText(true);
        }

        @Override
        public void update(AnActionEvent e) {
            e.getPresentation().setEnabled(getSelectedValues().size() == 1 && getSelectedValue() instanceof JsonSchemaConfigurable);
        }
    }
}
