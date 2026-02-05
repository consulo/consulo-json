// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.highlighting;

import com.intellij.json.JsonElementTypes;
import com.intellij.json.JsonFileType;
import com.intellij.json.JsonLanguage;
import com.intellij.json.JsonLexer;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.HighlighterColors;
import consulo.colorScheme.TextAttributesKey;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.ast.StringEscapesTokenTypes;
import consulo.language.ast.TokenType;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.language.editor.highlight.SyntaxHighlighterBase;
import consulo.language.editor.highlight.SyntaxHighlighterFactory;
import consulo.language.lexer.Lexer;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import static consulo.codeEditor.DefaultLanguageHighlighterColors.*;

@ExtensionImpl
public class JsonSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
    public static final TextAttributesKey JSON_BRACKETS = TextAttributesKey.createTextAttributesKey("JSON.BRACKETS", BRACKETS);
    public static final TextAttributesKey JSON_BRACES = TextAttributesKey.createTextAttributesKey("JSON.BRACES", BRACES);
    public static final TextAttributesKey JSON_COMMA = TextAttributesKey.createTextAttributesKey("JSON.COMMA", COMMA);
    public static final TextAttributesKey JSON_COLON = TextAttributesKey.createTextAttributesKey("JSON.COLON", SEMICOLON);
    public static final TextAttributesKey JSON_NUMBER = TextAttributesKey.createTextAttributesKey("JSON.NUMBER", NUMBER);
    public static final TextAttributesKey JSON_STRING = TextAttributesKey.createTextAttributesKey("JSON.STRING", STRING);
    public static final TextAttributesKey JSON_KEYWORD = TextAttributesKey.createTextAttributesKey("JSON.KEYWORD", KEYWORD);
    public static final TextAttributesKey JSON_LINE_COMMENT = TextAttributesKey.createTextAttributesKey("JSON.LINE_COMMENT", LINE_COMMENT);
    public static final TextAttributesKey JSON_BLOCK_COMMENT = TextAttributesKey.createTextAttributesKey("JSON.BLOCK_COMMENT", BLOCK_COMMENT);

    // Artificial element type
    public static final TextAttributesKey JSON_IDENTIFIER = TextAttributesKey.createTextAttributesKey("JSON.IDENTIFIER", IDENTIFIER);

    // Added by annotators
    public static final TextAttributesKey JSON_PROPERTY_KEY = TextAttributesKey.createTextAttributesKey("JSON.PROPERTY_KEY", INSTANCE_FIELD);

    // String escapes
    public static final TextAttributesKey JSON_VALID_ESCAPE =
        TextAttributesKey.createTextAttributesKey("JSON.VALID_ESCAPE", VALID_STRING_ESCAPE);
    public static final TextAttributesKey JSON_INVALID_ESCAPE =
        TextAttributesKey.createTextAttributesKey("JSON.INVALID_ESCAPE", INVALID_STRING_ESCAPE);

    public static final TextAttributesKey JSON_PARAMETER = TextAttributesKey.createTextAttributesKey("JSON.PARAMETER", KEYWORD);


    @Override
    public @Nonnull SyntaxHighlighter getSyntaxHighlighter(@Nullable Project project, @Nullable VirtualFile virtualFile) {
        return new MyHighlighter(virtualFile);
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return JsonLanguage.INSTANCE;
    }

    private final class MyHighlighter extends SyntaxHighlighterBase {
        private final Map<IElementType, TextAttributesKey> ourAttributes = new HashMap<>();

        private final @Nullable VirtualFile myFile;

        {
            fillMap(ourAttributes, JSON_BRACES, JsonElementTypes.L_CURLY, JsonElementTypes.R_CURLY);
            fillMap(ourAttributes, JSON_BRACKETS, JsonElementTypes.L_BRACKET, JsonElementTypes.R_BRACKET);
            fillMap(ourAttributes, JSON_COMMA, JsonElementTypes.COMMA);
            fillMap(ourAttributes, JSON_COLON, JsonElementTypes.COLON);
            fillMap(ourAttributes, JSON_STRING, JsonElementTypes.DOUBLE_QUOTED_STRING);
            fillMap(ourAttributes, JSON_STRING, JsonElementTypes.SINGLE_QUOTED_STRING);
            fillMap(ourAttributes, JSON_NUMBER, JsonElementTypes.NUMBER);
            fillMap(ourAttributes, JSON_KEYWORD, JsonElementTypes.TRUE, JsonElementTypes.FALSE, JsonElementTypes.NULL);
            fillMap(ourAttributes, JSON_LINE_COMMENT, JsonElementTypes.LINE_COMMENT);
            fillMap(ourAttributes, JSON_BLOCK_COMMENT, JsonElementTypes.BLOCK_COMMENT);
            // TODO may be it's worth to add more sensible highlighting for identifiers
            fillMap(ourAttributes, JSON_IDENTIFIER, JsonElementTypes.IDENTIFIER);
            fillMap(ourAttributes, HighlighterColors.BAD_CHARACTER, TokenType.BAD_CHARACTER);

            fillMap(ourAttributes, JSON_VALID_ESCAPE, StringEscapesTokenTypes.VALID_STRING_ESCAPE_TOKEN);
            fillMap(ourAttributes, JSON_INVALID_ESCAPE, StringEscapesTokenTypes.INVALID_CHARACTER_ESCAPE_TOKEN);
            fillMap(ourAttributes, JSON_INVALID_ESCAPE, StringEscapesTokenTypes.INVALID_UNICODE_ESCAPE_TOKEN);
        }

        MyHighlighter(@Nullable VirtualFile file) {
            myFile = file;
        }

        @Override
        public @Nonnull Lexer getHighlightingLexer() {
            return new JsonHighlightingLexer(isPermissiveDialect(), isCanEscapeEol(), getLexer());
        }

        private boolean isPermissiveDialect() {
            FileType fileType = myFile == null ? null : myFile.getFileType();
            boolean isPermissiveDialect = false;
            if (fileType instanceof JsonFileType) {
                Language language = ((JsonFileType) fileType).getLanguage();
                isPermissiveDialect = language instanceof JsonLanguage && ((JsonLanguage) language).hasPermissiveStrings();
            }
            return isPermissiveDialect;
        }

        @Override
        @Nonnull
        public TextAttributesKey[] getTokenHighlights(IElementType type) {
            return pack(ourAttributes.get(type));
        }
    }

    protected @Nonnull Lexer getLexer() {
        return new JsonLexer();
    }

    protected boolean isCanEscapeEol() {
        return false;
    }
}
