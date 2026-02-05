/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.extension;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Implement to contribute a JSON-adapter for your language. This allows to run JSON Schemas on non JSON languages.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface JsonLikePsiWalkerFactory {
  ExtensionPointName<JsonLikePsiWalkerFactory> EXTENSION_POINT_NAME = ExtensionPointName.create(JsonLikePsiWalkerFactory.class);

  boolean handles(@Nonnull PsiElement element);

  @Nonnull
  JsonLikePsiWalker create(@Nullable JsonSchemaObject schemaObject);
}
