// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.structureView;

import com.intellij.json.psi.JsonFile;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.fileEditor.structureView.StructureViewBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Used for customization of default structure view for JSON files.
 * Note that in case several extensions for current EP are registered,
 * the behaviour is undefined and can be changed by `order` attribute in EP registration.
 * Therefore, there is no guarantee that the expected builder will be returned every time.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface JsonCustomStructureViewFactory {
  ExtensionPointName<JsonCustomStructureViewFactory> EP_NAME = ExtensionPointName.create(JsonCustomStructureViewFactory.class);

  /**
   * The first not-null builder received from all registered extensions will be used for building structure view.
   * If the extension list is empty, the default implementation is used.
   *
   * @return a structure view builder for the given JSON file or {@code null} if the file doesn't need customized structure view.
   */
  @Nullable
  StructureViewBuilder getStructureViewBuilder(final @NotNull JsonFile jsonFile);
}
