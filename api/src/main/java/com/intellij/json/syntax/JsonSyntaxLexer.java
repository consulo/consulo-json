// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.syntax;

import consulo.language.lexer.FlexAdapter;

/**
 * @author Mikhail Golubev
 */
public class JsonSyntaxLexer extends FlexAdapter {
  public JsonSyntaxLexer() {
    super(new _JsonLexer(null));
  }
}
