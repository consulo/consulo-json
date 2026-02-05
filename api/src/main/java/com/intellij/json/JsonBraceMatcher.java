// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.BracePair;
import consulo.language.Language;
import consulo.language.PairedBraceMatcher;
import consulo.language.ast.IElementType;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
public class JsonBraceMatcher implements PairedBraceMatcher {
    private static final BracePair[] PAIRS = {
        new BracePair(JsonElementTypes.L_BRACKET, JsonElementTypes.R_BRACKET, true),
        new BracePair(JsonElementTypes.L_CURLY, JsonElementTypes.R_CURLY, true)
    };

    @Override
    @Nonnull
    public BracePair[] getPairs() {
        return PAIRS;
    }

    @Override
    public boolean isPairedBracesAllowedBeforeType(@Nonnull IElementType lbraceType, @Nullable IElementType contextType) {
        return true;
    }

    @Override
    public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
        return openingBraceOffset;
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return JsonLanguage.INSTANCE;
    }
}
