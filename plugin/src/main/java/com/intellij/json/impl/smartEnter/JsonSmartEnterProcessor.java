// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.smartEnter;

import com.intellij.json.JsonDialectUtil;
import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.editor.action.SmartEnterProcessorWithFixers;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;

import java.util.List;

import static com.intellij.json.JsonElementTypes.COLON;
import static com.intellij.json.JsonElementTypes.COMMA;

/**
 * This processor allows
 * <ul>
 * <li>Insert colon after key inside object property</li>
 * <li>Insert comma after array element or object property</li>
 * </ul>
 *
 * @author Mikhail Golubev
 */
@ExtensionImpl
public final class JsonSmartEnterProcessor extends SmartEnterProcessorWithFixers {
    public static final Logger LOG = Logger.getInstance(JsonSmartEnterProcessor.class);

    private boolean myShouldAddNewline = false;

    public JsonSmartEnterProcessor() {
        addFixers(new JsonObjectPropertyFixer(), new JsonArrayElementFixer());
        addEnterProcessors(new JsonEnterProcessor());
    }

    @Override
    protected void collectAdditionalElements(@Nonnull PsiElement element, @Nonnull List<PsiElement> result) {
        // include all parents as well
        PsiElement parent = element.getParent();
        while (parent != null && !(parent instanceof JsonFile)) {
            result.add(parent);
            parent = parent.getParent();
        }
    }

    private static boolean terminatedOnCurrentLine(@Nonnull Editor editor, @Nonnull PsiElement element) {
        final Document document = editor.getDocument();
        final int caretOffset = editor.getCaretModel().getCurrentCaret().getOffset();
        final int elementEndOffset = element.getTextRange().getEndOffset();
        if (document.getLineNumber(elementEndOffset) != document.getLineNumber(caretOffset)) {
            return false;
        }
        // Skip empty PsiError elements if comma is missing
        PsiElement nextLeaf = PsiTreeUtil.nextLeaf(element, true);
        return nextLeaf == null || (nextLeaf instanceof PsiWhiteSpace && nextLeaf.getText().contains("\n"));
    }

    private static boolean isFollowedByTerminal(@Nonnull PsiElement element, IElementType type) {
        final PsiElement nextLeaf = PsiTreeUtil.nextVisibleLeaf(element);
        return nextLeaf != null && nextLeaf.getNode().getElementType() == type;
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return JsonLanguage.INSTANCE;
    }

    private static final class JsonArrayElementFixer extends SmartEnterProcessorWithFixers.Fixer<JsonSmartEnterProcessor> {
        @Override
        public void apply(@Nonnull Editor editor, @Nonnull JsonSmartEnterProcessor processor, @Nonnull PsiElement element)
            throws IncorrectOperationException {
            if (element instanceof JsonValue arrayElement && element.getParent() instanceof JsonArray) {
                if (terminatedOnCurrentLine(editor, arrayElement) && !isFollowedByTerminal(element, COMMA)) {
                    editor.getDocument().insertString(arrayElement.getTextRange().getEndOffset(), ",");
                    processor.myShouldAddNewline = true;
                }
            }
        }
    }

    private static final class JsonObjectPropertyFixer extends SmartEnterProcessorWithFixers.Fixer<JsonSmartEnterProcessor> {
        @Override
        public void apply(@Nonnull Editor editor, @Nonnull JsonSmartEnterProcessor processor, @Nonnull PsiElement element)
            throws IncorrectOperationException {
            if (element instanceof JsonProperty) {
                final JsonValue propertyValue = ((JsonProperty) element).getValue();
                if (propertyValue != null) {
                    if (terminatedOnCurrentLine(editor, propertyValue) && !isFollowedByTerminal(propertyValue, COMMA)) {
                        editor.getDocument().insertString(propertyValue.getTextRange().getEndOffset(), ",");
                        processor.myShouldAddNewline = true;
                    }
                }
                else {
                    final JsonValue propertyKey = ((JsonProperty) element).getNameElement();
                    TextRange keyRange = propertyKey.getTextRange();
                    final int keyStartOffset = keyRange.getStartOffset();
                    int keyEndOffset = keyRange.getEndOffset();
                    //processor.myFirstErrorOffset = keyEndOffset;
                    if (terminatedOnCurrentLine(editor, propertyKey) && !isFollowedByTerminal(propertyKey, COLON)) {
                        boolean shouldQuoteKey = propertyKey instanceof JsonReferenceExpression && JsonDialectUtil.isStandardJson(propertyKey);
                        if (shouldQuoteKey) {
                            editor.getDocument().insertString(keyStartOffset, "\"");
                            keyEndOffset++;
                            editor.getDocument().insertString(keyEndOffset, "\"");
                            keyEndOffset++;
                        }
                        processor.myFirstErrorOffset = keyEndOffset + 2;
                        editor.getDocument().insertString(keyEndOffset, ": ");
                    }
                }
            }
        }
    }

    private final class JsonEnterProcessor extends SmartEnterProcessorWithFixers.FixEnterProcessor {
        @Override
        public boolean doEnter(PsiElement atCaret, PsiFile file, @Nonnull Editor editor, boolean modified) {
            if (myShouldAddNewline) {
                try {
                    plainEnter(editor);
                }
                finally {
                    myShouldAddNewline = false;
                }
            }
            return true;
        }
    }
}
