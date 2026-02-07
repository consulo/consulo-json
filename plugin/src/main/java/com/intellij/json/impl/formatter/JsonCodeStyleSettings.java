// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.formatter;

import com.intellij.json.JsonLanguage;
import consulo.json.localize.JsonLocalize;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.language.codeStyle.CustomCodeStyleSettings;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;
import org.intellij.lang.annotations.MagicConstant;

/**
 * @author Mikhail Golubev
 */
public final class JsonCodeStyleSettings extends CustomCodeStyleSettings {

    public static final int DO_NOT_ALIGN_PROPERTY = PropertyAlignment.DO_NOT_ALIGN.getId();
    public static final int ALIGN_PROPERTY_ON_VALUE = PropertyAlignment.ALIGN_ON_VALUE.getId();
    public static final int ALIGN_PROPERTY_ON_COLON = PropertyAlignment.ALIGN_ON_COLON.getId();

    public boolean SPACE_AFTER_COLON = true;
    public boolean SPACE_BEFORE_COLON = false;
    public boolean KEEP_TRAILING_COMMA = false;

    // TODO: check whether it's possible to migrate CustomCodeStyleSettings to newer com.intellij.util.xmlb.XmlSerializer
    /**
     * Contains value of {@link JsonCodeStyleSettings.PropertyAlignment#getId()}
     *
     * @see #DO_NOT_ALIGN_PROPERTY
     * @see #ALIGN_PROPERTY_ON_VALUE
     * @see #ALIGN_PROPERTY_ON_COLON
     */
    public int PROPERTY_ALIGNMENT = PropertyAlignment.DO_NOT_ALIGN.getId();

    @MagicConstant(flags = {
        CommonCodeStyleSettings.DO_NOT_WRAP,
        CommonCodeStyleSettings.WRAP_ALWAYS,
        CommonCodeStyleSettings.WRAP_AS_NEEDED,
        CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    })
    //@CommonCodeStyleSettings.WrapConstant
    public int OBJECT_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS;

    // This was default policy for array elements wrapping in JavaScript's JSON.
    // CHOP_DOWN_IF_LONG seems more appropriate however for short arrays.
    @MagicConstant(flags = {
        CommonCodeStyleSettings.DO_NOT_WRAP,
        CommonCodeStyleSettings.WRAP_ALWAYS,
        CommonCodeStyleSettings.WRAP_AS_NEEDED,
        CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM
    })
    //@CommonCodeStyleSettings.WrapConstant
    public int ARRAY_WRAPPING = CommonCodeStyleSettings.WRAP_ALWAYS;

    public JsonCodeStyleSettings(CodeStyleSettings container) {
        super(JsonLanguage.INSTANCE.getID(), container);
    }

    public enum PropertyAlignment {
        DO_NOT_ALIGN(0, JsonLocalize.formatterAlignPropertiesNone()),
        ALIGN_ON_VALUE(1, JsonLocalize.formatterAlignPropertiesOnValue()),
        ALIGN_ON_COLON(2, JsonLocalize.formatterAlignPropertiesOnColon());

        private final int myId;
        @Nonnull
        private final LocalizeValue myText;

        PropertyAlignment(int id, @Nonnull LocalizeValue text) {
            myText = text;
            myId = id;
        }

        public @Nonnull String getDescription() {
            return myText.get();
        }

        public int getId() {
            return myId;
        }
    }
}
