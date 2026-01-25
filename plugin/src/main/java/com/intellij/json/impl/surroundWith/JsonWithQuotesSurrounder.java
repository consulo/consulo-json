// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.surroundWith;

import consulo.json.localize.JsonLocalize;
import consulo.localize.LocalizeValue;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.NotNull;

public final class JsonWithQuotesSurrounder extends JsonSurrounderBase {
  @Override
  public LocalizeValue getTemplateDescription() {
    return JsonLocalize.surroundWithQuotesDesc();
  }

  @Override
  protected @NotNull String createReplacementText(@NotNull String firstElement) {
    return "\"" + StringUtil.escapeStringCharacters(firstElement) + "\"";
  }
}
