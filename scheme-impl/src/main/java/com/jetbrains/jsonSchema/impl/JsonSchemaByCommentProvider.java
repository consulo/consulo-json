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
package com.jetbrains.jsonSchema.impl;

import consulo.application.util.BombedStringUtil;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonSchemaByCommentProvider {
    private static final List<SchemaComment> availableComments = List.of(
        new SchemaComment(
            "yaml-language-server: $schema=",
            Pattern.compile("#\\s*yaml-language-server:\\s*\\$schema=(?<id>\\S+).*", Pattern.CASE_INSENSITIVE),
            false
        ),
        new SchemaComment(
            "$schema: ",
            Pattern.compile("#\\s*\\$schema:\\s*(?<id>\\S+).*", Pattern.CASE_INSENSITIVE),
            true
        )
    );

    public static final List<String> schemaCommentsForCompletion = availableComments.stream()
        .filter(comment -> comment.forCompletion)
        .map(comment -> comment.commentText)
        .toList();

    private JsonSchemaByCommentProvider() {
    }

    public static @Nullable String getCommentSchema(VirtualFile file, Project project) {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        if (psiFile == null) {
            return null;
        }
        return getCommentSchema(psiFile);
    }

    public static @Nullable String getCommentSchema(PsiFile psiFile) {
        for (PsiComment comment : PsiTreeUtil.findChildrenOfType(psiFile, PsiComment.class)) {
            ASTNode node = comment.getNode();
            if (node == null) {
                continue;
            }
            CharSequence chars = BombedStringUtil.newBombedCharSequence(node.getChars(), 300);
            TextRange range = detectInComment(chars);
            if (range != null) {
                return range.subSequence(chars).toString();
            }
        }
        return null;
    }

    public static @Nullable TextRange detectInComment(CharSequence chars) {
        for (SchemaComment schemaComment : availableComments) {
            TextRange range = schemaComment.detect(chars);
            if (range != null) {
                return range;
            }
        }
        return null;
    }

    private static final class SchemaComment {
        private final String commentText;
        private final Pattern detectPattern;
        private final boolean forCompletion;

        private SchemaComment(String commentText, Pattern detectPattern, boolean forCompletion) {
            this.commentText = commentText;
            this.detectPattern = detectPattern;
            this.forCompletion = forCompletion;
        }

        private @Nullable TextRange detect(CharSequence chars) {
            Matcher matcher = detectPattern.matcher(chars);
            if (!matcher.matches()) {
                return null;
            }
            return TextRange.create(matcher.start("id"), matcher.end("id"));
        }
    }
}
