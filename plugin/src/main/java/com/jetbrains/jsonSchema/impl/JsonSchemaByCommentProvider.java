// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiTreeUtil;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class JsonSchemaByCommentProvider {

  private static final List<SchemaComment> availableComments = Arrays.asList(
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
    .collect(Collectors.toList());

  @Nullable
  public static String getCommentSchema(VirtualFile file, Project project) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null) return null;
    return getCommentSchema(psiFile);
  }

  @Nullable
  public static String getCommentSchema(PsiFile psiFile) {
    Collection<PsiComment> comments = PsiTreeUtil.findChildrenOfType(psiFile, PsiComment.class);
    for (PsiComment comment : comments) {
      CharSequence chars = comment.getNode() != null ? comment.getNode().getChars() : null;
      if (chars != null) {
        chars = StringUtil.newBombedCharSequence(chars, 300);
        TextRange range = detectInComment(chars);
        if (range != null) {
          return range.subSequence(chars).toString();
        }
      }
    }
    return null;
  }

  @Nullable
  public static TextRange detectInComment(CharSequence chars) {
    for (SchemaComment schemaComment : availableComments) {
      TextRange range = schemaComment.detect(chars);
      if (range != null) {
        return range;
      }
    }
    return null;
  }

  private static class SchemaComment {
    final String commentText;
    final Pattern detectPattern;
    final boolean forCompletion;

    SchemaComment(String commentText, Pattern detectPattern, boolean forCompletion) {
      this.commentText = commentText;
      this.detectPattern = detectPattern;
      this.forCompletion = forCompletion;
    }

    @Nullable
    TextRange detect(CharSequence chars) {
      Matcher matcher = detectPattern.matcher(chars);
      if (!matcher.matches()) return null;
      return TextRange.create(matcher.start("id"), matcher.end("id"));
    }
  }
}
