// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension;

import com.intellij.json.pointer.JsonPointerPosition;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.JsonOriginalPsiWalker;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ThreeState;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Set;

public interface JsonLikePsiWalker {
  /**
   * Returns YES in place where a property name is expected,
   *         NO in place where a property value is expected,
   *         UNSURE where both property name and property value can be present
   */
  ThreeState isName(PsiElement element);

  boolean isPropertyWithValue(@Nonnull PsiElement element);

  PsiElement findElementToCheck(final @Nonnull PsiElement element);

  @Nullable
  JsonPointerPosition findPosition(final @Nonnull PsiElement element, boolean forceLastTransition);

  // for languages where objects and arrays are syntactically indistinguishable
  default boolean hasObjectArrayAmbivalence() { return false; }

  boolean requiresNameQuotes();
  default boolean requiresValueQuotes() { return true; }
  boolean allowsSingleQuotes();
  default boolean isValidIdentifier(@Nonnull String string, Project project) { return true; }

  default boolean isQuotedString(@Nonnull PsiElement element) { return false; }

  default String escapeInvalidIdentifier(@Nonnull String identifier) {
    return StringUtil.wrapWithDoubleQuote(identifier);
  }

  boolean hasMissingCommaAfter(@Nonnull PsiElement element);

  Set<String> getPropertyNamesOfParentObject(@Nonnull PsiElement originalPosition, PsiElement computedPosition);

  /** Returns the indent of the given element in its file expressed in number of spaces */
  default int indentOf(@Nonnull PsiElement element) {
    return 0;
  }

  /** Returns the indent, expressed in number of spaces, that this file has per indent level */
  default int indentOf(@Nonnull PsiFile file) {
    return 4;
  }

  @Nullable
  JsonPropertyAdapter getParentPropertyAdapter(@Nonnull PsiElement element);
  boolean isTopJsonElement(@Nonnull PsiElement element);
  @Nullable
  JsonValueAdapter createValueAdapter(@Nonnull PsiElement element);

  default TextRange adjustErrorHighlightingRange(@Nonnull PsiElement element) {
    return element.getTextRange();
  }

  default boolean acceptsEmptyRoot() { return false; }

  @Nullable
  Collection<PsiElement> getRoots(@Nonnull PsiFile file);

  /** @deprecated This is currently a hack. If you think you need this too, add another method, because this one WILL be removed. */
  @Deprecated(forRemoval = true)
  default boolean requiresReformatAfterArrayInsertion() {
    return true;
  }

  static @Nullable JsonLikePsiWalker getWalker(final @Nonnull PsiElement element) {
    return getWalker(element, null);
  }

  static @Nullable JsonLikePsiWalker getWalker(final @Nonnull PsiElement element, @Nullable JsonSchemaObject schemaObject) {
    if (JsonOriginalPsiWalker.INSTANCE.handles(element)) return JsonOriginalPsiWalker.INSTANCE;

    return JsonLikePsiWalkerFactory.EXTENSION_POINT_NAME.getExtensionList().stream()
      .filter(extension -> extension.handles(element))
      .findFirst()
      .map(extension -> extension.create(schemaObject))
      .orElse(null);
  }

  default String getDefaultObjectValue() { return "{}"; }
  default String getDefaultArrayValue() { return "[]"; }

  default boolean hasWhitespaceDelimitedCodeBlocks() { return false; }

  default String getNodeTextForValidation(PsiElement element) { return element.getText(); }

  default JsonLikeSyntaxAdapter getSyntaxAdapter(Project project) { return null; }

  default @Nullable PsiElement getParentContainer(PsiElement element) {
    return null;
  }

  @Nullable
  PsiElement getPropertyNameElement(@Nullable PsiElement property);

  default String getPropertyValueSeparator(@Nullable JsonSchemaType valueType) { return ":"; }

  // handling of exotic syntaxes where object properties can be located within object subsections and not directly
  default boolean haveSameParentWithinObject(@Nonnull PsiElement property1, @Nonnull PsiElement property2) { return true; }
}
