// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.formatter;

import com.intellij.json.JsonLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.json.localize.JsonLocalize;
import consulo.language.Language;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CustomCodeStyleSettings;
import consulo.language.codeStyle.setting.CodeStyleSettingsCustomizable;
import consulo.language.codeStyle.setting.IndentOptionsEditor;
import consulo.language.codeStyle.setting.LanguageCodeStyleSettingsProvider;
import consulo.language.codeStyle.ui.setting.SmartIndentOptionsEditor;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
public final class JsonLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
    private static final class Holder {
        private static final String[] ALIGN_OPTIONS = Arrays.stream(JsonCodeStyleSettings.PropertyAlignment.values())
            .map(alignment -> alignment.getDescription())
            .toArray(value -> new String[value]);

        private static final int[] ALIGN_VALUES =
            ArrayUtil.toIntArray(
                ContainerUtil.map(JsonCodeStyleSettings.PropertyAlignment.values(), alignment -> alignment.getId()));

        private static final String SAMPLE = """
            {
                "json literals are": {
                    "strings": ["foo", "bar", "\\u0062\\u0061\\u0072"],
                    "numbers": [42, 6.62606975e-34],
                    "boolean values": [true, false,],
                    "objects": {"null": null,"another": null,}
                }
            }""";
    }

    @Override
    public void customizeSettings(@Nonnull CodeStyleSettingsCustomizable consumer, @Nonnull SettingsType settingsType) {
        if (settingsType == SettingsType.SPACING_SETTINGS) {
            consumer.showStandardOptions("SPACE_WITHIN_BRACKETS",
                "SPACE_WITHIN_BRACES",
                "SPACE_AFTER_COMMA",
                "SPACE_BEFORE_COMMA");
            consumer.renameStandardOption("SPACE_WITHIN_BRACES", JsonLocalize.formatterSpace_within_bracesLabel().get());
            consumer.showCustomOption(JsonCodeStyleSettings.class, "SPACE_BEFORE_COLON", JsonLocalize.formatterSpace_before_colonLabel().get(), "SPACES_OTHER");
            consumer.showCustomOption(JsonCodeStyleSettings.class, "SPACE_AFTER_COLON", JsonLocalize.formatterSpace_after_colonLabel().get(), "SPACES_OTHER");
        }
        else if (settingsType == SettingsType.BLANK_LINES_SETTINGS) {
            consumer.showStandardOptions("KEEP_BLANK_LINES_IN_CODE");
        }
        else if (settingsType == SettingsType.WRAPPING_AND_BRACES_SETTINGS) {
            consumer.showStandardOptions("RIGHT_MARGIN",
                "WRAP_ON_TYPING",
                "KEEP_LINE_BREAKS",
                "WRAP_LONG_LINES");

            consumer.showCustomOption(JsonCodeStyleSettings.class,
                "KEEP_TRAILING_COMMA",
                JsonLocalize.formatterTrailing_commaLabel().get(),
                "WRAPPING_KEEP");

            consumer.showCustomOption(JsonCodeStyleSettings.class,
                "ARRAY_WRAPPING",
                JsonLocalize.formatterWrapping_arraysLabel().get(),
                null,
                "WRAP_OPTIONS",
                CodeStyleSettingsCustomizable.WRAP_VALUES);

            consumer.showCustomOption(JsonCodeStyleSettings.class,
                "OBJECT_WRAPPING",
                JsonLocalize.formatterObjectsLabel().get(),
                null,
                "WRAP_OPTIONS",
                CodeStyleSettingsCustomizable.WRAP_VALUES);

            consumer.showCustomOption(JsonCodeStyleSettings.class,
                "PROPERTY_ALIGNMENT",
                JsonLocalize.formatterAlignPropertiesCaption().get(),
                JsonLocalize.formatterObjectsLabel().get(),
                Holder.ALIGN_OPTIONS,
                Holder.ALIGN_VALUES);

        }
    }

    @Override
    public @Nonnull Language getLanguage() {
        return JsonLanguage.INSTANCE;
    }

    @Override
    public @Nullable IndentOptionsEditor getIndentOptionsEditor() {
        return new SmartIndentOptionsEditor();
    }

    @Override
    public String getCodeSample(@Nonnull SettingsType settingsType) {
        return Holder.SAMPLE;
    }

//  @Override
//  protected void customizeDefaults(@Nonnull CommonCodeStyleSettings commonSettings,
//                                   @Nonnull CommonCodeStyleSettings.IndentOptions indentOptions) {
//    indentOptions.INDENT_SIZE = 2;
//    // strip all blank lines by default
//    commonSettings.KEEP_BLANK_LINES_IN_CODE = 0;
//  }

//  @Override
//  public @Nullable CodeStyleFieldAccessor getAccessor(@Nonnull Object codeStyleObject, @Nonnull Field field) {
//    if (codeStyleObject instanceof JsonCodeStyleSettings && field.getName().equals("PROPERTY_ALIGNMENT")) {
//      return new MagicIntegerConstAccessor(
//        codeStyleObject, field,
//        new int[] {
//          JsonCodeStyleSettings.PropertyAlignment.DO_NOT_ALIGN.getId(),
//          JsonCodeStyleSettings.PropertyAlignment.ALIGN_ON_VALUE.getId(),
//          JsonCodeStyleSettings.PropertyAlignment.ALIGN_ON_COLON.getId()
//        },
//        new String[] {
//          "do_not_align",
//          "align_on_value",
//          "align_on_colon"
//        }
//      );
//    }
//    return null;
//  }

    @Override
    public @Nonnull CustomCodeStyleSettings createCustomSettings(@Nonnull CodeStyleSettings settings) {
        return new JsonCodeStyleSettings(settings);
    }
}
