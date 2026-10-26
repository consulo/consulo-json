// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
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
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import org.jspecify.annotations.Nullable;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface JsonSchemaCompletionCustomizer {

    /**
     * Whether this customization is applicable to a file.
     * Normally there should be just one customizer per file, otherwise the behavior is not defined
     */
    boolean isApplicable(PsiFile file);

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
        PsiElement completionElement) {
        return createHandlerForEnumValue(schema, value);
    }

    /**
     * Whether to accept the completion item for a property
     */
    default boolean acceptsPropertyCompletionItem(JsonSchemaObject propertySchema, PsiElement completionElement) { return true; }
}
