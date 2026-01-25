// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.fixes;

import com.intellij.json.JsonBundle;
import com.intellij.json.psi.JsonObject;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.lang.ref.Ref;
import com.jetbrains.jsonSchema.extension.JsonLikeSyntaxAdapter;
import com.jetbrains.jsonSchema.extension.JsonSchemaQuickFixSuppressor;
import com.jetbrains.jsonSchema.impl.JsonCachedValues;
import com.jetbrains.jsonSchema.impl.JsonOriginalPsiWalker;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

@IntentionMetaData(
  ignoreId = "json.AddOptionalPropertiesIntention",
  categories = {"JSON", "JSON Schema"},
  fileExtensions = "json"
)
public class AddOptionalPropertiesIntention extends PsiElementBaseIntentionAction {
  @Nonnull
  @Override
  public String getFamilyName() {
    return JsonBundle.message("intention.add.not.required.properties.family.name");
  }

  @Nonnull
  @Override
  public String getText() {
    return JsonBundle.message("intention.add.not.required.properties.text");
  }

  @Override
  @RequiredReadAction
  public boolean isAvailable(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) {
    JsonObject obj = findContainingObjectNode(element, editor);
    if (obj == null) {
      return false;
    }

    if (JsonSchemaQuickFixSuppressor.EXTENSION_POINT_NAME.getExtensionList().stream()
      .anyMatch(it -> it.shouldSuppressFix(element.getContainingFile(), AddOptionalPropertiesIntention.class))) {
      return false;
    }

    if (!JsonCachedValues.hasComputedSchemaObjectForFile(obj.getContainingFile())) {
      return false;
    }

    SmartPsiElementPointer<JsonObject> pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(obj);
    MissingPropertiesResult missing = AddMissingPropertyFix.collectMissingPropertiesFromSchema(pointer, project);
    return missing != null && missing.getMissingKnownProperties() != null
      && !missing.getMissingKnownProperties().myMissingPropertyIssues.isEmpty();
  }

  @Override
  @RequiredReadAction
  public void invoke(@Nonnull Project project, Editor editor, @Nonnull PsiElement element) throws IncorrectOperationException {
    JsonObject objCopy = findContainingObjectNode(element, editor);
    if (objCopy == null) {
      return;
    }

    JsonObject physObj = findPhysicalObjectNode(element, editor);
    if (physObj == null) {
      return;
    }

    SmartPsiElementPointer<JsonObject> pointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(physObj);
    MissingPropertiesResult missing = AddMissingPropertyFix.collectMissingPropertiesFromSchema(pointer, project);
    if (missing == null || missing.getMissingKnownProperties() == null) {
      return;
    }

    new AddMissingPropertyFix(missing.getMissingKnownProperties(), getSyntaxAdapter(project))
      .performFixInner(objCopy, Ref.create());

    consulo.language.codeStyle.CodeStyleManager.getInstance(project)
      .reformatText(objCopy.getContainingFile(), java.util.Collections.singleton(objCopy.getTextRange()));
  }

  @Nonnull
  protected JsonLikeSyntaxAdapter getSyntaxAdapter(@Nonnull Project project) {
    return JsonOriginalPsiWalker.INSTANCE.getSyntaxAdapter(project);
  }

  @Nullable
  protected JsonObject findPhysicalObjectNode(@Nonnull PsiElement element, @Nullable Editor editor) {
    if (editor == null) {
      return null;
    }
    PsiFile file = element.getContainingFile();
    if (file == null) {
      return null;
    }
    PsiElement physLeaf = file.findElementAt(editor.getCaretModel().getOffset());
    if (physLeaf == null) {
      return null;
    }
    return PsiTreeUtil.getParentOfType(physLeaf, JsonObject.class);
  }

  @Nullable
  protected JsonObject findContainingObjectNode(@Nonnull PsiElement element, @Nullable Editor editor) {
    JsonObject fromElement = PsiTreeUtil.getParentOfType(element, JsonObject.class);
    if (fromElement != null) {
      return fromElement;
    }

    if (editor != null) {
      PsiFile file = element.getContainingFile();
      if (file != null) {
        PsiElement leaf = file.findElementAt(editor.getCaretModel().getOffset());
        if (leaf != null) {
          return PsiTreeUtil.getParentOfType(leaf, JsonObject.class);
        }
      }
    }

    return null;
  }
}
