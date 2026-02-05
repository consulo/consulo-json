// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import consulo.json.localize.JsonLocalize;
import com.intellij.json.JsonFileType;
import com.intellij.json.JsonUtil;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressManager;
import consulo.fileEditor.FileEditorManager;
import consulo.json.localize.JsonLocalize;
import consulo.language.editor.CommonDataKeys;
import consulo.language.editor.WriteCommandAction;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.navigation.OpenFileDescriptor;
import consulo.project.Project;
import consulo.ui.ex.action.ActionUpdateThread;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.awt.MessageDialogBuilder;
import consulo.virtualFileSystem.LargeFileWriteRequestor;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.io.IOException;
import java.io.OutputStream;

class JsonJacksonReformatAction extends AnAction implements LargeFileWriteRequestor {

    @Override
    public void update(@Nonnull AnActionEvent e) {
        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        e.getPresentation().setEnabledAndVisible(
            ApplicationManager.getApplication().isInternal()
                && virtualFile != null
                && JsonUtil.isJsonFile(virtualFile, e.getData(Project.KEY))
        );
    }

    @Nonnull
    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@Nonnull AnActionEvent e) {
        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (virtualFile == null) {
            return;
        }

        Project project = e.getProject();
        if (project == null) {
            return;
        }

        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            try {
                // Read and format JSON
                ObjectMapper objectMapper = new ObjectMapper();
                String formatted;
                try {
                    var parsed = objectMapper.readTree(virtualFile.getInputStream());
                    formatted = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
                }
                catch (IOException ex) {
                    throw new RuntimeException(ex);
                }

                // Try to update document if writable
                ApplicationManager.getApplication().runReadAction(() -> {
                    PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                    if (psiFile != null) {
                        var doc = PsiDocumentManager.getInstance(project).getDocument(psiFile);
                        if (doc != null && doc.isWritable()) {
                            ApplicationManager.getApplication().invokeLater(() -> {
                                WriteCommandAction.runWriteCommandAction(project,
                                    JsonLocalize.jsonjacksonreformatactionCommandNameJsonReformat().get(),
                                    null,
                                    () -> doc.setText(formatted)
                                );
                            });
                            return;
                        }
                    }
                });

                // If file is not writable, open formatted version in new file
                if (!virtualFile.isWritable()) {
                    LightVirtualFile file = new LightVirtualFile(
                        virtualFile.getNameWithoutExtension() + "-formatted.json",
                        JsonFileType.INSTANCE,
                        formatted
                    );
                    OpenFileDescriptor descriptor = new OpenFileDescriptor(project, file);
                    ApplicationManager.getApplication().invokeLater(() -> {
                        FileEditorManager.getInstance(project).openEditor(descriptor, true);
                    });
                    return;
                }

                // Ask user confirmation for non-undoable action
                boolean[] result = new boolean[1];
                ApplicationManager.getApplication().invokeAndWait(() -> {
                    MessageDialogBuilder dialogBuilder = MessageDialogBuilder.okCancel(
                        JsonLocalize.jsonjacksonreformatactionDialogTitleJsonReformatting().get(),
                        JsonLocalize.jsonjacksonreformatactionDialogMessageThisActionNotUndoableDoYouWantToReformatDocument().get()
                    );
                    result[0] = dialogBuilder.ask(project);
                });

                if (!result[0]) {
                    return;
                }

                // Write to file
                ApplicationManager.getApplication().invokeLater(() -> {
                    WriteCommandAction.runWriteCommandAction(project,
                        JsonLocalize.jsonjacksonreformatactionCommandNameJsonReformat().get(),
                        null,
                        () -> {
                            try (OutputStream stream = virtualFile.getOutputStream(JsonJacksonReformatAction.this)) {
                                stream.write(formatted.getBytes(virtualFile.getCharset()));
                            }
                            catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    );
                });

            }
            catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }, JsonLocalize.jsonjacksonreformatactionProgressTitleJsonReformatting().get(), true, project);
    }
}
