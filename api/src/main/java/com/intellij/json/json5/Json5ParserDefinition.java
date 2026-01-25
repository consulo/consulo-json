// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.json5;

import com.intellij.json.JsonFileElementTypes;
import com.intellij.json.JsonParserDefinition;
import com.intellij.json.psi.impl.JsonFileImpl;
import com.intellij.json.syntax.JsonSyntaxParser;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.IFileElementType;
import consulo.language.file.FileViewProvider;
import consulo.language.lexer.Lexer;
import consulo.language.parser.PsiParser;
import consulo.language.psi.PsiFile;
import consulo.language.version.LanguageVersion;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public class Json5ParserDefinition extends JsonParserDefinition {
    @Nonnull
    @Override
    public Language getLanguage() {
        return Json5Language.INSTANCE;
    }

    @Nonnull
    @Override
    public PsiParser createParser(LanguageVersion languageVersion) {
        return new JsonSyntaxParser();
    }

    @Override
    @Nonnull
    public Lexer createLexer(LanguageVersion languageVersion) {
        return new Json5Lexer();
    }

    @Override
    @Nonnull
    public PsiFile createFile(@Nonnull FileViewProvider fileViewProvider) {
        return new JsonFileImpl(fileViewProvider, Json5Language.INSTANCE);
    }

    @Override
    @Nonnull
    public IFileElementType getFileNodeType() {
        return JsonFileElementTypes.JSON5_FILE;
    }
}
