// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json;

import consulo.json.icon.JsonIconGroup;
import consulo.json.localize.JsonLocalize;
import consulo.language.Language;
import consulo.language.file.LanguageFileType;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;

/**
 * @author Mikhail Golubev
 */
public class JsonFileType extends LanguageFileType {
  public static final JsonFileType INSTANCE = new JsonFileType();
  public static final String DEFAULT_EXTENSION = "json";

  protected JsonFileType(Language language) {
    super(language);
  }

  protected JsonFileType(Language language, boolean secondary) {
    super(language, secondary);
  }

  protected JsonFileType() {
    super(JsonLanguage.INSTANCE);
  }

  @Override
  public @Nonnull String getId() {
    return "JSON";
  }

  @Override
  public @Nonnull LocalizeValue getDescription() {
    return JsonLocalize.filetypeJsonDescription();
  }

  @Override
  public @Nonnull String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Override
  public Image getIcon() {
    return JsonIconGroup.json();
  }
}
