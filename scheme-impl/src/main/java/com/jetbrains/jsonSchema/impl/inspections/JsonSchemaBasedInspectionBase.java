// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonOriginalPsiWalker;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

public abstract class JsonSchemaBasedInspectionBase extends LocalInspectionTool {

  @Override
  public @Nonnull PsiElementVisitor buildVisitor(final @Nonnull ProblemsHolder holder, boolean isOnTheFly, @Nonnull LocalInspectionToolSession session) {
    PsiFile file = holder.getFile();
    Collection<PsiElement> allRoots = JsonOriginalPsiWalker.INSTANCE.getRoots(file);
    // JSON may have only a single root element
    JsonValue root = allRoots.size() == 1 ? ObjectUtils.tryCast(ContainerUtil.getFirstItem(allRoots), JsonValue.class) : null;
    if (root == null) return PsiElementVisitor.EMPTY_VISITOR;

    JsonSchemaService service = JsonSchemaService.Impl.get(file.getProject());
    VirtualFile virtualFile = file.getViewProvider().getVirtualFile();
    if (!service.isApplicableToFile(virtualFile)) return PsiElementVisitor.EMPTY_VISITOR;

    return doBuildVisitor(root, service.getSchemaObject(file), service, holder, session);
  }

  protected abstract PsiElementVisitor doBuildVisitor(@Nonnull JsonValue root,
                                                      @Nullable JsonSchemaObject schema,
                                                      @Nonnull JsonSchemaService service,
                                                      @Nonnull ProblemsHolder holder,
                                                      @Nonnull LocalInspectionToolSession session);
}
