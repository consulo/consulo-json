// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.psi.impl;

import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonValue;
import consulo.language.Language;
import consulo.language.file.FileViewProvider;
import consulo.language.impl.psi.PsiFileBase;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.virtualFileSystem.fileType.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class JsonFileImpl extends PsiFileBase implements JsonFile {

  public JsonFileImpl(FileViewProvider fileViewProvider, Language language) {
    super(fileViewProvider, language);
  }

  @Override
  public @NotNull FileType getFileType() {
    return getViewProvider().getFileType();
  }

  @Override
  public @Nullable JsonValue getTopLevelValue() {
    return PsiTreeUtil.getChildOfType(this, JsonValue.class);
  }

  @Override
  public @NotNull List<JsonValue> getAllTopLevelValues() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, JsonValue.class);
  }

  @Override
  public String toString() {
    return "JsonFile: " + getName();
  }
}
