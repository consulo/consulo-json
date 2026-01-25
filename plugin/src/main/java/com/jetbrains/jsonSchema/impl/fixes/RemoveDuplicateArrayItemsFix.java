// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.fixes;

import com.intellij.json.JsonBundle;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.project.Project;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;

import jakarta.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class RemoveDuplicateArrayItemsFix implements LocalQuickFix {
  private final int[] indices;

  public RemoveDuplicateArrayItemsFix(int[] indices) {
    this.indices = indices;
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return JsonBundle.message("remove.duplicated.items");
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element, null);
    if (walker == null) {
      return;
    }

    JsonArrayValueAdapter parentArray = findParentArray(element, walker);
    if (parentArray == null) {
      return;
    }

    List<PsiElement> elementsToDelete = new ArrayList<>();
    List<JsonValueAdapter> elements = parentArray.getElements();
    for (int i = 0; i < elements.size(); i++) {
      if (contains(indices, i)) {
        JsonValueAdapter adapter = elements.get(i);
        PsiElement delegate = adapter.getDelegate();
        if (delegate != null) {
          elementsToDelete.add(delegate);
        }
      }
    }

    JsonLikeSyntaxAdapter syntaxAdapter = walker.getSyntaxAdapter(project);
    if (syntaxAdapter == null) {
      return;
    }

    for (PsiElement it : elementsToDelete) {
      if (!it.getTextRange().intersects(element.getTextRange())) {
        syntaxAdapter.removeArrayItem(it);
      }
    }
  }

  @jakarta.annotation.Nullable
  private JsonArrayValueAdapter findParentArray(@Nonnull PsiElement element, @Nonnull JsonLikePsiWalker walker) {
    PsiElement current = element;
    while (current != null) {
      JsonValueAdapter adapter = walker.createValueAdapter(current);
      if (adapter != null) {
        JsonArrayValueAdapter asArray = adapter.getAsArray();
        if (asArray != null) {
          return asArray;
        }
      }
      current = current.getParent();
    }
    return null;
  }

  private boolean contains(int[] array, int value) {
    for (int i : array) {
      if (i == value) {
        return true;
      }
    }
    return false;
  }
}
