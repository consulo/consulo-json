// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.surroundWith;

import com.intellij.json.psi.JsonElementGenerator;
import com.intellij.json.psi.JsonPsiUtil;
import com.intellij.json.psi.JsonValue;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.editor.surroundWith.Surrounder;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public abstract class JsonSurrounderBase implements Surrounder {
  @Override
  public boolean isApplicable(PsiElement @Nonnull [] elements) {
    return elements.length >= 1 && elements[0] instanceof JsonValue && !JsonPsiUtil.isPropertyKey(elements[0]);
  }

  @Override
  public @Nullable TextRange surroundElements(@Nonnull Project project, @Nonnull Editor editor, PsiElement @Nonnull [] elements) {
    if (!isApplicable(elements)) {
      return null;
    }

    final JsonElementGenerator generator = new JsonElementGenerator(project);

    if (elements.length == 1) {
      JsonValue replacement = generator.createValue(createReplacementText(elements[0].getText()));
      elements[0].replace(replacement);
    }
    else {
      final String propertiesText = getTextAndRemoveMisc(elements[0], elements[elements.length - 1]);
      JsonValue replacement = generator.createValue(createReplacementText(propertiesText));
      elements[0].replace(replacement);
    }
    return null;
  }

  protected static @Nonnull String getTextAndRemoveMisc(@Nonnull PsiElement firstProperty, @Nonnull PsiElement lastProperty) {
    final TextRange replacedRange = new TextRange(firstProperty.getTextOffset(), lastProperty.getTextRange().getEndOffset());
    final String propertiesText = replacedRange.substring(firstProperty.getContainingFile().getText());
    if (firstProperty != lastProperty) {
      final PsiElement parent = firstProperty.getParent();
      parent.deleteChildRange(firstProperty.getNextSibling(), lastProperty);
    }
    return propertiesText;
  }

  protected abstract @Nonnull String createReplacementText(@Nonnull String textInRange);
}
