// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.impl.pointer.JsonPointerPosition;
import com.jetbrains.jsonSchema.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonValidationError;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.fus.JsonSchemaHighlightingSessionStatisticsCollector;
import consulo.document.util.TextRange;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.collection.SmartList;
import consulo.util.concurrent.ConcurrencyUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class JsonSchemaComplianceChecker {
  private static final Key<Set<PsiElement>> ANNOTATED_PROPERTIES = Key.create("JsonSchema.Properties.Annotated");

  private final @Nonnull JsonSchemaObject myRootSchema;
  private final @Nonnull ProblemsHolder myHolder;
  private final @Nonnull JsonLikePsiWalker myWalker;
  private final LocalInspectionToolSession mySession;
  private final @Nonnull JsonComplianceCheckerOptions myOptions;
  private final @Nullable
  @Nls String myMessagePrefix;

  public JsonSchemaComplianceChecker(@Nonnull JsonSchemaObject rootSchema,
                                     @Nonnull ProblemsHolder holder,
                                     @Nonnull JsonLikePsiWalker walker,
                                     @Nonnull LocalInspectionToolSession session,
                                     @Nonnull JsonComplianceCheckerOptions options) {
    this(rootSchema, holder, walker, session, options, null);
  }

  public JsonSchemaComplianceChecker(@Nonnull JsonSchemaObject rootSchema,
                                     @Nonnull ProblemsHolder holder,
                                     @Nonnull JsonLikePsiWalker walker,
                                     @Nonnull LocalInspectionToolSession session,
                                     @Nonnull JsonComplianceCheckerOptions options,
                                     @Nullable @Nls String messagePrefix) {
    myRootSchema = rootSchema;
    myHolder = holder;
    myWalker = walker;
    mySession = session;
    myOptions = options;
    myMessagePrefix = messagePrefix;
  }

  public void annotate(final @Nonnull PsiElement element) {
    JsonSchemaHighlightingSessionStatisticsCollector.getInstance().recordSchemaFeaturesUsage(myRootSchema, () -> doAnnotate(element));
  }

  private void doAnnotate(@Nonnull PsiElement element) {
    Project project = element.getProject();
    final JsonPropertyAdapter firstProp = myWalker.getParentPropertyAdapter(element);
    if (firstProp != null) {
      final JsonPointerPosition position = myWalker.findPosition(firstProp.getDelegate(), true);
      if (position == null || position.isEmpty()) return;
      final MatchResult result = new JsonSchemaResolver(project, myRootSchema, position, firstProp.getNameValueAdapter()).detailedResolve();
      for (JsonValueAdapter value : firstProp.getValues()) {
        createWarnings(JsonSchemaAnnotatorChecker.checkByMatchResult(project, value, result, myOptions));
      }
    }
    checkRoot(element, firstProp);
  }

  private void checkRoot(@Nonnull PsiElement element, @Nullable JsonPropertyAdapter firstProp) {
    JsonValueAdapter rootToCheck;
    if (firstProp == null) {
      rootToCheck = findTopLevelElement(myWalker, element);
    } else {
      rootToCheck = firstProp.getParentObject();
      if (rootToCheck == null || !myWalker.isTopJsonElement(rootToCheck.getDelegate().getParent())) {
        return;
      }
    }
    if (rootToCheck != null) {
      Project project = element.getProject();
      final MatchResult matchResult = new JsonSchemaResolver(project, myRootSchema, new JsonPointerPosition(), rootToCheck).detailedResolve();
      createWarnings(JsonSchemaAnnotatorChecker.checkByMatchResult(project, rootToCheck, matchResult, myOptions));
    }
  }

  @ApiStatus.Internal
  protected void createWarnings(@Nullable JsonSchemaAnnotatorChecker checker) {
    if (checker == null || checker.isCorrect()) return;
    // compute intersecting ranges - we'll solve warning priorities based on this information
    List<TextRange> ranges = new ArrayList<>();
    List<List<Map.Entry<PsiElement, JsonValidationError>>> entries = new ArrayList<>();
    for (Map.Entry<PsiElement, JsonValidationError> entry : checker.getErrors().entrySet()) {
      TextRange range = myWalker.adjustErrorHighlightingRange(entry.getKey());
      boolean processed = false;
      for (int i = 0; i < ranges.size(); i++) {
        TextRange currRange = ranges.get(i);
        if (currRange.intersects(range)) {
          ranges.set(i, new TextRange(Math.min(currRange.getStartOffset(), range.getStartOffset()), Math.max(currRange.getEndOffset(), range.getEndOffset())));
          entries.get(i).add(entry);
          processed = true;
          break;
        }
      }
      if (processed) continue;

      ranges.add(range);
      entries.add(new SmartList<>(entry));
    }

    // for each set of intersecting ranges, compute the best errors to show
    for (List<Map.Entry<PsiElement, JsonValidationError>> entryList : entries) {
      int min = entryList.stream().map(v -> v.getValue().getPriority().ordinal()).min(Integer::compareTo).orElse(Integer.MAX_VALUE);
      for (Map.Entry<PsiElement, JsonValidationError> entry : entryList) {
        JsonValidationError validationError = entry.getValue();
        PsiElement psiElement = entry.getKey();
        if (validationError.getPriority().ordinal() > min) {
          continue;
        }
        TextRange range = myWalker.adjustErrorHighlightingRange(psiElement);
        range = range.shiftLeft(psiElement.getTextRange().getStartOffset());
        registerError(psiElement, range, validationError);
      }
    }
  }

  private void registerError(@Nonnull PsiElement psiElement, @Nonnull TextRange range, @Nonnull JsonValidationError validationError) {
    if (checkIfAlreadyProcessed(psiElement)) return;
    String value = validationError.getMessage();
    if (myMessagePrefix != null) value = myMessagePrefix + value;
    LocalQuickFix[] fix = validationError.createFixes(myWalker.getSyntaxAdapter(myHolder.getProject()));
    PsiElement element = range.isEmpty() ? psiElement.getContainingFile() : psiElement;
    if (fix.length == 0) {
      myHolder.registerProblem(element, range, value);
    }
    else {
      myHolder.registerProblem(element, range, value, fix);
    }
  }

  private static JsonValueAdapter findTopLevelElement(@Nonnull JsonLikePsiWalker walker, @Nonnull PsiElement element) {
    final Ref<PsiElement> ref = new Ref<>();
    PsiTreeUtil.findFirstParent(element, el -> {
      final boolean isTop = walker.isTopJsonElement(el);
      if (!isTop) ref.set(el);
      return isTop;
    });
    return ref.isNull() ? (walker.acceptsEmptyRoot() ? walker.createValueAdapter(element) : null) : walker.createValueAdapter(ref.get());
  }

  private boolean checkIfAlreadyProcessed(@Nonnull PsiElement property) {
    Set<PsiElement> data = ConcurrencyUtil.computeIfAbsent(mySession, ANNOTATED_PROPERTIES, () -> ConcurrentCollectionFactory.createConcurrentSet());
    return !data.add(property);
  }
}
