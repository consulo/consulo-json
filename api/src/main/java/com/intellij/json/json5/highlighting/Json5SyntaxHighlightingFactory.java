// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.json5.highlighting;

import com.intellij.json.highlighting.JsonSyntaxHighlighterFactory;
import com.intellij.json.json5.Json5Language;
import com.intellij.json.json5.Json5Lexer;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.lexer.Lexer;
import jakarta.annotation.Nonnull;

@ExtensionImpl
public final class Json5SyntaxHighlightingFactory extends JsonSyntaxHighlighterFactory {
    @Override
    protected @Nonnull Lexer getLexer() {
        return new Json5Lexer();
    }

    @Override
    protected boolean isCanEscapeEol() {
        return true;
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return Json5Language.INSTANCE;
    }
}
