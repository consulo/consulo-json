// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.surroundWith;

import com.intellij.json.psi.*;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.json.localize.JsonLocalize;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * This surrounder ported from JavaScript allows to wrap single JSON value or several consecutive JSON properties
 * in object literal.
 * <p/>
 * Examples:
 * <ol>
 * <li>{@code [42]} converts to {@code [{"property": 42}]}</li>
 * <li><pre>
 * {
 *    "foo": 42,
 *    "bar": false
 * }
 * </pre> converts to <pre>
 * {
 *    "property": {
 *      "foo": 42,
 *      "bar": false
 *    }
 * }
 * </pre></li>
 * </ol>
 *
 * @author Mikhail Golubev
 */
public final class JsonWithObjectLiteralSurrounder extends JsonSurrounderBase {
  @Override
  public LocalizeValue getTemplateDescription() {
    return JsonLocalize.surroundWithObjectLiteralDesc();
  }

  @Override
  public boolean isApplicable(PsiElement @Nonnull [] elements) {
    return !JsonPsiUtil.isPropertyKey(elements[0]) && (elements[0] instanceof JsonProperty || elements.length == 1);
  }

  @Override
  public @Nullable TextRange surroundElements(@Nonnull Project project,
                                              @Nonnull Editor editor,
                                              PsiElement @Nonnull [] elements) {

    if (!isApplicable(elements)) {
      return null;
    }

    final JsonElementGenerator generator = new JsonElementGenerator(project);

    final PsiElement firstElement = elements[0];
    final JsonElement newNameElement;
    if (firstElement instanceof JsonValue) {
      assert elements.length == 1 : "Only single JSON value can be wrapped in object literal";
      JsonObject replacement = generator.createValue(createReplacementText(firstElement.getText()));
      replacement = (JsonObject)firstElement.replace(replacement);
      newNameElement = replacement.getPropertyList().get(0).getNameElement();
    }
    else {
      assert firstElement instanceof JsonProperty;
      final String propertiesText = getTextAndRemoveMisc(firstElement, elements[elements.length - 1]);
      final JsonObject tempJsonObject = generator.createValue(createReplacementText("{\n" + propertiesText) + "\n}");
      JsonProperty replacement = tempJsonObject.getPropertyList().get(0);
      replacement = (JsonProperty)firstElement.replace(replacement);
      newNameElement = replacement.getNameElement();
    }
    final TextRange rangeWithQuotes = newNameElement.getTextRange();
    return new TextRange(rangeWithQuotes.getStartOffset() + 1, rangeWithQuotes.getEndOffset() - 1);
  }

  @Override
  protected @Nonnull String createReplacementText(@Nonnull String textInRange) {
    return "{\n\"property\": " + textInRange + "\n}";
  }
}
