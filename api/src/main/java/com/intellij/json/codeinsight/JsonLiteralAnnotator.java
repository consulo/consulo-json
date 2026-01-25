// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.codeinsight;

import com.intellij.json.highlighting.JsonSyntaxHighlighterFactory;
import com.intellij.json.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.ApplicationManager;
import consulo.document.util.TextRange;
import consulo.json.localize.JsonLocalize;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.util.lang.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class JsonLiteralAnnotator implements Annotator {

    private final boolean isDebug = ApplicationManager.getApplication().isUnitTestMode();

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        List<JsonLiteralChecker> extensions = JsonLiteralChecker.EP_NAME.getExtensionList();
        if (element instanceof JsonReferenceExpression) {
            highlightPropertyKey(element, holder);
        }
        else if (element instanceof JsonStringLiteral stringLiteral) {
            final int elementOffset = element.getTextOffset();
            highlightPropertyKey(element, holder);
            final String text = JsonPsiUtil.getElementTextWithoutHostEscaping(element);
            final int length = text.length();

            // Check that string literal is closed properly
            if (length <= 1 || text.charAt(0) != text.charAt(length - 1) || JsonPsiUtil.isEscapedChar(text, length - 1)) {
                holder.newAnnotation(HighlightSeverity.ERROR, JsonLocalize.syntaxErrorMissingClosingQuote()).create();
            }

            // Check escapes
            final List<JsonStringLiteralTextFragment> fragments = stringLiteral.getTextFragments();
            for (JsonStringLiteralTextFragment fragment : fragments) {
                for (JsonLiteralChecker checker : extensions) {
                    if (!checker.isApplicable(element)) {
                        continue;
                    }
                    Pair<TextRange, LocalizeValue> error = checker.getErrorForStringFragment(fragment, stringLiteral);
                    if (error != null) {
                        holder.newAnnotation(HighlightSeverity.ERROR, error.second).range(error.getFirst().shiftRight(elementOffset)).create();
                    }
                }
            }
        }
        else if (element instanceof JsonNumberLiteral) {
            String text = null;
            for (JsonLiteralChecker checker : extensions) {
                if (!checker.isApplicable(element)) {
                    continue;
                }
                if (text == null) {
                    text = JsonPsiUtil.getElementTextWithoutHostEscaping(element);
                }
                LocalizeValue error = checker.getErrorForNumericLiteral(text);
                if (error.isNotEmpty()) {
                    holder.newAnnotation(HighlightSeverity.ERROR, error).create();
                }
            }
        }
    }

    @RequiredReadAction
    private void highlightPropertyKey(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (JsonPsiUtil.isPropertyKey(element)) {
            if (isDebug) {
                holder.newAnnotation(HighlightSeverity.INFORMATION, JsonLocalize.annotationPropertyKey()).textAttributes(JsonSyntaxHighlighterFactory.JSON_PROPERTY_KEY).create();
            }
            else {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION).textAttributes(JsonSyntaxHighlighterFactory.JSON_PROPERTY_KEY).create();
            }
        }
    }
}
