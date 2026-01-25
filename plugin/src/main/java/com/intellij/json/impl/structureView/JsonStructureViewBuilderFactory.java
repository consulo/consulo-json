// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.structureView;

import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.fileEditor.structureView.StructureViewBuilder;
import consulo.fileEditor.structureView.StructureViewModel;
import consulo.fileEditor.structureView.TreeBasedStructureViewBuilder;
import consulo.language.Language;
import consulo.language.editor.structureView.PsiStructureViewFactory;
import consulo.language.psi.PsiFile;
import consulo.logging.Logger;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
public final class JsonStructureViewBuilderFactory implements PsiStructureViewFactory {

  public JsonStructureViewBuilderFactory() {
  }

  @Override
  public @Nullable StructureViewBuilder getStructureViewBuilder(final @NotNull PsiFile psiFile) {
    if (!(psiFile instanceof JsonFile)) {
      return null;
    }

    List<JsonCustomStructureViewFactory> extensionList = JsonCustomStructureViewFactory.EP_NAME.getExtensionList();
    if (extensionList.size() > 1) {
      Logger.getInstance(JsonStructureViewBuilderFactory.class)
        .warn("Several extensions are registered for JsonCustomStructureViewFactory extension point. " +
              "Conflicts can arise if there are several builders corresponding to the same file.");
    }

    for (JsonCustomStructureViewFactory extension : extensionList) {
      final StructureViewBuilder builder = extension.getStructureViewBuilder((JsonFile)psiFile);
      if (builder != null) {
        return builder;
      }
    }

    return new TreeBasedStructureViewBuilder() {
      @Override
      public @NotNull StructureViewModel createStructureViewModel(@Nullable Editor editor) {
        return new JsonStructureViewModel(psiFile, editor);
      }
    };
  }

    @Nonnull
    @Override
    public Language getLanguage() {
        return JsonLanguage.INSTANCE;
    }
}
