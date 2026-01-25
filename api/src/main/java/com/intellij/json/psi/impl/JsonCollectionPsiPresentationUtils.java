// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.psi.impl;

import com.intellij.json.JsonBundle;
import com.intellij.json.psi.JsonArray;

import jakarta.annotation.Nonnull;

public class JsonCollectionPsiPresentationUtils {

  @Nonnull
  public static String getCollectionPsiPresentationText(@Nonnull JsonArray array) {
    int childrenCount = array.getValueList().size();
    return getCollectionPsiPresentationText(childrenCount);
  }

  @Nonnull
  public static String getCollectionPsiPresentationText(int childrenCount) {
    return JsonBundle.message("folding.collapsed.array.text", childrenCount);
  }

  private JsonCollectionPsiPresentationUtils() {
  }
}
