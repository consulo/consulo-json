// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.surroundWith;

import com.intellij.json.JsonElementTypes;
import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonValue;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.surroundWith.SurroundDescriptor;
import consulo.language.editor.surroundWith.Surrounder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.SmartList;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
public final class JsonSurroundDescriptor implements SurroundDescriptor {
    private static final Surrounder[] ourSurrounders = new Surrounder[]{
        new JsonWithObjectLiteralSurrounder(),
        new JsonWithArrayLiteralSurrounder(),
        new JsonWithQuotesSurrounder()
    };

    @Override
    @Nonnull
    public PsiElement[] getElementsToSurround(PsiFile file, int startOffset, int endOffset) {
        PsiElement firstElement = file.findElementAt(startOffset);
        PsiElement lastElement = file.findElementAt(endOffset - 1);

        // Extend selection beyond possible delimiters
        while (firstElement != null &&
            (firstElement instanceof PsiWhiteSpace || firstElement.getNode().getElementType() == JsonElementTypes.COMMA)) {
            firstElement = firstElement.getNextSibling();
        }
        while (lastElement != null &&
            (lastElement instanceof PsiWhiteSpace || lastElement.getNode().getElementType() == JsonElementTypes.COMMA)) {
            lastElement = lastElement.getPrevSibling();
        }
        if (firstElement != null) {
            startOffset = firstElement.getTextRange().getStartOffset();
        }
        if (lastElement != null) {
            endOffset = lastElement.getTextRange().getEndOffset();
        }

        final JsonProperty property = PsiTreeUtil.findElementOfClassAtRange(file, startOffset, endOffset, JsonProperty.class);
        if (property != null) {
            return collectElements(endOffset, property, JsonProperty.class);
        }

        final JsonValue value = PsiTreeUtil.findElementOfClassAtRange(file, startOffset, endOffset, JsonValue.class);
        if (value != null) {
            return collectElements(endOffset, value, JsonValue.class);
        }
        return PsiElement.EMPTY_ARRAY;
    }

    @Nonnull
    private static <T extends PsiElement> PsiElement[] collectElements(int endOffset, @Nonnull T property, @Nonnull Class<T> kind) {
        final List<T> properties = new SmartList<>(property);
        PsiElement nextSibling = property.getNextSibling();
        while (nextSibling != null && nextSibling.getTextRange().getEndOffset() <= endOffset) {
            if (kind.isInstance(nextSibling)) {
                properties.add(kind.cast(nextSibling));
            }
            nextSibling = nextSibling.getNextSibling();
        }
        return properties.toArray(PsiElement.EMPTY_ARRAY);
    }

    @Override
    @Nonnull
    public Surrounder[] getSurrounders() {
        return ourSurrounders;
    }

    @Override
    public boolean isExclusive() {
        return false;
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return JsonLanguage.INSTANCE;
    }
}
