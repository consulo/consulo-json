// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.highlighting;

import com.intellij.json.JsonElementTypes;
import consulo.language.ast.IElementType;
import consulo.language.lexer.LayeredLexer;
import consulo.language.lexer.Lexer;

public class JsonHighlightingLexer extends LayeredLexer {
  public JsonHighlightingLexer(boolean isPermissiveDialect, boolean canEscapeEol, Lexer baseLexer) {
    super(baseLexer);
    registerSelfStoppingLayer(new JsonStringLiteralLexer('\"', JsonElementTypes.DOUBLE_QUOTED_STRING, canEscapeEol, isPermissiveDialect),
                              new IElementType[]{JsonElementTypes.DOUBLE_QUOTED_STRING}, IElementType.EMPTY_ARRAY);
    registerSelfStoppingLayer(new JsonStringLiteralLexer('\'', JsonElementTypes.SINGLE_QUOTED_STRING, canEscapeEol, isPermissiveDialect),
                                           new IElementType[]{JsonElementTypes.SINGLE_QUOTED_STRING}, IElementType.EMPTY_ARRAY);
  }
}
