// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.breadcrumbs;

import com.intellij.json.JsonBundle;
import com.intellij.json.JsonLanguage;
import com.intellij.json.JsonUtil;
import com.intellij.json.impl.navigation.JsonQualifiedNameKind;
import com.intellij.json.impl.navigation.JsonQualifiedNameProvider;
import com.intellij.json.psi.JsonProperty;
import com.intellij.lang.Language;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider;
import com.jetbrains.jsonSchema.impl.JsonSchemaDocumentationProvider;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Mikhail Golubev
 */
public final class JsonBreadcrumbsProvider implements BreadcrumbsProvider {
  private static final Language[] LANGUAGES = new Language[]{JsonLanguage.INSTANCE};

  @Override
  public Language[] getLanguages() {
    return LANGUAGES;
  }

  @Override
  public boolean acceptElement(@Nonnull PsiElement e) {
    return e instanceof JsonProperty || JsonUtil.isArrayElement(e);
  }

  @Override
  public @Nonnull String getElementInfo(@Nonnull PsiElement e) {
    if (e instanceof JsonProperty) {
      return ((JsonProperty)e).getName();
    }
    else if (JsonUtil.isArrayElement(e)) {
      int i = JsonUtil.getArrayIndexOfItem(e);
      if (i != -1) return String.valueOf(i);
    }
    throw new AssertionError("Breadcrumbs can be extracted only from JsonProperty elements or JsonArray child items");
  }

  @Override
  public @Nullable String getElementTooltip(@Nonnull PsiElement e) {
    return JsonSchemaDocumentationProvider.findSchemaAndGenerateDoc(e, null, true, null);
  }

  @Override
  public @Nonnull List<? extends Action> getContextActions(@Nonnull PsiElement element) {
    JsonQualifiedNameKind[] values = JsonQualifiedNameKind.values();
    List<Action> actions = new ArrayList<>(values.length);
    for (JsonQualifiedNameKind kind: values) {
      actions.add(new AbstractAction(JsonBundle.message("json.copy.to.clipboard", kind.toString())) {
        @Override
        public void actionPerformed(ActionEvent e) {
          CopyPasteManager.getInstance().setContents(new StringSelection(JsonQualifiedNameProvider.generateQualifiedName(element, kind)));
        }
      });
    }
    return actions;
  }

  @Override
  public boolean isShownByDefault() {
    return false;
  }
}
