// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.json5;

import com.intellij.json.JsonFileType;
import consulo.json.localize.JsonLocalize;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

public final class Json5FileType extends JsonFileType {
    public static final Json5FileType INSTANCE = new Json5FileType();
    public static final String DEFAULT_EXTENSION = "json5";

    private Json5FileType() {
        super(Json5Language.INSTANCE);
    }

    @Override
    @Nonnull
    public String getId() {
        return "JSON5";
    }

    @Override
    @Nonnull
    public LocalizeValue getDescription() {
        return JsonLocalize.filetypeJson5Description();
    }

    @Override
    public @Nonnull String getDefaultExtension() {
        return DEFAULT_EXTENSION;
    }
}
