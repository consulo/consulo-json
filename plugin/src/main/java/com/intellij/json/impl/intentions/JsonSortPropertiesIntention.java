// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.intentions;

import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.json.JsonBundle;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.impl.JsonRecursiveElementVisitor;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class JsonSortPropertiesIntention extends PsiUpdateModCommandAction<PsiFile> implements LightEditCompatible, DumbAware {

  public JsonSortPropertiesIntention() {
    super(PsiFile.class);
  }

  protected AbstractSortPropertiesSession<? extends PsiElement, ? extends PsiElement> createSession(ActionContext context, PsiFile file) {
    return new JsonSortSession(context, file);
  }

  @Nonnull
  @Override
  public String getFamilyName() {
    return JsonBundle.message("json.intention.sort.properties");
  }

  @Nullable
  @Override
  protected Presentation getPresentation(@Nonnull ActionContext context, @Nonnull PsiFile element) {
    AbstractSortPropertiesSession<? extends PsiElement, ? extends PsiElement> session = createSession(context, element);
    PsiElement root = session.getRootElement();
    if (root == null) return null;
    if (!session.hasUnsortedObjects()) return null;
    return Presentation.of(getFamilyName())
      .withPriority(PriorityAction.Priority.LOW)
      .withHighlighting(root.getTextRange());
  }

  @Override
  protected void invoke(@Nonnull ActionContext context, @Nonnull PsiFile element, @Nonnull ModPsiUpdater updater) {
    AbstractSortPropertiesSession<? extends PsiElement, ? extends PsiElement> session = createSession(context, element);
    PsiElement root = session.getRootElement();
    if (root == null) return;
    session.sort();
    CodeStyleManager.getInstance(context.getProject()).reformatText(element, Collections.singleton(root.getTextRange()));
  }

  private static class JsonSortSession extends AbstractSortPropertiesSession<JsonObject, JsonProperty> {

    public JsonSortSession(ActionContext context, PsiFile file) {
      super(context, file);
    }

    @Nullable
    @Override
    protected JsonObject findRootObject() {
      int offset = context.getOffset();
      JsonObject initObj = PsiTreeUtil.getParentOfType(file.findElementAt(offset), JsonObject.class);
      if (initObj == null) {
        if (file instanceof JsonFile) {
          JsonFile jsonFile = (JsonFile) file;
          initObj = jsonFile.getAllTopLevelValues().stream()
            .filter(v -> v instanceof JsonObject)
            .map(v -> (JsonObject) v)
            .findFirst()
            .orElse(null);
        }
      }
      return adjustToSelectionContainer(initObj);
    }

    @Override
    protected Set<JsonObject> collectObjects(JsonObject rootObj) {
      return collectIntersectingObjects(rootObj);
    }

    @Override
    protected List<JsonProperty> getProperties(JsonObject obj) {
      return obj.getPropertyList();
    }

    @Nullable
    @Override
    protected String getPropertyName(JsonProperty prop) {
      return prop.getName();
    }

    @Nullable
    @Override
    protected JsonObject getParentObject(JsonObject obj) {
      return PsiTreeUtil.getParentOfType(obj, JsonObject.class);
    }

    @Override
    protected void traverseObjects(JsonObject root, Consumer<JsonObject> visitor) {
      new JsonRecursiveElementVisitor() {
        @Override
        public void visitObject(@Nonnull JsonObject o) {
          super.visitObject(o);
          visitor.accept(o);
        }
      }.visitObject(root);
    }
  }
}
