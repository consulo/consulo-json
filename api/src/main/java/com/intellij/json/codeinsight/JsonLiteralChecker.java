// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.codeinsight;

import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonStringLiteralTextFragment;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nullable;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface JsonLiteralChecker {
  ExtensionPointName<JsonLiteralChecker> EP_NAME = ExtensionPointName.create(JsonLiteralChecker.class);

  @Nonnull
  LocalizeValue getErrorForNumericLiteral(String literalText);

  @Nullable
  Pair<TextRange, LocalizeValue> getErrorForStringFragment(JsonStringLiteralTextFragment fragmentText, JsonStringLiteral stringLiteral);

  boolean isApplicable(PsiElement element);
}
