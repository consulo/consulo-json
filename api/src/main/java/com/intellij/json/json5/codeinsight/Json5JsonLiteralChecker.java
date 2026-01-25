// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.json5.codeinsight;

import com.intellij.json.JsonDialectUtil;
import com.intellij.json.codeinsight.JsonLiteralChecker;
import com.intellij.json.codeinsight.StandardJsonLiteralChecker;
import com.intellij.json.json5.Json5Language;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonStringLiteralTextFragment;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.regex.Pattern;

@ExtensionImpl
public final class Json5JsonLiteralChecker implements JsonLiteralChecker {
  private static final Pattern VALID_HEX_ESCAPE = Pattern.compile("\\\\(x[0-9a-fA-F]{2})");
  private static final Pattern INVALID_NUMERIC_ESCAPE = Pattern.compile("\\\\[1-9]");
  @Override
  public @NonNull LocalizeValue getErrorForNumericLiteral(String literalText) {
    return LocalizeValue.empty();
  }

  @Override
  public @Nullable Pair<TextRange, LocalizeValue> getErrorForStringFragment(JsonStringLiteralTextFragment fragment, JsonStringLiteral stringLiteral) {
    String fragmentText = fragment.text();
    if (fragmentText.startsWith("\\") && fragmentText.length() > 1 && fragmentText.endsWith("\n")) {
      if (StringUtil.isEmptyOrSpaces(fragmentText.substring(1, fragmentText.length() - 1))) {
        return null;
      }
    }

    if (fragmentText.startsWith("\\x") && VALID_HEX_ESCAPE.matcher(fragmentText).matches()) {
      return null;
    }

    if (!StandardJsonLiteralChecker.VALID_ESCAPE.matcher(fragmentText).matches() && !INVALID_NUMERIC_ESCAPE.matcher(fragmentText).matches()) {
      return null;
    }

    final LocalizeValue error = StandardJsonLiteralChecker.getStringError(fragmentText);
    return error == LocalizeValue.empty() ? null : Pair.create(fragment.textRange(), error);
  }

  @Override
  public boolean isApplicable(PsiElement element) {
    return JsonDialectUtil.getLanguageOrDefaultJson(element) == Json5Language.INSTANCE;
  }
}
