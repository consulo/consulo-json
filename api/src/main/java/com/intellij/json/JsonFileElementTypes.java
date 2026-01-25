// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.json.json5.Json5Language;
import com.intellij.json.jsonLines.JsonLinesLanguage;
import consulo.language.ast.IFileElementType;

public class JsonFileElementTypes {
    public static final IFileElementType JSON_FILE = new IFileElementType(JsonLanguage.INSTANCE);

    public static final IFileElementType JSON5_FILE = new IFileElementType(Json5Language.INSTANCE);

    public static final IFileElementType JSON_LINES_FILE = new IFileElementType(JsonLinesLanguage.INSTANCE);

    private JsonFileElementTypes() {
    }
}
