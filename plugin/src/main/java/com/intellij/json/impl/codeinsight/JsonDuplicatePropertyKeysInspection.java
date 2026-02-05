// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.codeinsight;

import consulo.json.localize.JsonLocalize;
import com.intellij.json.psi.JsonElementVisitor;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.jetbrains.jsonSchema.JsonSchemaService;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.*;
import consulo.project.Project;
import consulo.ui.ex.popup.BaseListPopupStep;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.PopupStep;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;
import jakarta.annotation.Nullable;
import sun.tools.jconsole.inspector.IconManager;

import javax.swing.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class JsonDuplicatePropertyKeysInspection extends LocalInspectionTool {
  private static final String COMMENT = "$comment";

  @Override
  public @Nonnull PsiElementVisitor buildVisitor(final @Nonnull ProblemsHolder holder, boolean isOnTheFly) {
    boolean isSchemaFile = JsonSchemaService.isSchemaFile(holder.getFile());
    return new JsonElementVisitor() {
      @Override
      public void visitObject(@Nonnull JsonObject o) {
        final MultiMap<String, PsiElement> keys = new MultiMap<>();
        for (JsonProperty property : o.getPropertyList()) {
          keys.putValue(property.getName(), property.getNameElement());
        }
        visitKeys(keys, isSchemaFile, holder);
      }
    };
  }

  protected static void visitKeys(MultiMap<String, PsiElement> keys, boolean isSchemaFile, @Nonnull ProblemsHolder holder) {
    for (Map.Entry<String, Collection<PsiElement>> entry : keys.entrySet()) {
      final Collection<PsiElement> sameNamedKeys = entry.getValue();
      final String entryKey = entry.getKey();
      if (sameNamedKeys.size() > 1 && (!isSchemaFile || !COMMENT.equalsIgnoreCase(entryKey))) {
        for (PsiElement element : sameNamedKeys) {
          holder.registerProblem(element, JsonBundle.message("inspection.duplicate.keys.msg.duplicate.keys", entryKey),
                                 getNavigateToDuplicatesFix(sameNamedKeys, element, entryKey));
        }
      }
    }
  }

  protected static @Nonnull NavigateToDuplicatesFix getNavigateToDuplicatesFix(Collection<PsiElement> sameNamedKeys,
                                                                               PsiElement element,
                                                                               String entryKey) {
    return new NavigateToDuplicatesFix(sameNamedKeys, element, entryKey);
  }

  private static final class NavigateToDuplicatesFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    private final @Nonnull Collection<SmartPsiElementPointer<PsiElement>> mySameNamedKeys;
    private final @Nonnull String myEntryKey;

    private NavigateToDuplicatesFix(@Nonnull Collection<PsiElement> sameNamedKeys, @Nonnull PsiElement element, @Nonnull String entryKey) {
      super(element);
      mySameNamedKeys = ContainerUtil.map(sameNamedKeys, k -> SmartPointerManager.createPointer(k));
      myEntryKey = entryKey;
    }

    @Override
    public @Nonnull String getText() {
      return JsonBundle.message("navigate.to.duplicates");
    }

    @Override
    public @Nls(capitalization = Nls.Capitalization.Sentence) @Nonnull String getFamilyName() {
      return getText();
    }

    @Override
    public @Nonnull IntentionPreviewInfo generatePreview(@Nonnull Project project, @Nonnull ProblemDescriptor previewDescriptor) {
      return IntentionPreviewInfo.EMPTY;
    }

    @Override
    public @Nonnull IntentionPreviewInfo generatePreview(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiFile psiFile) {
      return IntentionPreviewInfo.EMPTY;
    }

    @Override
    public void invoke(@Nonnull Project project,
                       @Nonnull PsiFile psiFile,
                       @Nullable Editor editor,
                       @Nonnull PsiElement startElement,
                       @Nonnull PsiElement endElement) {
      if (editor == null) return;

      if (mySameNamedKeys.size() == 2) {
        final Iterator<SmartPsiElementPointer<PsiElement>> iterator = mySameNamedKeys.iterator();
        final PsiElement next = iterator.next().getElement();
        PsiElement toNavigate = next != startElement ? next : iterator.next().getElement();
        if (toNavigate == null) return;
        navigateTo(editor, toNavigate);
      }
      else {
        final List<PsiElement> allElements =
          mySameNamedKeys.stream().map(k -> k.getElement()).filter(k -> k != startElement).collect(Collectors.toList());
        JBPopupFactory.getInstance().createListPopup(
          new BaseListPopupStep<>(JsonBundle.message("navigate.to.duplicates.header", myEntryKey), allElements) {
            @Override
            public @Nonnull Icon getIconFor(PsiElement aValue) {
              return IconManager.getInstance().getPlatformIcon(PlatformIcons.Property);
            }

            @Override
            public @Nonnull String getTextFor(PsiElement value) {
              return JsonBundle
                .message("navigate.to.duplicates.desc", myEntryKey, editor.getDocument().getLineNumber(value.getTextOffset()));
            }

            @Override
            public int getDefaultOptionIndex() {
              return 0;
            }

            @Override
            public @Nullable PopupStep<?> onChosen(PsiElement selectedValue, boolean finalChoice) {
              navigateTo(editor, selectedValue);
              return PopupStep.FINAL_CHOICE;
            }

            @Override
            public boolean isSpeedSearchEnabled() {
              return true;
            }
          }).showInBestPositionFor(editor);
      }
    }

    private static void navigateTo(@Nonnull Editor editor, @Nonnull PsiElement toNavigate) {
      editor.getCaretModel().moveToOffset(toNavigate.getTextOffset());
      editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
  }
}
