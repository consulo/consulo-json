// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema.walker;

import com.intellij.json.pointer.JsonPointerPosition;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.adapter.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import consulo.application.Application;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ThreeState;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public interface JsonLikePsiWalker {
    /**
     * Returns YES in place where a property name is expected,
     * NO in place where a property value is expected,
     * UNSURE where both property name and property value can be present
     */
    ThreeState isName(PsiElement element);

    boolean isPropertyWithValue(PsiElement element);

    @Nullable
    PsiElement findElementToCheck(PsiElement element);

    @Nullable
    JsonPointerPosition findPosition(final PsiElement element, boolean forceLastTransition);

    // for languages where objects and arrays are syntactically indistinguishable
    default boolean hasObjectArrayAmbivalence() {
        return false;
    }

    boolean requiresNameQuotes();

    default boolean requiresValueQuotes() {
        return true;
    }

    boolean allowsSingleQuotes();

    default boolean isValidIdentifier(String string, Project project) {
        return true;
    }

    default boolean isQuotedString(PsiElement element) {
        return false;
    }

    default String escapeInvalidIdentifier(String identifier) {
        return StringUtil.wrapWithDoubleQuote(identifier);
    }

    boolean hasMissingCommaAfter(PsiElement element);

    Set<String> getPropertyNamesOfParentObject(PsiElement originalPosition, PsiElement computedPosition);

    /**
     * Returns the indent of the given element in its file expressed in number of spaces
     */
    default int indentOf(PsiElement element) {
        return 0;
    }

    /**
     * Returns the indent, expressed in number of spaces, that this file has per indent level
     */
    default int indentOf(PsiFile file) {
        return 4;
    }

    @Nullable
    JsonPropertyAdapter getParentPropertyAdapter(PsiElement element);

    boolean isTopJsonElement(PsiElement element);

    @Nullable
    JsonValueAdapter createValueAdapter(PsiElement element);

    default TextRange adjustErrorHighlightingRange(PsiElement element) {
        return element.getTextRange();
    }

    default boolean acceptsEmptyRoot() {
        return false;
    }

    @Nullable
    Collection<PsiElement> getRoots(PsiFile file);

    /**
     * @deprecated This is currently a hack. If you think you need this too, add another method, because this one WILL be removed.
     */
    @Deprecated(forRemoval = true)
    default boolean requiresReformatAfterArrayInsertion() {
        return true;
    }

    static @Nullable JsonLikePsiWalker getWalker(final PsiElement element) {
        return getWalker(element, null);
    }

    static @Nullable JsonLikePsiWalker getWalker(final PsiElement element, @Nullable JsonSchemaObject schemaObject) {
        if (JsonOriginalPsiWalker.INSTANCE.handles(element)) {
            return JsonOriginalPsiWalker.INSTANCE;
        }

        JsonLikePsiWalkerFactory factory = Application.get().getExtensionPoint(JsonLikePsiWalkerFactory.class)
            .findFirstSafe(extension -> extension.handles(element));
        return factory == null ? null : factory.create(schemaObject);
    }

    default String getDefaultObjectValue() {
        return "{}";
    }

    default String getDefaultArrayValue() {
        return "[]";
    }

    default boolean hasWhitespaceDelimitedCodeBlocks() {
        return false;
    }

    default String getNodeTextForValidation(PsiElement element) {
        return element.getText();
    }

    default @Nullable JsonLikeSyntaxAdapter getSyntaxAdapter(Project project) {
        return null;
    }

    default @Nullable PsiElement getParentContainer(PsiElement element) {
        return null;
    }

    @Nullable
    PsiElement getPropertyNameElement(@Nullable PsiElement property);

    default String getPropertyValueSeparator(@Nullable JsonSchemaType valueType) {
        return ":";
    }

    // handling of exotic syntaxes where object properties can be located within object subsections and not directly
    default boolean haveSameParentWithinObject(PsiElement property1, PsiElement property2) {
        return true;
    }
}
