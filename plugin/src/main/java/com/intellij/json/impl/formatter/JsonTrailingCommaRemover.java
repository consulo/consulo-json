// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.formatter;

import com.intellij.json.JsonElementTypes;
import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.impl.JsonRecursiveElementVisitor;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.document.util.DocumentUtil;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.CodeStyle;
import consulo.language.codeStyle.PreFormatProcessor;
import consulo.language.psi.*;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@ExtensionImpl
public final class JsonTrailingCommaRemover implements PreFormatProcessor {
    @Override
    public @Nonnull TextRange process(@Nonnull ASTNode element, @Nonnull TextRange range) {
        PsiElement rootPsi = element.getPsi();
        if (rootPsi.getLanguage() != JsonLanguage.INSTANCE) {
            return range;
        }
        JsonCodeStyleSettings settings = CodeStyle.getCustomSettings(rootPsi.getContainingFile(), JsonCodeStyleSettings.class);
        if (settings.KEEP_TRAILING_COMMA) {
            return range;
        }
        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(rootPsi.getProject());
        Document document = psiDocumentManager.getDocument(rootPsi.getContainingFile());
        if (document == null) {
            return range;
        }
        DocumentUtil.executeInBulk(document, false, () -> {
            psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
            PsiElementVisitor visitor = new Visitor(document);
            rootPsi.accept(visitor);
            psiDocumentManager.commitDocument(document);
        });
        return range;
    }

    private static final class Visitor extends JsonRecursiveElementVisitor {
        private final Document myDocument;
        private int myOffsetDelta;

        Visitor(Document document) {
            myDocument = document;
        }

        @Override
        public void visitArray(@Nonnull JsonArray o) {
            super.visitArray(o);
            PsiElement lastChild = o.getLastChild();
            if (lastChild == null || lastChild.getNode().getElementType() != JsonElementTypes.R_BRACKET) {
                return;
            }
            deleteTrailingCommas(ObjectUtil.coalesce(ContainerUtil.getLastItem(o.getValueList()), o.getFirstChild()));
        }

        @Override
        public void visitObject(@Nonnull JsonObject o) {
            super.visitObject(o);
            PsiElement lastChild = o.getLastChild();
            if (lastChild == null || lastChild.getNode().getElementType() != JsonElementTypes.R_CURLY) {
                return;
            }
            deleteTrailingCommas(ObjectUtil.coalesce(ContainerUtil.getLastItem(o.getPropertyList()), o.getFirstChild()));
        }

        private void deleteTrailingCommas(@Nullable PsiElement lastElementOrOpeningBrace) {
            PsiElement element = lastElementOrOpeningBrace != null ? lastElementOrOpeningBrace.getNextSibling() : null;

            while (element != null) {
                if (element.getNode().getElementType() == JsonElementTypes.COMMA ||
                    element instanceof PsiErrorElement && ",".equals(element.getText())) {
                    deleteNode(element.getNode());
                }
                else if (!(element instanceof PsiComment || element instanceof PsiWhiteSpace)) {
                    break;
                }
                element = element.getNextSibling();
            }
        }

        private void deleteNode(@Nonnull ASTNode node) {
            int length = node.getTextLength();
            myDocument.deleteString(node.getStartOffset() + myOffsetDelta, node.getStartOffset() + length + myOffsetDelta);
            myOffsetDelta -= length;
        }
    }
}
