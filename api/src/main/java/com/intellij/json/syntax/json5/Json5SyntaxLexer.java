// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.syntax.json5;

import consulo.language.lexer.FlexAdapter;

public class Json5SyntaxLexer extends FlexAdapter {
  public Json5SyntaxLexer() {
    super(new _Json5Lexer(null));
  }
}
