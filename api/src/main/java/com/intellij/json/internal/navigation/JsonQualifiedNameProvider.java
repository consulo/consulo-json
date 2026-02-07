// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.internal.navigation;

import com.intellij.json.JsonUtil;
import com.intellij.json.pointer.JsonPointerUtil;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonElement;
import com.intellij.json.psi.JsonProperty;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.QualifiedNameProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
public final class JsonQualifiedNameProvider implements QualifiedNameProvider {
  @Override
  public @Nullable PsiElement adjustElementToCopy(@Nonnull PsiElement element) {
    return null;
  }

  @Override
  public @Nullable String getQualifiedName(@Nonnull PsiElement element) {
    return generateQualifiedName(element, JsonQualifiedNameKind.Qualified);
  }

  public static String generateQualifiedName(PsiElement element, JsonQualifiedNameKind qualifiedNameKind) {
    if (!(element instanceof JsonElement)) {
      return null;
    }
    JsonElement parentProperty = PsiTreeUtil.getNonStrictParentOfType(element, JsonProperty.class, JsonArray.class);
    StringBuilder builder = new StringBuilder();
    while (parentProperty != null) {
      if (parentProperty instanceof JsonProperty jsonProperty) {
        String name = jsonProperty.getName();
        if (qualifiedNameKind == JsonQualifiedNameKind.JsonPointer) {
          name = JsonPointerUtil.escapeForJsonPointer(name);
        }
        builder.insert(0, name);
        builder.insert(0, qualifiedNameKind == JsonQualifiedNameKind.JsonPointer ? "/" : ".");
      }
      else {
        int index = JsonUtil.getArrayIndexOfItem(element instanceof JsonProperty ? element.getParent() : element);
        if (index == -1) return null;
        builder.insert(0, qualifiedNameKind == JsonQualifiedNameKind.JsonPointer ? ("/" + index) : ("[" + index + "]"));
      }
      element = parentProperty;
      parentProperty = PsiTreeUtil.getParentOfType(parentProperty, JsonProperty.class, JsonArray.class);
    }

    if (builder.isEmpty()) return null;

    // if the first operation is array indexing, we insert the 'root' element $
    if (builder.charAt(0) == '[') {
      builder.insert(0, "$");
    }

    return StringUtil.trimStart(builder.toString(), ".");
  }

  @Override
  public PsiElement qualifiedNameToElement(@Nonnull String fqn, @Nonnull Project project) {
    return null;
  }
}
