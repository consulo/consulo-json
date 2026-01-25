// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.formatter;

import com.intellij.json.JsonElementTypes;
import com.intellij.json.JsonLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.DefaultLineWrapPositionStrategy;
import consulo.document.Document;
import consulo.language.Language;
import consulo.language.ast.IElementType;
import consulo.language.editor.LanguageLineWrapPositionStrategy;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public final class JsonLineWrapPositionStrategy extends DefaultLineWrapPositionStrategy implements LanguageLineWrapPositionStrategy {
    @Override
    public int calculateWrapPosition(@Nonnull Document document,
                                     @Nullable Project project,
                                     int startOffset,
                                     int endOffset,
                                     int maxPreferredOffset,
                                     boolean allowToBeyondMaxPreferredOffset,
                                     boolean isSoftWrap) {
        if (isSoftWrap) {
            return super.calculateWrapPosition(document, project, startOffset, endOffset, maxPreferredOffset, allowToBeyondMaxPreferredOffset,
                true);
        }
        if (project == null) {
            return -1;
        }
        final int wrapPosition = getMinWrapPosition(document, project, maxPreferredOffset);
        if (wrapPosition == SKIP_WRAPPING) {
            return -1;
        }
        int minWrapPosition = Math.max(startOffset, wrapPosition);
        return super
            .calculateWrapPosition(document, project, minWrapPosition, endOffset, maxPreferredOffset, allowToBeyondMaxPreferredOffset, isSoftWrap);
    }

    private static final int SKIP_WRAPPING = -2;

    private static int getMinWrapPosition(@Nonnull Document document, @Nonnull Project project, int offset) {
        PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
        if (manager.isUncommited(document)) {
            manager.commitDocument(document);
        }
        PsiFile psiFile = manager.getPsiFile(document);
        if (psiFile != null) {
            PsiElement currElement = psiFile.findElementAt(offset);
            final IElementType elementType = PsiUtilCore.getElementType(currElement);
            if (elementType == JsonElementTypes.DOUBLE_QUOTED_STRING
                || elementType == JsonElementTypes.SINGLE_QUOTED_STRING
                || elementType == JsonElementTypes.LITERAL
                || elementType == JsonElementTypes.BOOLEAN_LITERAL
                || elementType == JsonElementTypes.TRUE
                || elementType == JsonElementTypes.FALSE
                || elementType == JsonElementTypes.IDENTIFIER
                || elementType == JsonElementTypes.NULL_LITERAL
                || elementType == JsonElementTypes.NUMBER_LITERAL) {
                return currElement.getTextRange().getEndOffset();
            }
            if (elementType == JsonElementTypes.COLON) {
                return SKIP_WRAPPING;
            }
            if (currElement != null) {
                if (currElement instanceof PsiComment ||
                    PsiUtilCore.getElementType(PsiTreeUtil.skipWhitespacesForward(currElement)) == JsonElementTypes.COMMA) {
                    return SKIP_WRAPPING;
                }
            }
        }
        return -1;
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return JsonLanguage.INSTANCE;
    }
}
