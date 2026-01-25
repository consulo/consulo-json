// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This API provides a mechanism to enable JSON schemas in particular files
 * This interface should be implemented if you want a particular kind of virtual files to have access to JsonSchemaService APIs
 *
 * This API is new in IntelliJ IDEA Platform 2018.2
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface JsonSchemaEnabler {
  ExtensionPointName<JsonSchemaEnabler> EXTENSION_POINT_NAME = ExtensionPointName.create(JsonSchemaEnabler.class);

  /**
   * This method should return true if JSON schema mechanism should become applicable to corresponding file.
   * This method SHOULD NOT ADDRESS INDEXES.
   * @param file Virtual file to check for
   * @param project Current project
   * @return true if available, false otherwise
   */
  boolean isEnabledForFile(@NotNull VirtualFile file, @Nullable Project project);

  /**
   * This method enables/disables JSON schema selection widget
   * This method SHOULD NOT ADDRESS INDEXES
   */
  default boolean shouldShowSwitcherWidget(VirtualFile file) {
    return true;
  }

  default boolean canBeSchemaFile(VirtualFile file) {
    return false;
  }
}
