// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.json.syntax._JsonLexer;
import consulo.language.lexer.FlexAdapter;

/**
 * @author Mikhail Golubev
 */
public class JsonLexer extends FlexAdapter {
    public JsonLexer() {
        super(new _JsonLexer(null));
    }
}
