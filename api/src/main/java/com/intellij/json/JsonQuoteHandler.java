// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import com.intellij.json.editor.JsonTypedHandler;
import com.intellij.json.psi.JsonStringLiteral;
import consulo.codeEditor.Editor;
import consulo.codeEditor.HighlighterIterator;
import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenType;
import consulo.language.editor.action.MultiCharQuoteHandler;
import consulo.language.editor.action.SimpleTokenSetQuoteHandler;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import static com.intellij.json.JsonTokenSets.STRING_LITERALS;

/**
 * @author Mikhail Golubev
 */
public final class JsonQuoteHandler extends SimpleTokenSetQuoteHandler implements MultiCharQuoteHandler {
  public JsonQuoteHandler() {
    super(STRING_LITERALS);
  }

  @Override
  public @Nullable CharSequence getClosingQuote(@Nonnull HighlighterIterator iterator, int offset) {
    final IElementType tokenType = (IElementType) iterator.getTokenType();
    if (tokenType == TokenType.WHITE_SPACE) {
      final int index = iterator.getStart() - 1;
      if (index >= 0) {
        return String.valueOf(iterator.getDocument().getCharsSequence().charAt(index));
      }
    }
    return tokenType == JsonElementTypes.SINGLE_QUOTED_STRING ? "'" : "\"";
  }

  @Override
  public void insertClosingQuote(@Nonnull Editor editor, int offset, @Nonnull PsiFile file, @Nonnull CharSequence closingQuote) {
    PsiElement element = file.findElementAt(offset - 1);
    PsiElement parent = element == null ? null : element.getParent();
    if (parent instanceof JsonStringLiteral) {
      PsiDocumentManager.getInstance(file.getProject()).commitDocument(editor.getDocument());
      TextRange range = parent.getTextRange();
      if (offset - 1 != range.getStartOffset() || !"\"".contentEquals(closingQuote)) {
        int endOffset = range.getEndOffset();
        if (offset < endOffset) return;
        if (offset == endOffset && !StringUtil.isEmpty(((JsonStringLiteral)parent).getValue())) return;
      }
    }
    editor.getDocument().insertString(offset, closingQuote);
    JsonTypedHandler.processPairedBracesComma(closingQuote.charAt(0), editor, file);
  }
}