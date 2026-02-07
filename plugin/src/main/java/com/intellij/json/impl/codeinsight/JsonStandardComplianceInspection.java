// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.codeinsight;

import com.intellij.json.JsonDialectUtil;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.JsonLanguage;
import com.intellij.json.codeinsight.StandardJsonLiteralChecker;
import com.intellij.json.jsonLines.JsonLinesFileType;
import com.intellij.json.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.json.localize.JsonLocalize;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Compliance checks include
 * <ul>
 * <li>Usage of line and block commentaries</li>
 * <li>Usage of single quoted strings</li>
 * <li>Usage of identifiers (unqouted words)</li>
 * <li>Not double quoted string literal is used as property key</li>
 * <li>Multiple top-level values</li>
 * </ul>
 *
 * @author Mikhail Golubev
 */
@ExtensionImpl
public class JsonStandardComplianceInspection extends LocalInspectionTool {

    public boolean myWarnAboutComments = true;
    public boolean myWarnAboutNanInfinity = true;
    public boolean myWarnAboutTrailingCommas = true;
    public boolean myWarnAboutMultipleTopLevelValues = true;

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        return LocalizeValue.empty();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return JsonLocalize.inspectionComplianceName();
    }

    @Override
    public @Nonnull HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.ERROR;
    }

    @Override
    public @Nonnull PsiElementVisitor buildVisitor(final @Nonnull ProblemsHolder holder, boolean isOnTheFly) {
        if (!JsonDialectUtil.isStandardJson(holder.getFile())) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new StandardJsonValidatingElementVisitor(holder);
    }

    protected static @Nullable PsiElement findTrailingComma(@Nonnull PsiElement lastChild, @Nonnull IElementType ending) {
        if (lastChild.getNode().getElementType() != ending) {
            return null;
        }
        final PsiElement beforeEnding = PsiTreeUtil.skipWhitespacesAndCommentsBackward(lastChild);
        if (beforeEnding != null && beforeEnding.getNode().getElementType() == JsonElementTypes.COMMA) {
            return beforeEnding;
        }
        return null;
    }

