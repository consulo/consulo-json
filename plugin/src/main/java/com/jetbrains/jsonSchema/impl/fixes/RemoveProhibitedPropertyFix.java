// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.fixes;

import consulo.json.localize.JsonLocalize;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.JsonValidationError;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;

import java.util.Objects;

public final class RemoveProhibitedPropertyFix extends PsiUpdateModCommandQuickFix {
  private final JsonValidationError.ProhibitedPropertyIssueData myData;
  private final JsonLikeSyntaxAdapter myQuickFixAdapter;

  public RemoveProhibitedPropertyFix(JsonValidationError.ProhibitedPropertyIssueData data,
                                     JsonLikeSyntaxAdapter quickFixAdapter) {
    myData = data;
    myQuickFixAdapter = quickFixAdapter;
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @Nonnull String getFamilyName() {
    return JsonLocalize.removeProhibitedProperty().get();
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @Nonnull String getName() {
    return getFamilyName() + " '" + myData.propertyName + "'";
  }

  @Override
  protected void applyFix(@Nonnull Project project, @Nonnull PsiElement element, @Nonnull ModPsiUpdater updater) {
    JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element);
    if (walker == null) return;
    JsonPropertyAdapter parentProperty = walker.getParentPropertyAdapter(element);
    assert myData.propertyName.equals(Objects.requireNonNull(parentProperty).getName());
    myQuickFixAdapter.removeProperty(parentProperty.getDelegate());
  }
}
