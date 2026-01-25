// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.jetbrains.jsonSchema.impl.nestedCompletions.NestedCompletionsNode;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.ApiStatus;

import java.util.stream.StreamSupport;

/**
 * Extension point used for extending completion provided by {@link com.jetbrains.jsonSchema.impl.JsonSchemaCompletionContributor}.
 * Provided instance of {@link NestedCompletionsNode} will be converted into a completion item with a several level json/yaml tree to insert.
 * See the {@link NestedCompletionsNode}'s documentation for a more detailed description of how exactly the completion item's text will be constructed.
 */
@ApiStatus.Experimental
public interface JsonSchemaNestedCompletionsTreeProvider {
  ExtensionPointName<JsonSchemaNestedCompletionsTreeProvider> EXTENSION_POINT_NAME =
    ExtensionPointName.create("com.intellij.json.jsonSchemaNestedCompletionsTreeProvider");

  @Nullable
  static NestedCompletionsNode getNestedCompletionsData(PsiFile editedFile) {
    return StreamSupport.stream(EXTENSION_POINT_NAME.getExtensionsIfPointIsRegistered().spliterator(), false)
      .map(extension -> extension.getNestedCompletionsRoot(editedFile))
      .filter(node -> node != null)
      .reduce((acc, next) -> acc.merge(next))
      .orElse(null);
  }

  /**
   * @return null if you do not want to alter the json schema-based completion for this file
   */
  @Nullable
  NestedCompletionsNode getNestedCompletionsRoot(PsiFile editedFile);
}
