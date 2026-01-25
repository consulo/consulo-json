// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.surroundWith;

import consulo.json.localize.JsonLocalize;
import consulo.localize.LocalizeValue;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

public final class JsonWithQuotesSurrounder extends JsonSurrounderBase {
  @Override
  public LocalizeValue getTemplateDescription() {
    return JsonLocalize.surroundWithQuotesDesc();
  }

  @Override
  protected @Nonnull String createReplacementText(@Nonnull String firstElement) {
    return "\"" + StringUtil.escapeStringCharacters(firstElement) + "\"";
  }
}
