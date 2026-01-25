// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.jsonLines;

import com.intellij.json.JsonFileElementTypes;
import com.intellij.json.JsonParserDefinition;
import com.intellij.json.psi.impl.JsonFileImpl;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.IFileElementType;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class JsonLinesParserDefinition extends JsonParserDefinition {
    @Nonnull
    @Override
    public Language getLanguage() {
        return JsonLinesLanguage.INSTANCE;
    }

    @Override
    @Nonnull
    public PsiFile createFile(@Nonnull FileViewProvider fileViewProvider) {
        return new JsonFileImpl(fileViewProvider, JsonLinesLanguage.INSTANCE);
    }

    @Override
    @Nonnull
    public IFileElementType getFileNodeType() {
        return JsonFileElementTypes.JSON_LINES_FILE;
    }
}
