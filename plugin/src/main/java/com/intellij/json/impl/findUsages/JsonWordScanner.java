// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.findUsages;

import com.intellij.json.JsonElementTypes;
import com.intellij.json.JsonLexer;
import consulo.language.ast.TokenSet;
import consulo.language.cacheBuilder.DefaultWordsScanner;

import static com.intellij.json.JsonTokenSets.JSON_COMMENTARIES;
import static com.intellij.json.JsonTokenSets.JSON_LITERALS;

/**
 * @author Mikhail Golubev
 */
public final class JsonWordScanner extends DefaultWordsScanner {
  public JsonWordScanner() {
    super(new JsonLexer(), TokenSet.create(JsonElementTypes.IDENTIFIER), JSON_COMMENTARIES, JSON_LITERALS);
    setMayHaveFileRefsInLiterals(true);
  }
}
