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
package com.jetbrains.jsonSchema.impl.fixes;

import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;
import com.intellij.json.JsonLanguage;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonValidationError;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectRendering;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectRenderingLanguage;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.walker.JsonLikeSyntaxAdapter;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.WriteAction;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.json.localize.JsonLocalize;
import consulo.language.Language;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.inspection.CommonProblemDescriptor;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.QuickFix;
import consulo.language.editor.intention.BatchQuickFix;
import consulo.language.editor.template.ConstantNode;
import consulo.language.editor.template.EmptyNode;
import consulo.language.editor.template.Expression;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.editor.template.TemplateManager;
import consulo.language.editor.template.macro.CompleteMacro;
import consulo.language.editor.template.macro.MacroCallNode;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class AddMissingPropertyFix implements LocalQuickFix, BatchQuickFix<CommonProblemDescriptor> {
    private final JsonValidationError.MissingMultiplePropsIssueData myData;
    private final JsonLikeSyntaxAdapter myQuickFixAdapter;

    public AddMissingPropertyFix(JsonValidationError.MissingMultiplePropsIssueData data,
                                 JsonLikeSyntaxAdapter quickFixAdapter) {
        myData = data;
        myQuickFixAdapter = quickFixAdapter;
    }

    public LocalizeValue getFamilyName() {
        return JsonLocalize.addMissingProperties();
    }

    @Override
    public LocalizeValue getName() {
        return JsonLocalize.addMissing0(myData.getMessage(true));
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    @Override
    @RequiredUIAccess
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        QuickFix[] fixes = descriptor.getFixes();
        AddMissingPropertyFix fix = fixes == null ? this : getWorkingQuickFix(fixes);
        if (fix == null) {
            return;
        }
        PsiElement element = descriptor.getPsiElement();
        if (element == null) {
            return;
        }
        PsiFile file = element.getContainingFile();
        SimpleReference<PsiElement> newElementRef = SimpleReference.create();
        WriteAction.run(() -> {
            SmartPsiElementPointer<PsiElement> smartPointer = SmartPointerManager.createPointer(element);
            // Reformatting the whole file because formatting is crucial for yaml files and
            // changes now should be inside ModCommand.
            // Reformatting inside `myQuickFixAdapter` doesn't work for non-physical elements
            assert file != null;
            CodeStyleManager.getInstance(project).reformatText(file, 0, file.getTextLength());
            PsiElement pointerElement = smartPointer.getElement();
            if (pointerElement != null) {
                fix.performFixInner(pointerElement, newElementRef);
            }
        });
        PsiElement newElement = newElementRef.get();
        // multiple properties: no template/caret move
        if (newElement == null) {
            return;
        }

        JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(newElement);
        if (walker == null) {
            return;
        }

        Editor editor = SuggestEnumValuesFix.findEditor(project, Objects.requireNonNull(newElement.getContainingFile()));
        if (editor == null) {
            return;
        }

        Collection<JsonValueAdapter> values = Objects.requireNonNull(walker.getParentPropertyAdapter(newElement)).getValues();
        PsiElement value;
        if (values.size() == 1) {
            value = values.iterator().next().getDelegate();
        }
        else {
            value = null;
        }
        if (value == null || value.getText().isBlank()) {
            editor.getCaretModel().moveToOffset(newElement.getTextRange().getEndOffset());
            return;
        }
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
        String text = value.getText();
        boolean isEmptyArray = StringUtil.equalsIgnoreWhitespaces(text, "[]");
        boolean isEmptyObject = StringUtil.equalsIgnoreWhitespaces(text, "{}");
        boolean goInside = isEmptyArray || isEmptyObject || StringUtil.isQuotedString(text);
        TextRange range = goInside ? TextRange.create(1, text.length() - 1) : TextRange.create(0, text.length());
        Expression expr = fix.myData.myMissingPropertyIssues.iterator().next().enumItemsCount > 1 || isEmptyObject
            ? new MacroCallNode(new CompleteMacro())
            : (isEmptyArray ? new EmptyNode() : new ConstantNode(goInside ? StringUtil.unquoteString(text) : text));

        editor.getCaretModel().moveToOffset(newElement.getTextRange().getStartOffset());
        TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(newElement);
        builder.replaceElement(value, range, "VALUE", expr, true);
        if (PsiTreeUtil.nextLeaf(newElement) != null) {
            builder.setEndVariableAfter(newElement);
        }
        else {
            builder.setEndVariableBefore(newElement.getLastChild());
        }
        WriteAction.run(() -> {
            Template template = builder.buildInlineTemplate();
            template.setToReformat(true);
            TemplateManager.getInstance(project).startTemplate(editor, template);
        });
    }

    @Override
    @RequiredUIAccess
    public void applyFix(Project project,
                         CommonProblemDescriptor[] descriptors,
                         List<PsiElement> psiElementsToIgnore,
                         @Nullable Runnable refreshViews) {
        if (descriptors.length == 0) {
            return;
        }
        WriteCommandAction.writeCommandAction(project).withName(getFamilyName().get()).run(() -> {
            for (CommonProblemDescriptor d : descriptors) {
                if (!(d instanceof ProblemDescriptor problemDescriptor)) {
                    continue;
                }
                QuickFix[] fixes = d.getFixes();
                AddMissingPropertyFix fix = fixes == null ? this : getWorkingQuickFix(fixes);
                if (fix == null) {
                    continue;
                }
                PsiElement element = problemDescriptor.getPsiElement();
                if (element == null) {
                    continue;
                }
                fix.performFixInner(element, SimpleReference.create());
            }
        });
        if (refreshViews != null) {
            refreshViews.run();
        }
    }

    public @Nullable PsiElement performFix(@Nullable PsiElement node) {
        if (node == null) {
            return null;
        }
        PsiElement element = node instanceof PsiFile ? node.getFirstChild() : node;
        if (element == null) {
            return null;
        }
        SimpleReference<PsiElement> newElementRef = SimpleReference.create(null);
        WriteAction.run(() -> {
            performFixInner(element, newElementRef);
        });
        return newElementRef.get();
    }

    @RequiredWriteAction
    public void performFixInner(PsiElement element, SimpleReference<PsiElement> newElementRef) {
        boolean isSingle = myData.myMissingPropertyIssues.size() == 1;
        PsiElement context = element instanceof PsiFile ? element.getFirstChild() : element;
        if (context == null) {
            return;
        }
        PsiElement processedElement = context;
        List<JsonValidationError.MissingPropertyIssueData> reverseOrder = ContainerUtil.reverse(new ArrayList<>(myData.myMissingPropertyIssues));
        for (JsonValidationError.MissingPropertyIssueData issue : reverseOrder) {
            Object defaultValueObject = issue.defaultValue;
            String defaultValue = formatDefaultValue(defaultValueObject, context.getLanguage());
            PsiElement property = myQuickFixAdapter.createProperty(
                issue.propertyName,
                defaultValue == null ? myQuickFixAdapter.getDefaultValueFromType(issue.propertyType) : defaultValue,
                processedElement.getProject()
            );
            PsiElement adjusted = myQuickFixAdapter.addProperty(processedElement, property);
            processedElement = adjusted;
            if (isSingle) {
                newElementRef.set(adjusted);
            }
        }
    }

    @Contract("null, _ -> null")
    @RequiredReadAction
    public @Nullable String formatDefaultValue(@Nullable Object defaultValueObject, Language targetLanguage) {
        if (defaultValueObject instanceof JsonSchemaObject schemaObject) {
            JsonSchemaObjectRenderingLanguage renderingLanguage = targetLanguage.is(JsonLanguage.INSTANCE)
                ? JsonSchemaObjectRenderingLanguage.JSON
                : JsonSchemaObjectRenderingLanguage.YAML;
            return JsonSchemaObjectRendering.renderSchemaNode(schemaObject, renderingLanguage);
        }
        else if (defaultValueObject instanceof JsonNode jsonNode) {
            return convertToYamlIfNeeded(targetLanguage, jsonNode);
        }
        else if (defaultValueObject instanceof String) {
            return StringUtil.wrapWithDoubleQuote(defaultValueObject.toString());
        }
        else if (defaultValueObject instanceof Boolean) {
            return Boolean.toString((Boolean) defaultValueObject);
        }
        else if (defaultValueObject instanceof Number) {
            return defaultValueObject.toString();
        }
        else if (defaultValueObject instanceof PsiElement) {
            return ((PsiElement) defaultValueObject).getText();
        }
        return null;
    }

    private static @Nullable String convertToYamlIfNeeded(Language language, JsonNode jsonNode) {
        TokenStreamFactory jacksonFactory;
        if (language.is(JsonLanguage.INSTANCE)) {
            jacksonFactory = new JsonFactory();
        }
        else {
            jacksonFactory = YAMLFactory.builder()
                .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
                .build();
        }

        try {
            String exampleInTargetLanguage = new ObjectMapper(jacksonFactory)
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(jsonNode);
            return StringUtil.trimEnd(exampleInTargetLanguage, "\n");
        }
        catch (JacksonException e) {
            return null;
        }
    }

    private static @Nullable AddMissingPropertyFix getWorkingQuickFix(QuickFix[] fixes) {
        for (QuickFix fix : fixes) {
            if (fix instanceof AddMissingPropertyFix addMissingPropertyFix) {
                return addMissingPropertyFix;
            }
        }
        return null;
    }
}