//  @Override
//  public @NotNull OptPane getOptionsPane() {
//    return pane(
//      checkbox("myWarnAboutComments", JsonBundle.message("inspection.compliance.option.comments")),
//      checkbox("myWarnAboutMultipleTopLevelValues", JsonBundle.message("inspection.compliance.option.multiple.top.level.values")),
//      checkbox("myWarnAboutTrailingCommas", JsonBundle.message("inspection.compliance.option.trailing.comma")),
//      checkbox("myWarnAboutNanInfinity", JsonBundle.message("inspection.compliance.option.nan.infinity")));
//  }

    protected static @Nonnull String escapeSingleQuotedStringContent(@Nonnull String content) {
        final StringBuilder result = new StringBuilder();
        boolean nextCharEscaped = false;
        for (int i = 0; i < content.length(); i++) {
            final char c = content.charAt(i);
            if ((nextCharEscaped && c != '\'') || (!nextCharEscaped && c == '"')) {
                result.append('\\');
            }
            if (c != '\\' || nextCharEscaped) {
                result.append(c);
                nextCharEscaped = false;
            }
            else {
                nextCharEscaped = true;
            }
        }
        if (nextCharEscaped) {
            result.append('\\');
        }
        return result.toString();
    }

    private static final class AddDoubleQuotesFix implements LocalQuickFix {
        @Nonnull
        @Override
        public LocalizeValue getName() {
            return JsonLocalize.quickfixAddDoubleQuotesDesc();
        }

        @Override
        public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor problemDescriptor) {
            PsiElement element = problemDescriptor.getPsiElement();

            final String rawText = element.getText();
            if (element instanceof JsonLiteral || element instanceof JsonReferenceExpression) {
                String content = JsonPsiUtil.stripQuotes(rawText);
                if (element instanceof JsonStringLiteral && rawText.startsWith("'")) {
                    content = escapeSingleQuotedStringContent(content);
                }
                TextRange range = element.getTextRange();
                // Replace in document to avoid reformat
                element.getContainingFile().getFileDocument()
                    .replaceString(range.getStartOffset(), range.getEndOffset(), "\"" + content + "\"");
            }
            else {
                Logger.getInstance(JsonStandardComplianceInspection.class)
                    .error("Quick fix was applied to unexpected element", rawText, element.getParent().getText());
            }
        }
    }

    protected class StandardJsonValidatingElementVisitor extends JsonElementVisitor {
        private final ProblemsHolder myHolder;
        private static final String MISSING_VALUE = "missingValue";

        public StandardJsonValidatingElementVisitor(ProblemsHolder holder) {
            myHolder = holder;
        }

        protected boolean allowComments() {
            return false;
        }

        protected boolean allowSingleQuotes() {
            return false;
        }

        protected boolean allowIdentifierPropertyNames() {
            return false;
        }

        protected boolean allowTrailingCommas() {
            return false;
        }

        protected boolean isValidPropertyName(@Nonnull PsiElement literal) {
            return literal instanceof JsonLiteral && JsonPsiUtil.getElementTextWithoutHostEscaping(literal).startsWith("\"");
        }

        @Override
        public void visitComment(@Nonnull PsiComment comment) {
            if (!allowComments() && myWarnAboutComments) {
                if (JsonStandardComplianceProvider.shouldWarnAboutComment(comment) &&
                    comment.getContainingFile().getLanguage() instanceof JsonLanguage) {
                    myHolder.registerProblem(comment, JsonLocalize.inspectionComplianceMsgComments().get());
                }
            }
        }

        @Override
        public void visitStringLiteral(@Nonnull JsonStringLiteral stringLiteral) {
            if (!allowSingleQuotes() && JsonPsiUtil.getElementTextWithoutHostEscaping(stringLiteral).startsWith("'")) {
                myHolder.registerProblem(stringLiteral, JsonLocalize.inspectionComplianceMsgSingleQuotedStrings().get(),
                    new AddDoubleQuotesFix());
            }
            // May be illegal property key as well
            super.visitStringLiteral(stringLiteral);
        }

        @Override
        public void visitLiteral(@Nonnull JsonLiteral literal) {
            if (JsonPsiUtil.isPropertyKey(literal) && !isValidPropertyName(literal)) {
                myHolder.registerProblem(literal, JsonLocalize.inspectionComplianceMsgIllegalPropertyKey().get(), new AddDoubleQuotesFix());
            }

            // for standard JSON, the inspection for NaN, Infinity and -Infinity is now configurable
            if (!allowNanInfinity() && literal instanceof JsonNumberLiteral && myWarnAboutNanInfinity) {
                final String text = JsonPsiUtil.getElementTextWithoutHostEscaping(literal);
                if (StandardJsonLiteralChecker.INF.equals(text) ||
                    StandardJsonLiteralChecker.MINUS_INF.equals(text) ||
                    StandardJsonLiteralChecker.NAN.equals(text)) {
                    myHolder.registerProblem(literal, JsonLocalize.syntaxErrorIllegalFloatingPointLiteral().get());
                }
            }
            super.visitLiteral(literal);
        }

        protected boolean allowNanInfinity() {
            return false;
        }

        @Override
        public void visitReferenceExpression(@Nonnull JsonReferenceExpression reference) {
            if (!allowIdentifierPropertyNames() || !JsonPsiUtil.isPropertyKey(reference) || !isValidPropertyName(reference)) {
                if (!MISSING_VALUE.equals(reference.getText()) || !InjectedLanguageManager.getInstance(myHolder.getProject()).isInjectedFragment(myHolder.getFile())) {
                    myHolder.registerProblem(reference, JsonLocalize.inspectionComplianceMsgBadToken().get(), new AddDoubleQuotesFix());
                }
            }
            // May be illegal property key as well
            super.visitReferenceExpression(reference);
        }

        @Override
        public void visitArray(@Nonnull JsonArray array) {
            if (myWarnAboutTrailingCommas && !allowTrailingCommas() &&
                JsonStandardComplianceProvider.shouldWarnAboutTrailingComma(array)) {
                final PsiElement trailingComma = findTrailingComma(array.getLastChild(), JsonElementTypes.R_BRACKET);
                if (trailingComma != null) {
                    myHolder.registerProblem(trailingComma, JsonLocalize.inspectionComplianceMsgTrailingComma().get());
                }
            }
            super.visitArray(array);
        }

        @Override
        public void visitObject(@Nonnull JsonObject object) {
            if (myWarnAboutTrailingCommas && !allowTrailingCommas() &&
                JsonStandardComplianceProvider.shouldWarnAboutTrailingComma(object)) {
                final PsiElement trailingComma = findTrailingComma(object.getLastChild(), JsonElementTypes.R_CURLY);
                if (trailingComma != null) {
                    myHolder.registerProblem(trailingComma, JsonLocalize.inspectionComplianceMsgTrailingComma().get());
                }
            }
            super.visitObject(object);
        }

        @Override
        public void visitValue(@Nonnull JsonValue value) {
            if (value.getContainingFile() instanceof JsonFile jsonFile) {
                if (myWarnAboutMultipleTopLevelValues &&
                    value.getParent() == jsonFile &&
                    value != jsonFile.getTopLevelValue() &&
                    jsonFile.getFileType() != JsonLinesFileType.INSTANCE) {
                    myHolder.registerProblem(value, JsonLocalize.inspectionComplianceMsgMultipleTopLevelValues().get());
                }
            }
        }
    }
}
