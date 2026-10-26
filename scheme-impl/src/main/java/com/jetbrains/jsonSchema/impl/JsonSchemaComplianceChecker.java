// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.pointer.JsonPointerPosition;
import com.jetbrains.jsonSchema.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.JsonValidationError;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.adapter.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.fixes.AddMissingPropertyFix;
import com.jetbrains.jsonSchema.impl.fixes.FixPropertyNameTypoFix;
import com.jetbrains.jsonSchema.impl.fixes.RemoveDuplicateArrayItemsFix;
import com.jetbrains.jsonSchema.impl.fixes.RemoveProhibitedPropertyFix;
import com.jetbrains.jsonSchema.impl.fixes.SuggestEnumValuesFix;
import com.jetbrains.jsonSchema.internal.JsonSchemaAnnotatorChecker;
import com.jetbrains.jsonSchema.internal.JsonSchemaResolver;
import com.jetbrains.jsonSchema.internal.MatchResult;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.walker.JsonLikeSyntaxAdapter;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import consulo.util.dataholder.Key;
import consulo.util.lang.ref.SimpleReference;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class JsonSchemaComplianceChecker {
    private static final Key<Set<PsiElement>> ANNOTATED_PROPERTIES = Key.create("JsonSchema.Properties.Annotated");

    private final JsonSchemaObject myRootSchema;
    private final ProblemsHolder myHolder;
    private final JsonLikePsiWalker myWalker;
    private final LocalInspectionToolSession mySession;
    private final JsonComplianceCheckerOptions myOptions;
    private final @Nullable String myMessagePrefix;
    private final ProblemHighlightType myHighlightType;

    public JsonSchemaComplianceChecker(JsonSchemaObject rootSchema,
                                       ProblemsHolder holder,
                                       JsonLikePsiWalker walker,
                                       LocalInspectionToolSession session,
                                       JsonComplianceCheckerOptions options) {
        this(rootSchema, holder, walker, session, options, null);
    }

    public JsonSchemaComplianceChecker(JsonSchemaObject rootSchema,
                                       ProblemsHolder holder,
                                       JsonLikePsiWalker walker,
                                       LocalInspectionToolSession session,
                                       JsonComplianceCheckerOptions options,
                                       @Nullable String messagePrefix) {
        myRootSchema = rootSchema;
        myHolder = holder;
        myWalker = walker;
        mySession = session;
        myOptions = options;
        myMessagePrefix = messagePrefix;
        myHighlightType = getValidationHighlightType(holder.getFile(), rootSchema);
    }

    private static ProblemHighlightType getValidationHighlightType(PsiFile file, JsonSchemaObject rootSchema) {
        JsonSchemaService service = JsonSchemaService.get(file.getProject());
        JsonSchemaFileProvider provider = service.getSchemaProviderForFile(file.getViewProvider().getVirtualFile(), rootSchema);
        return provider == null ? ProblemHighlightType.GENERIC_ERROR_OR_WARNING : provider.getValidationHighlightType();
    }

    @RequiredReadAction
    public void annotate(PsiElement element) {
        doAnnotate(element);
    }

    @RequiredReadAction
    private void doAnnotate(PsiElement element) {
        Project project = element.getProject();
        JsonPropertyAdapter firstProp = myWalker.getParentPropertyAdapter(element);
        if (firstProp != null) {
            JsonPointerPosition position = myWalker.findPosition(firstProp.getDelegate(), true);
            if (position == null || position.isEmpty()) {
                return;
            }
            MatchResult result = new JsonSchemaResolver(project, myRootSchema, position, firstProp.getNameValueAdapter()).detailedResolve();
            for (JsonValueAdapter value : firstProp.getValues()) {
                createWarnings(JsonSchemaAnnotatorChecker.checkByMatchResult(project, value, result, myOptions));
            }
        }
        checkRoot(element, firstProp);
    }

    @RequiredReadAction
    private void checkRoot(PsiElement element, @Nullable JsonPropertyAdapter firstProp) {
        JsonValueAdapter rootToCheck;
        if (firstProp == null) {
            rootToCheck = findTopLevelElement(myWalker, element);
        }
        else {
            rootToCheck = firstProp.getParentObject();
            if (rootToCheck == null || !myWalker.isTopJsonElement(Objects.requireNonNull(rootToCheck.getDelegate().getParent()))) {
                return;
            }
        }
        if (rootToCheck != null) {
            Project project = element.getProject();
            MatchResult matchResult = new JsonSchemaResolver(project, myRootSchema, new JsonPointerPosition(), rootToCheck).detailedResolve();
            createWarnings(JsonSchemaAnnotatorChecker.checkByMatchResult(project, rootToCheck, matchResult, myOptions));
        }
    }

    @RequiredReadAction
    protected void createWarnings(@Nullable JsonSchemaAnnotatorChecker checker) {
        if (checker == null || checker.isCorrect()) {
            return;
        }
        // compute intersecting ranges - we'll solve warning priorities based on this information
        List<TextRange> ranges = new ArrayList<>();
        List<List<Map.Entry<PsiElement, JsonValidationError>>> entries = new ArrayList<>();
        for (Map.Entry<PsiElement, JsonValidationError> entry : checker.getErrors().entrySet()) {
            TextRange range = myWalker.adjustErrorHighlightingRange(entry.getKey());
            boolean processed = false;
            for (int i = 0; i < ranges.size(); i++) {
                TextRange currRange = ranges.get(i);
                if (currRange.intersects(range)) {
                    ranges.set(i, new TextRange(Math.min(currRange.getStartOffset(), range.getStartOffset()),
                        Math.max(currRange.getEndOffset(), range.getEndOffset())));
                    entries.get(i).add(entry);
                    processed = true;
                    break;
                }
            }
            if (processed) {
                continue;
            }

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

    @RequiredReadAction
    private void registerError(PsiElement psiElement, TextRange range, JsonValidationError validationError) {
        if (checkIfAlreadyProcessed(psiElement)) {
            return;
        }
        String value = validationError.getMessage();
        if (myMessagePrefix != null) {
            value = myMessagePrefix + value;
        }
        LocalQuickFix[] fix = createFixes(validationError, myWalker.getSyntaxAdapter(myHolder.getProject()));
        PsiElement element = range.isEmpty() ? psiElement.getContainingFile() : psiElement;
        myHolder.newProblem(LocalizeValue.of(value))
            .range(element, range)
            .highlightType(myHighlightType)
            .withFixes(fix)
            .create();
    }

    public static LocalQuickFix[] createFixes(JsonValidationError validationError, @Nullable JsonLikeSyntaxAdapter quickFixAdapter) {
        if (quickFixAdapter == null) {
            return LocalQuickFix.EMPTY_ARRAY;
        }
        JsonValidationError.@Nullable IssueData issueData = validationError.getIssueData();
        return switch (validationError.getFixableIssueKind()) {
            case MissingProperty -> new AddMissingPropertyFix[]{
                new AddMissingPropertyFix((JsonValidationError.MissingMultiplePropsIssueData) Objects.requireNonNull(issueData), quickFixAdapter)
            };
            case MissingOneOfProperty, MissingAnyOfProperty ->
                ((JsonValidationError.MissingOneOfPropsIssueData) Objects.requireNonNull(issueData)).myExclusiveOptions.stream()
                    .map(d -> new AddMissingPropertyFix(d, quickFixAdapter))
                    .toArray(LocalQuickFix[]::new);
            case ProhibitedProperty -> getProhibitedPropertyFixes(validationError, quickFixAdapter);
            case NonEnumValue -> new SuggestEnumValuesFix[]{new SuggestEnumValuesFix(quickFixAdapter)};
            case DuplicateArrayItem -> new RemoveDuplicateArrayItemsFix[]{
                new RemoveDuplicateArrayItemsFix(((JsonValidationError.DuplicateArrayItemIssueData) Objects.requireNonNull(issueData)).duplicateIndices)
            };
            default -> LocalQuickFix.EMPTY_ARRAY;
        };
    }

    private static LocalQuickFix[] getProhibitedPropertyFixes(JsonValidationError validationError, JsonLikeSyntaxAdapter quickFixAdapter) {
        JsonValidationError.ProhibitedPropertyIssueData data = (JsonValidationError.ProhibitedPropertyIssueData) Objects.requireNonNull(validationError.getIssueData());
        if (data.typoCandidates.isEmpty()) {
            return new RemoveProhibitedPropertyFix[]{new RemoveProhibitedPropertyFix(data, quickFixAdapter)};
        }
        List<LocalQuickFix> allFixes = new ArrayList<>();
        for (String candidate : data.typoCandidates) {
            allFixes.add(new FixPropertyNameTypoFix(candidate, quickFixAdapter));
        }
        allFixes.add(new RemoveProhibitedPropertyFix(data, quickFixAdapter));
        return allFixes.toArray(LocalQuickFix[]::new);
    }

    @RequiredReadAction
    private static @Nullable JsonValueAdapter findTopLevelElement(JsonLikePsiWalker walker, PsiElement element) {
        SimpleReference<PsiElement> ref = new SimpleReference<>();
        PsiTreeUtil.findFirstParent(element, el -> {
            boolean isTop = walker.isTopJsonElement(el);
            if (!isTop) {
                ref.set(el);
            }
            return isTop;
        });
        return ref.isNull() ? (walker.acceptsEmptyRoot() ? walker.createValueAdapter(element) : null) : walker.createValueAdapter(Objects.requireNonNull(ref.get()));
    }

    private boolean checkIfAlreadyProcessed(PsiElement property) {
        Set<PsiElement> data = mySession.getUserData(ANNOTATED_PROPERTIES);
        if (data == null) {
            data = mySession.putUserDataIfAbsent(ANNOTATED_PROPERTIES, ContainerUtil.newConcurrentSet());
        }
        return !data.add(property);
    }
}
