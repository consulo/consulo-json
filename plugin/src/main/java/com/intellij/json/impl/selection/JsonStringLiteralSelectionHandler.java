// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.selection;

import com.intellij.json.psi.JsonStringLiteral;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import consulo.language.editor.action.ExtendWordSelectionHandlerBase;
import consulo.language.editor.action.SelectWordUtil;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.lexer.StringLiteralLexer;
import consulo.language.psi.ElementManipulators;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.json.JsonElementTypes.SINGLE_QUOTED_STRING;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
public final class JsonStringLiteralSelectionHandler extends ExtendWordSelectionHandlerBase {
  @Override
  public boolean canSelect(@Nonnull PsiElement e) {
    if (!(e.getParent() instanceof JsonStringLiteral)) {
      return false;
    }
    return !InjectedLanguageManager.getInstance(e.getProject()).isInjectedFragment(e.getContainingFile());
  }

  @Override
  public List<TextRange> select(@Nonnull PsiElement e, @Nonnull CharSequence editorText, int cursorOffset, @Nonnull Editor editor) {
    final IElementType type = e.getNode().getElementType();
    final StringLiteralLexer lexer = new StringLiteralLexer(type == SINGLE_QUOTED_STRING ? '\'' : '"', type, false, "/", false, false);
    final List<TextRange> result = new ArrayList<>();
    SelectWordUtil.addWordHonoringEscapeSequences(editorText, e.getTextRange(), cursorOffset, lexer, result);

    final PsiElement parent = e.getParent();
    result.add(ElementManipulators.getValueTextRange(parent).shiftRight(parent.getTextOffset()));
    return result;
  }
}
