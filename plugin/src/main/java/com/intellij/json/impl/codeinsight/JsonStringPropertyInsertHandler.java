// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.codeinsight;

import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import consulo.document.util.TextRange;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.AutoPopupController;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

public final class JsonStringPropertyInsertHandler implements InsertHandler<LookupElement> {

  private final String myNewValue;

  public JsonStringPropertyInsertHandler(@Nonnull String newValue) {
    myNewValue = newValue;
  }

  @Override
  public void handleInsert(@Nonnull InsertionContext context, @Nonnull LookupElement item) {
    PsiElement element = context.getFile().findElementAt(context.getStartOffset());
    JsonStringLiteral literal = PsiTreeUtil.getParentOfType(element, JsonStringLiteral.class, false);
    if (literal == null) return;
    JsonProperty property = ObjectUtil.tryCast(literal.getParent(), JsonProperty.class);
    if (property == null) return;
    final TextRange toDelete;
    String textToInsert = "";
    TextRange literalRange = literal.getTextRange();
    if (literal.getValue().equals(myNewValue)) {
      toDelete = new TextRange(literalRange.getEndOffset(), literalRange.getEndOffset());
    }
    else {
      toDelete = literalRange;
      textToInsert = StringUtil.wrapWithDoubleQuote(myNewValue);
    }
    int newCaretOffset = literalRange.getStartOffset() + 1 + myNewValue.length();
    boolean showAutoPopup = false;
    if (property.getNameElement().equals(literal)) {
      if (property.getValue() == null) {
        textToInsert += ":\"\"";
        newCaretOffset += 3; // "package<caret offset>":"<new caret offset>"
        if (needCommaAfter(property)) {
          textToInsert += ",";
        }
        showAutoPopup = true;
      }
    }
    context.getDocument().replaceString(toDelete.getStartOffset(), toDelete.getEndOffset(), textToInsert);
    context.getEditor().getCaretModel().moveToOffset(newCaretOffset);
    reformat(context, toDelete.getStartOffset(), toDelete.getStartOffset() + textToInsert.length());
    if (showAutoPopup) {
      AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
    }
  }

  private static boolean needCommaAfter(@Nonnull JsonProperty property) {
    PsiElement element = property.getNextSibling();
    while (element != null) {
      if (element instanceof JsonProperty) {
        return true;
      }
      if (element.getNode().getElementType() == JsonElementTypes.COMMA) {
        return false;
      }
      element = element.getNextSibling();
    }
    return false;
  }

  private static void reformat(@Nonnull InsertionContext context, int startOffset, int endOffset) {
    PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getDocument());
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(context.getProject());
    codeStyleManager.reformatText(context.getFile(), startOffset, endOffset);
  }
}
