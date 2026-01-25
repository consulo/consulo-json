// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface JsonSchemaCompletionCustomizer {
  ExtensionPointName<JsonSchemaCompletionCustomizer> EXTENSION_POINT_NAME = ExtensionPointName.create(JsonSchemaCompletionCustomizer.class);

  /**
   * Whether this customization is applicable to a file.
   * Normally there should be just one customizer per file, otherwise the behavior is not defined
   */
  boolean isApplicable(@NotNull PsiFile file);

  /**
   * Allows customizing insertion handler for enum values (e.g., to turn a value into a more complicated structure).
   * If it returns null, the default handler will be invoked.
   */
  default @Nullable InsertHandler<LookupElement> createHandlerForEnumValue(JsonSchemaObject schema, String value) {
    return null;
  }

  /**
   * Allows customizing insertion handler for enum values (e.g., to turn a value into a more complicated structure).
   * If it returns null, the default handler will be invoked.
   */
  default @Nullable InsertHandler<LookupElement> createHandlerForEnumValue(
    JsonSchemaObject schema,
    String value,
    @NotNull PsiElement completionElement) {
    return createHandlerForEnumValue(schema, value);
  }

  /**
   * Whether to accept the completion item for a property
   */
  default boolean acceptsPropertyCompletionItem(JsonSchemaObject propertySchema, @NotNull PsiElement completionElement) { return true; }
}
