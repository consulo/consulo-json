// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.jsonLines;

import com.intellij.json.JsonLanguage;

import jakarta.annotation.Nonnull;

public class JsonLinesLanguage extends JsonLanguage {
  @Nonnull
  public static final JsonLinesLanguage INSTANCE = new JsonLinesLanguage();

  private JsonLinesLanguage() {
    super("JSON Lines", "application/json-lines");
  }
}
