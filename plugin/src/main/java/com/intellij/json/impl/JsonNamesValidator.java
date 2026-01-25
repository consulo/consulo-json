// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.json.impl;

import com.intellij.json.JsonElementTypes;
import com.intellij.json.JsonLanguage;
import com.intellij.json.JsonLexer;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.editor.refactoring.NamesValidator;
import consulo.language.lexer.Lexer;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;

import static com.intellij.json.JsonTokenSets.JSON_KEYWORDS;

@ExtensionImpl
public final class JsonNamesValidator implements NamesValidator {

    private final Lexer myLexer = new JsonLexer();

    @Override
    public synchronized boolean isKeyword(@NotNull String name, Project project) {
        myLexer.start(name);
        IElementType tokenType = myLexer.getTokenType();
        return tokenType != null && JSON_KEYWORDS.contains(myLexer.getTokenType()) && myLexer.getTokenEnd() == name.length();
    }

    @Override
    public synchronized boolean isIdentifier(@NotNull String name, final Project project) {
        if (!StringUtil.startsWithChar(name, '\'') && !StringUtil.startsWithChar(name, '"')) {
            name = "\"" + name;
        }

        if (!StringUtil.endsWithChar(name, '"') && !StringUtil.endsWithChar(name, '\'')) {
            name += "\"";
        }

        myLexer.start(name);
        IElementType type = myLexer.getTokenType();

        return myLexer.getTokenEnd() == name.length() && (type == JsonElementTypes.DOUBLE_QUOTED_STRING ||
            type == JsonElementTypes.SINGLE_QUOTED_STRING);
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return JsonLanguage.INSTANCE;
    }
}
