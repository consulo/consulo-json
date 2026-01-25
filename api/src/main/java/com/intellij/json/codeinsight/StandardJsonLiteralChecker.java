// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.codeinsight;

import com.intellij.json.JsonDialectUtil;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonStringLiteralTextFragment;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.json.localize.JsonLocalize;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.util.lang.Pair;
import jakarta.annotation.Nullable;

import java.util.regex.Pattern;

@ExtensionImpl(order = "last")
public class StandardJsonLiteralChecker implements JsonLiteralChecker {
  public static final Pattern VALID_ESCAPE = Pattern.compile("\\\\([\"\\\\/bfnrt]|u[0-9a-fA-F]{4})");
  private static final Pattern VALID_NUMBER_LITERAL = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");
  public static final String INF = "Infinity";
  public static final String MINUS_INF = "-Infinity";
  public static final String NAN = "NaN";

  @Override
  public @Nullable LocalizeValue getErrorForNumericLiteral(String literalText) {
    if (!INF.equals(literalText) &&
        !MINUS_INF.equals(literalText) &&
        !NAN.equals(literalText) &&
        !VALID_NUMBER_LITERAL.matcher(literalText).matches()) {
      return JsonLocalize.syntaxErrorIllegalFloatingPointLiteral();
    }
    return null;
  }

  @Override
  public @Nullable Pair<TextRange, LocalizeValue> getErrorForStringFragment(JsonStringLiteralTextFragment fragment, JsonStringLiteral stringLiteral) {
    if (fragment.text().chars().anyMatch(c -> c <= '\u001F')) { // fragments are cached, string values - aren't; go inside only if we encountered a potentially 'wrong' char
      final String text = stringLiteral.getText();
      if (new TextRange(0, text.length()).contains(fragment.textRange())) {
        final int startOffset = fragment.textRange().getStartOffset();
        final String part = text.substring(startOffset, fragment.textRange().getEndOffset());
        char[] array = part.toCharArray();
        for (int i = 0; i < array.length; i++) {
          char c = array[i];
          if (c <= '\u001F') {
            return Pair.create(new TextRange(startOffset + i, startOffset + i + 1),
                JsonLocalize.syntaxErrorControlCharInString("\\u" + Integer.toHexString(c | 0x10000).substring(1)));
          }
        }
      }
    }
    final LocalizeValue error = getStringError(fragment.text());
    return error == LocalizeValue.empty() ? null : Pair.create(fragment.textRange(), error);
  }

  public static LocalizeValue getStringError(String fragmentText) {
    if (fragmentText.startsWith("\\") && fragmentText.length() > 1 && !VALID_ESCAPE.matcher(fragmentText).matches()) {
      if (fragmentText.startsWith("\\u")) {
        return JsonLocalize.syntaxErrorIllegalUnicodeEscapeSequence();
      }
      else {
        return JsonLocalize.syntaxErrorIllegalEscapeSequence();
      }
    }
    return LocalizeValue.empty();
  }

  @Override
  public boolean isApplicable(PsiElement element) {
    return JsonDialectUtil.isStandardJson(element);
  }
}
