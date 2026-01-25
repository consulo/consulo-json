// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.remote;

import consulo.component.extension.ExtensionPointName;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * Disables applying schemastore.org JSON schema mappings for particular files.
 */
public interface JsonSchemaCatalogExclusion {
  ExtensionPointName<JsonSchemaCatalogExclusion> EP_NAME = ExtensionPointName.create("com.intellij.json.catalog.exclusion");

  boolean isExcluded(@NotNull VirtualFile file);
}
