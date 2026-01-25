// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.formatter;

import com.intellij.json.JsonLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.Configurable;
import consulo.json.localize.JsonLocalize;
import consulo.language.Language;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CustomCodeStyleSettings;
import consulo.language.codeStyle.setting.CodeStyleSettingsProvider;
import consulo.language.codeStyle.ui.setting.CodeStyleAbstractConfigurable;
import consulo.language.codeStyle.ui.setting.CodeStyleAbstractPanel;
import consulo.language.codeStyle.ui.setting.TabbedLanguageCodeStylePanel;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
public class JsonCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
    @Override
    public @Nonnull LocalizeValue getConfigurableDisplayName() {
        return JsonLanguage.INSTANCE.getDisplayName();
    }

    @Override
    public @Nonnull CustomCodeStyleSettings createCustomSettings(@Nonnull CodeStyleSettings settings) {
        return new JsonCodeStyleSettings(settings);
    }

    @Nonnull
    @Override
    public Configurable createSettingsPage(CodeStyleSettings settings, CodeStyleSettings originalSettings) {
        return new CodeStyleAbstractConfigurable(settings, originalSettings, JsonLocalize.settingsDisplayNameJson()) {
            @Override
            protected @Nonnull CodeStyleAbstractPanel createPanel(@Nonnull CodeStyleSettings settings) {
                final Language language = JsonLanguage.INSTANCE;
                final CodeStyleSettings currentSettings = getCurrentSettings();
                return new TabbedLanguageCodeStylePanel(language, currentSettings, settings) {
                    @Override
                    protected void initTabs(CodeStyleSettings settings) {
                        addIndentOptionsTab(settings);
                        addSpacesTab(settings);
                        addBlankLinesTab(settings);
                        addWrappingAndBracesTab(settings);
                    }
                };
            }

            @Override
            public @Nonnull String getHelpTopic() {
                return "reference.settingsdialog.codestyle.json";
            }
        };
    }

    @Override
    public @Nonnull Language getLanguage() {
        return JsonLanguage.INSTANCE;
    }
}
