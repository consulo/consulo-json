// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.json.psi.impl.JsonFileImpl;
import com.intellij.json.syntax.JsonSyntaxParser;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IFileElementType;
import consulo.language.ast.TokenSet;
import consulo.language.file.FileViewProvider;
import consulo.language.lexer.Lexer;
import consulo.language.parser.ParserDefinition;
import consulo.language.parser.PsiParser;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.version.LanguageVersion;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public class JsonParserDefinition implements ParserDefinition {

    @Nonnull
    @Override
    public Language getLanguage() {
        return JsonLanguage.INSTANCE;
    }

    @Override
    @Nonnull
    public Lexer createLexer(LanguageVersion languageVersion) {
        return new JsonLexer();
    }

    @Override
    @Nonnull
    public PsiParser createParser(LanguageVersion languageVersion) {
        return new JsonSyntaxParser();
    }

    @Override
    @Nonnull
    public IFileElementType getFileNodeType() {
        return JsonFileElementTypes.JSON_FILE;
    }

    @Override
    @Nonnull
    public TokenSet getCommentTokens(LanguageVersion languageVersion) {
        return JsonTokenSets.JSON_COMMENTARIES;
    }

    @Override
    @Nonnull
    public TokenSet getStringLiteralElements(LanguageVersion languageVersion) {
        return JsonTokenSets.STRING_LITERALS;
    }

    @Override
    @Nonnull
    public PsiElement createElement(@Nonnull ASTNode astNode) {
        return JsonElementTypes.Factory.createElement(astNode);
    }

    @Override
    @Nonnull
    public PsiFile createFile(@Nonnull FileViewProvider fileViewProvider) {
        return new JsonFileImpl(fileViewProvider, JsonLanguage.INSTANCE);
    }

    @Override
    @Nonnull
    public SpaceRequirements spaceExistenceTypeBetweenTokens(@Nullable ASTNode left, @Nullable ASTNode right) {
        return SpaceRequirements.MAY;
    }
}
