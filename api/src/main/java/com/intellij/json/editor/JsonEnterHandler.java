// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.editor;

import com.intellij.json.JsonElementTypes;
import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.dataContext.DataContext;
import consulo.document.Document;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.editor.action.EnterHandlerDelegate;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.ref.SimpleReference;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;

@ExtensionImpl
public final class JsonEnterHandler implements EnterHandlerDelegate {
    @Override
    public Result preprocessEnter(@NotNull PsiFile file,
                                  @NotNull Editor editor,
                                  @NotNull SimpleReference<Integer> caretOffsetRef,
                                  @NotNull SimpleReference<Integer> caretAdvanceRef,
                                  @NotNull DataContext dataContext,
                                  EditorActionHandler originalHandler) {
        if (!JsonEditorOptions.getInstance().COMMA_ON_ENTER) {
            return Result.Continue;
        }

        Language language = EnterHandlerDelegate.getContextLanguage(dataContext);
        if (!(language instanceof JsonLanguage)) {
            return Result.Continue;
        }

        int caretOffset = caretOffsetRef.get().intValue();
        PsiElement psiAtOffset = file.findElementAt(caretOffset);

        if (psiAtOffset == null) {
            return Result.Continue;
        }

        if (psiAtOffset instanceof consulo.language.impl.psi.LeafPsiElement && handleComma(caretOffsetRef, psiAtOffset, editor)) {
            return Result.Continue;
        }

        JsonValue literal = ObjectUtil.tryCast(psiAtOffset.getParent(), JsonValue.class);
        if (literal != null && (!(literal instanceof JsonStringLiteral) || !((JsonLanguage) language).hasPermissiveStrings())) {
            handleJsonValue(literal, editor, caretOffsetRef);
        }

        return Result.Continue;
    }

    @Override
    public Result postProcessEnter(@Nonnull PsiFile psiFile, @Nonnull Editor editor, @Nonnull DataContext dataContext) {
        return Result.Continue;
    }

    private static boolean handleComma(@NotNull SimpleReference<Integer> caretOffsetRef, @NotNull PsiElement psiAtOffset, @NotNull Editor editor) {
        PsiElement nextSibling = psiAtOffset;
        boolean hasNewlineBefore = false;
        while (nextSibling instanceof PsiWhiteSpace) {
            hasNewlineBefore = nextSibling.getText().contains("\n");
            nextSibling = nextSibling.getNextSibling();
        }

        consulo.language.impl.psi.LeafPsiElement leafPsiElement = ObjectUtil.tryCast(nextSibling, consulo.language.impl.psi.LeafPsiElement.class);
        IElementType elementType = leafPsiElement == null ? null : leafPsiElement.getElementType();
        if (elementType == JsonElementTypes.COMMA || elementType == JsonElementTypes.R_CURLY) {
            PsiElement prevSibling = nextSibling.getPrevSibling();
            while (prevSibling instanceof PsiWhiteSpace) {
                prevSibling = prevSibling.getPrevSibling();
            }

            if (prevSibling instanceof JsonProperty && ((JsonProperty) prevSibling).getValue() != null) {
                int offset = elementType == JsonElementTypes.COMMA ? nextSibling.getTextRange().getEndOffset() : prevSibling.getTextRange().getEndOffset();
                if (offset < editor.getDocument().getTextLength()) {
                    if (elementType == JsonElementTypes.R_CURLY && hasNewlineBefore) {
                        editor.getDocument().insertString(offset, ",");
                        offset++;
                    }
                    caretOffsetRef.set(offset);
                }
                return true;
            }
            return false;
        }

        if (nextSibling instanceof JsonProperty) {
            PsiElement prevSibling = nextSibling.getPrevSibling();
            while (prevSibling instanceof PsiWhiteSpace || prevSibling instanceof PsiErrorElement) {
                prevSibling = prevSibling.getPrevSibling();
            }

            if (prevSibling instanceof JsonProperty) {
                int offset = prevSibling.getTextRange().getEndOffset();
                if (offset < editor.getDocument().getTextLength()) {
                    editor.getDocument().insertString(offset, ",");
                    caretOffsetRef.set(offset + 1);
                }
                return true;
            }
        }

        return false;
    }

    @RequiredReadAction
    private static void handleJsonValue(@NotNull JsonValue literal, @NotNull Editor editor, @NotNull SimpleReference<Integer> caretOffsetRef) {
        PsiElement parent = literal.getParent();
        if (!(parent instanceof JsonProperty) || ((JsonProperty) parent).getValue() != literal) {
            return;
        }

        PsiElement nextSibling = parent.getNextSibling();
        while (nextSibling instanceof PsiWhiteSpace || nextSibling instanceof PsiErrorElement) {
            nextSibling = nextSibling.getNextSibling();
        }

        int offset = literal.getTextRange().getEndOffset();

        if (literal instanceof JsonObject || literal instanceof JsonArray) {
            if (nextSibling instanceof consulo.language.impl.psi.LeafPsiElement && ((consulo.language.impl.psi.LeafPsiElement) nextSibling).getElementType() == JsonElementTypes.COMMA
                || !(nextSibling instanceof JsonProperty)) {
                return;
            }
            Document document = editor.getDocument();
            if (offset < document.getTextLength()) {
                document.insertString(offset, ",");
            }
            return;
        }

        if (nextSibling instanceof consulo.language.impl.psi.LeafPsiElement && ((consulo.language.impl.psi.LeafPsiElement) nextSibling).getElementType() == JsonElementTypes.COMMA) {
            offset = nextSibling.getTextRange().getEndOffset();
        }
        else {
            Document document = editor.getDocument();
            if (offset < document.getTextLength()) {
                document.insertString(offset, ",");
            }
            offset++;
        }

        if (offset < editor.getDocument().getTextLength()) {
            caretOffsetRef.set(offset);
        }
    }
}
