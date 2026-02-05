// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.JsonUtil;
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler;
import consulo.annotation.component.ExtensionImpl;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public final class JsonSchemaInJsonFilesEnabler implements JsonSchemaEnabler {
  @Override
  public boolean isEnabledForFile(@Nonnull VirtualFile file, @Nullable Project project) {
    return JsonUtil.isJsonFile(file, project);
  }

  @Override
  public boolean canBeSchemaFile(VirtualFile file) {
    return isEnabledForFile(file, null /*avoid checking scratches*/);
  }
}
