// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.fixes;

import consulo.json.localize.JsonLocalize;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;

public class FixPropertyNameTypoFix extends PsiUpdateModCommandQuickFix {
  private final String myAltName;
  private final JsonLikeSyntaxAdapter myQuickFixAdapter;

  public FixPropertyNameTypoFix(String altName,
                                     JsonLikeSyntaxAdapter quickFixAdapter) {
    myAltName = altName;
    myQuickFixAdapter = quickFixAdapter;
  }

  @Override
  public @Nls(capitalization = Nls.Capitalization.Sentence) @Nonnull String getFamilyName() {
    return JsonLocalize.fixPropertyNameSpelling(myAltName).get();
  }

  @Override
  protected void applyFix(@Nonnull Project project, @Nonnull PsiElement element, @Nonnull ModPsiUpdater updater) {
    JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element);
    if (walker == null) return;
    JsonPropertyAdapter parentProperty = walker.getParentPropertyAdapter(element);
    if (parentProperty == null) return;
    PsiElement newProperty = walker.getSyntaxAdapter(project).createProperty(myAltName, "foo", project);
    parentProperty.getNameValueAdapter().getDelegate().replace(
      walker.getParentPropertyAdapter(newProperty).getNameValueAdapter().getDelegate()
    );
  }
}
