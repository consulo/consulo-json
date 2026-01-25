// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.actions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.json.JsonBundle;
import com.intellij.json.JsonFileType;
import com.intellij.json.JsonUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.vfs.LargeFileWriteRequestor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.LightVirtualFile;
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
      && JsonUtil.isJsonFile(virtualFile, e.getProject())
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
    if (virtualFile == null) return;

    Project project = e.getProject();
    if (project == null) return;

    ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      try {
        // Read and format JSON
        ObjectMapper objectMapper = new ObjectMapper();
        String formatted;
        try {
          var parsed = objectMapper.readTree(virtualFile.getInputStream());
          formatted = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (IOException ex) {
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
                  JsonBundle.message("JsonJacksonReformatAction.command.name.json.reformat"),
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
            JsonBundle.message("JsonJacksonReformatAction.dialog.title.json.reformatting"),
            JsonBundle.message("JsonJacksonReformatAction.dialog.message.this.action.not.undoable.do.you.want.to.reformat.document")
          );
          result[0] = dialogBuilder.ask(project);
        });

        if (!result[0]) return;

        // Write to file
        ApplicationManager.getApplication().invokeLater(() -> {
          WriteCommandAction.runWriteCommandAction(project,
            JsonBundle.message("JsonJacksonReformatAction.command.name.json.reformat"),
            null,
            () -> {
              try (OutputStream stream = virtualFile.getOutputStream(JsonJacksonReformatAction.this)) {
                stream.write(formatted.getBytes(virtualFile.getCharset()));
              } catch (IOException ex) {
                throw new RuntimeException(ex);
              }
            }
          );
        });

      } catch (Exception ex) {
        throw new RuntimeException(ex);
      }
    }, JsonBundle.message("JsonJacksonReformatAction.progress.title.json.reformatting"), true, project);
  }
}
