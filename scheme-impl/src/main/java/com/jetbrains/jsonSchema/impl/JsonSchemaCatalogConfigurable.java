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
package com.jetbrains.jsonSchema.impl;

import com.jetbrains.jsonSchema.internal.JsonSchemaCatalogProjectConfiguration;
import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.NonDefaultProjectConfigurable;
import consulo.configurable.ProjectConfigurable;
import consulo.configurable.SimpleConfigurable;
import consulo.disposer.Disposable;
import consulo.json.localize.JsonLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.Platform;
import consulo.project.Project;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.HtmlLabel;
import consulo.ui.Label;
import consulo.ui.LabelStyle;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.event.HyperlinkEvent;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.style.ComponentColors;
import consulo.ui.util.Indenter;
import consulo.util.lang.StringUtil;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

@ExtensionImpl
public class JsonSchemaCatalogConfigurable extends SimpleConfigurable<JsonSchemaCatalogConfigurable.Panel>
    implements ProjectConfigurable, NonDefaultProjectConfigurable {
    public static final String ID = "settings.json.schema.catalog";

    protected static final class Panel implements Supplier<Component> {
        private final VerticalLayout myRoot;
        private final CheckBox myRemoteCheckBox;
        private final CheckBox myCatalogCheckBox;
        private final CheckBox myImplicitSchemasCheckBox;
        private final CheckBox myPreferRemoteCheckBox;
        private final List<Component> myDependentComponents;

        @RequiredUIAccess
        private Panel() {
            myRemoteCheckBox = CheckBox.create(JsonLocalize.checkboxAllowDownloadingJsonSchemasFromRemoteSources());
            myCatalogCheckBox = CheckBox.create(JsonLocalize.checkboxUseSchemastoreOrgJsonSchemaCatalog());
            myImplicitSchemasCheckBox = CheckBox.create(JsonLocalize.checkboxAutomaticallyApplySchemasForKnownFileNames());
            myPreferRemoteCheckBox = CheckBox.create(JsonLocalize.checkboxAlwaysDownloadTheMostRecentVersionOfSchemas());

            HtmlLabel catalogComment = HtmlLabel.create(JsonLocalize.schemaCatalogHint());
            comment(catalogComment);
            catalogComment.addHyperlinkListener(Panel::openLink);
            Label implicitComment = comment(Label.create(JsonLocalize.schemaCatalogImplicitHint()));
            Label remoteComment = comment(Label.create(JsonLocalize.schemaCatalogRemoteHint()));

            myDependentComponents = List.of(myCatalogCheckBox,
                catalogComment,
                myImplicitSchemasCheckBox,
                implicitComment,
                myPreferRemoteCheckBox,
                remoteComment);

            VerticalLayout dependent = VerticalLayout.create();
            dependent.add(myCatalogCheckBox);
            dependent.add(Indenter.indent(catalogComment));
            dependent.add(myImplicitSchemasCheckBox);
            dependent.add(Indenter.indent(implicitComment));
            dependent.add(myPreferRemoteCheckBox);
            dependent.add(Indenter.indent(remoteComment));

            myRoot = VerticalLayout.create();
            myRoot.add(myRemoteCheckBox);
            myRoot.add(Indenter.indent(dependent));

            myRemoteCheckBox.addValueListener(event -> {
                boolean selected = Boolean.TRUE.equals(event.getValue());
                if (!selected) {
                    myCatalogCheckBox.setValue(false);
                    myImplicitSchemasCheckBox.setValue(false);
                    myPreferRemoteCheckBox.setValue(false);
                }
                updateEnabled();
            });
        }

        @RequiredUIAccess
        private void updateEnabled() {
            boolean enabled = myRemoteCheckBox.getValueOrError();
            for (Component component : myDependentComponents) {
                component.setEnabled(enabled);
            }
        }

        @RequiredUIAccess
        private static Label comment(Label label) {
            label.addStyle(LabelStyle.FONT_SMALL);
            label.setForegroundColor(ComponentColors.DISABLED_TEXT);
            return label;
        }

        private static void openLink(HyperlinkEvent event) {
            String href = event.getDescription();
            if (!StringUtil.isEmpty(href)) {
                Platform.current().openInBrowser(href);
            }
        }

        @Override
        public Component get() {
            return myRoot;
        }
    }

    private final Project myProject;

    @Inject
    public JsonSchemaCatalogConfigurable(Project project) {
        myProject = project;
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public LocalizeValue getDisplayName() {
        return JsonLocalize.configurableJsonschemacatalogconfigurableDisplayName();
    }

    @Override
    public @Nullable String getParentId() {
        return "settings.json.schema";
    }

    @RequiredUIAccess
    @Override
    protected Panel createPanel(Disposable uiDisposable) {
        return new Panel();
    }

    @RequiredUIAccess
    @Override
    protected boolean isModified(Panel panel) {
        JsonSchemaCatalogProjectConfiguration.MyState state = JsonSchemaCatalogProjectConfiguration.getInstance(myProject).getState();
        return state == null
            || state.myIsCatalogEnabled != panel.myCatalogCheckBox.getValueOrError()
            || state.myIsImplicitSchemasEnabled != panel.myImplicitSchemasCheckBox.getValueOrError()
            || state.myIsPreferRemoteSchemas != panel.myPreferRemoteCheckBox.getValueOrError()
            || state.myIsRemoteActivityEnabled != panel.myRemoteCheckBox.getValueOrError();
    }

    @RequiredUIAccess
    @Override
    protected void reset(Panel panel) {
        JsonSchemaCatalogProjectConfiguration.MyState state = JsonSchemaCatalogProjectConfiguration.getInstance(myProject).getState();
        panel.myRemoteCheckBox.setValue(state == null || state.myIsRemoteActivityEnabled, false);
        panel.myCatalogCheckBox.setValue(state == null || state.myIsCatalogEnabled);
        panel.myImplicitSchemasCheckBox.setValue(state == null || state.myIsImplicitSchemasEnabled);
        panel.myPreferRemoteCheckBox.setValue(state == null || state.myIsPreferRemoteSchemas);
        panel.updateEnabled();
    }

    @RequiredUIAccess
    @Override
    protected void apply(Panel panel) {
        JsonSchemaCatalogProjectConfiguration.getInstance(myProject).setState(panel.myCatalogCheckBox.getValueOrError(),
            panel.myRemoteCheckBox.getValueOrError(),
            panel.myPreferRemoteCheckBox.getValueOrError(),
            panel.myImplicitSchemasCheckBox.getValueOrError());
    }
}
