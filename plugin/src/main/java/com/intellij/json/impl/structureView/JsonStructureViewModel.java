// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.structureView;

import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import consulo.codeEditor.Editor;
import consulo.fileEditor.structureView.StructureViewModel;
import consulo.fileEditor.structureView.StructureViewTreeElement;
import consulo.fileEditor.structureView.tree.Sorter;
import consulo.language.editor.structureView.StructureViewModelBase;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Mikhail Golubev
 */
public final class JsonStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider {

  public JsonStructureViewModel(@Nonnull PsiFile psiFile, @Nullable Editor editor) {
    super(psiFile, editor, new JsonStructureViewElement((JsonFile)psiFile));
    withSuitableClasses(JsonFile.class, JsonProperty.class, JsonObject.class, JsonArray.class);
    withSorters(Sorter.ALPHA_SORTER);
  }

  @Override
  public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
    return false;
  }

  @Override
  public boolean isAlwaysLeaf(StructureViewTreeElement element) {
    return false;
  }

}
