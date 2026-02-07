// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.jsonLines;

import com.intellij.json.JsonFileType;
import consulo.json.icon.JsonIconGroup;
import consulo.json.localize.JsonLocalize;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;

public final class JsonLinesFileType extends JsonFileType {
    @Nonnull
    public static final JsonLinesFileType INSTANCE = new JsonLinesFileType();

    private JsonLinesFileType() {
        super(JsonLinesLanguage.INSTANCE);
    }

    @Override
    @Nonnull
    public String getId() {
        return "JSON-lines";
    }

    @Override
    @Nonnull
    public consulo.localize.LocalizeValue getDescription() {
        return JsonLocalize.filetypeJson_linesDescription();
    }

    @Override
    @Nonnull
    public String getDefaultExtension() {
        return "jsonl";
    }

    @Override
    @Nonnull
    public Image getIcon() {
        return JsonIconGroup.json();
    }
}
