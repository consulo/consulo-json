// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.editor;

import com.intellij.json.JsonUtil;
import com.intellij.json.internal.navigation.JsonQualifiedNameKind;
import com.intellij.json.internal.navigation.JsonQualifiedNameProvider;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.dataContext.DataContext;
import consulo.document.FileDocumentManager;
import consulo.execution.action.ConfigurationContext;
import consulo.ide.action.CopyReferenceActionBase;
import consulo.json.localize.JsonLocalize;
import consulo.language.psi.PsiElement;
import consulo.ui.ex.action.AnActionEvent;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.util.Collections;
import java.util.List;

public final class JsonCopyPointerAction extends CopyReferenceActionBase {
    public JsonCopyPointerAction() {
        super(JsonLocalize.actionJsoncopypointerText(), JsonLocalize.actionJsoncopypointerText(), null);
    }

    @Override
    public void update(@Nonnull AnActionEvent e) {
        super.update(e);
        e.getPresentation().setText(JsonLocalize.actionJsoncopypointerText().get());
        DataContext dataContext = e.getDataContext();
        Editor editor = dataContext.getData(Editor.KEY);
        VirtualFile file = editor == null ? null : FileDocumentManager.getInstance().getFile(editor.getDocument());
        e.getPresentation().setVisible(file != null && JsonUtil.isJsonFile(file, editor.getProject()));
    }

    @Override
    protected String getQualifiedName(Editor editor, List<? extends PsiElement> elements) {
        if (elements.size() != 1) {
            return null;
        }
        return JsonQualifiedNameProvider.generateQualifiedName(elements.get(0), JsonQualifiedNameKind.JsonPointer);
    }

    @RequiredReadAction
    @Override
    protected @Nonnull List<PsiElement> getPsiElements(DataContext dataContext, Editor editor) {
        List<PsiElement> elements = super.getPsiElements(dataContext, editor);
        if (!elements.isEmpty()) {
            return elements;
        }
        PsiElement location = ConfigurationContext.getFromContext(dataContext).getPsiLocation();
        if (location == null) {
            return elements;
        }
        PsiElement parent = location.getParent();
        return parent != null ? Collections.singletonList(parent) : elements;
    }
}
