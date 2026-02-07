// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.editor;

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

@Singleton
@State(name = "JsonFoldingSettings", storages = @Storage("editor.xml"))
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
public class JsonFoldingSettings implements PersistentStateComponent<JsonFoldingSettings> {

    @Nonnull
    public static JsonFoldingSettings getInstance() {
        return ApplicationManager.getApplication().getService(JsonFoldingSettings.class);
    }

    public boolean showKeyCount = true;

    public boolean showFirstKey = false;

    public boolean isShowKeyCount() {
        return showKeyCount;
    }

    public void setShowKeyCount(boolean showKeyCount) {
        this.showKeyCount = showKeyCount;
    }

    public boolean isShowFirstKey() {
        return showFirstKey;
    }

    public void setShowFirstKey(boolean showFirstKey) {
        this.showFirstKey = showFirstKey;
    }

    @Nullable
    @Override
    public JsonFoldingSettings getState() {
        return this;
    }

    @Override
    public void loadState(@Nonnull JsonFoldingSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}

