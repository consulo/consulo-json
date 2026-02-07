// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.editor;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.ApplicationManager;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.util.xml.serializer.XmlSerializerUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

@State(
    name = "JsonEditorOptions",
    storages = @Storage("editor.xml")
)
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@Singleton
public final class JsonEditorOptions implements PersistentStateComponent<JsonEditorOptions> {
    public boolean COMMA_ON_ENTER = true;
    public boolean COMMA_ON_MATCHING_BRACES = true;
    public boolean COMMA_ON_PASTE = true;
    public boolean AUTO_QUOTE_PROP_NAME = true;
    public boolean AUTO_WHITESPACE_AFTER_COLON = true;
    public boolean ESCAPE_PASTED_TEXT = true;
    public boolean COLON_MOVE_OUTSIDE_QUOTES = false;
    public boolean COMMA_MOVE_OUTSIDE_QUOTES = false;

    @Override
    public @Nullable JsonEditorOptions getState() {
        return this;
    }

    @Override
    public void loadState(@Nonnull JsonEditorOptions state) {
        XmlSerializerUtil.copyBean(state, this);
    }

    public static JsonEditorOptions getInstance() {
        return ApplicationManager.getApplication().getService(JsonEditorOptions.class);
    }

    public boolean isCommaOnEnter() {
        return COMMA_ON_ENTER;
    }

    public void setCommaOnEnter(boolean value) {
        COMMA_ON_ENTER = value;
    }

    public boolean isCommaOnMatchingBraces() {
        return COMMA_ON_MATCHING_BRACES;
    }

    public void setCommaOnMatchingBraces(boolean value) {
        COMMA_ON_MATCHING_BRACES = value;
    }

    public boolean isCommaOnPaste() {
        return COMMA_ON_PASTE;
    }

    public void setCommaOnPaste(boolean value) {
        COMMA_ON_PASTE = value;
    }

    public boolean isAutoQuotePropName() {
        return AUTO_QUOTE_PROP_NAME;
    }

    public void setAutoQuotePropName(boolean value) {
        AUTO_QUOTE_PROP_NAME = value;
    }

    public boolean isAutoWhitespaceAfterColon() {
        return AUTO_WHITESPACE_AFTER_COLON;
    }

    public void setAutoWhitespaceAfterColon(boolean value) {
        AUTO_WHITESPACE_AFTER_COLON = value;
    }

    public boolean isEscapePastedText() {
        return ESCAPE_PASTED_TEXT;
    }

    public void setEscapePastedText(boolean value) {
        ESCAPE_PASTED_TEXT = value;
    }

    public boolean isColonMoveOutsideQuotes() {
        return COLON_MOVE_OUTSIDE_QUOTES;
    }

    public void setColonMoveOutsideQuotes(boolean value) {
        COLON_MOVE_OUTSIDE_QUOTES = value;
    }

    public boolean isCommaMoveOutsideQuotes() {
        return COMMA_MOVE_OUTSIDE_QUOTES;
    }

    public void setCommaMoveOutsideQuotes(boolean value) {
        COMMA_MOVE_OUTSIDE_QUOTES = value;
    }
}
