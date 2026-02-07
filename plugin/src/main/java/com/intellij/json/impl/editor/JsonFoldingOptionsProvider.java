package com.intellij.json.impl.editor;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ApplicationConfigurable;
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.disposer.Disposable;
import consulo.json.localize.JsonLocalize;
import consulo.localize.LocalizeValue;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 2026-02-05
 */
@ExtensionImpl
public class JsonFoldingOptionsProvider extends SimpleConfigurableByProperties implements ApplicationConfigurable {
    @RequiredUIAccess
    @Nonnull
    @Override
    protected Component createLayout(@Nonnull PropertyBuilder propertyBuilder, @Nonnull Disposable disposable) {
        VerticalLayout layout = VerticalLayout.create();

        JsonFoldingSettings settings = JsonFoldingSettings.getInstance();

        CheckBox showKeys = CheckBox.create(JsonLocalize.jsonfoldingsettingsShowFirstKey());
        propertyBuilder.add(showKeys, settings::isShowFirstKey, settings::setShowFirstKey);
        layout.add(showKeys);

        CheckBox showCount = CheckBox.create(JsonLocalize.jsonfoldingsettingsShowKeyCount());
        propertyBuilder.add(showCount, settings::isShowKeyCount, settings::setShowKeyCount);
        layout.add(showCount);

        return layout;
    }

    @Nonnull
    @Override
    public String getId() {
        return "editor.preferences.folding.json";
    }

    @Nullable
    @Override
    public String getParentId() {
        return "editor.preferences.folding";
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return JsonLocalize.jsonfoldingsettingsTitle();
    }
}
