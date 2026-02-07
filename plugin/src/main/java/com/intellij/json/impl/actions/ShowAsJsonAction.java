// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.actions;

import com.google.common.base.CharMatcher;
import com.intellij.json.JsonLanguage;
import consulo.codeEditor.Editor;
import consulo.codeEditor.SelectionModel;
import consulo.document.Document;
import consulo.document.FileDocumentManager;
import consulo.execution.ui.console.ConsoleView;
import consulo.fileEditor.FileEditor;
import consulo.fileEditor.FileEditorManager;
import consulo.fileEditor.TextEditor;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.CommonDataKeys;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.project.Project;
import consulo.ui.ex.action.ActionUpdateThread;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.undoRedo.util.UndoUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SoftReference;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.lang.ref.WeakReference;

final class ShowAsJsonAction extends DumbAwareAction {
    private static final class Holder {
        private static final CharMatcher JSON_START_MATCHER = CharMatcher.is('{');
    }

    private static final Key<Integer> LINE_KEY = Key.create("jsonFileToLogLineNumber");
    private static final Key<WeakReference<Editor>> EDITOR_REF_KEY = Key.create("jsonFileToConsoleEditor");

    private static final class JsonLineExtractor {
        private int start = -1;
        private int end = -1;

        private Document document;

        private int line = -1;
        private int lineStart = -1;

        private JsonLineExtractor(@Nonnull Editor editor) {
            doCompute(editor);
        }

        public int getLine() {
            return line;
        }

        private void doCompute(@Nonnull Editor editor) {
            SelectionModel model = editor.getSelectionModel();
            document = editor.getDocument();
            if (!model.hasSelection()) {
                int offset = editor.getCaretModel().getOffset();
                if (offset <= document.getTextLength()) {
                    line = document.getLineNumber(offset);
                    lineStart = document.getLineStartOffset(line);
                    getJsonString(document, document.getLineEndOffset(line));
                }
                return;
            }

            lineStart = model.getSelectionStart();
            int end = model.getSelectionEnd();
            line = document.getLineNumber(lineStart);
            if (line == document.getLineNumber(end)) {
                getJsonString(document, end);
            }
        }

        private void getJsonString(Document document, int lineEnd) {
            CharSequence documentChars = document.getCharsSequence();
            int start = Holder.JSON_START_MATCHER.indexIn(documentChars, lineStart);
            if (start < 0) {
                return;
            }

            int end = -1;
            for (int i = lineEnd - 1; i > start; i--) {
                if (documentChars.charAt(i) == '}') {
                    end = i;
                    break;
                }
            }

            if (end == -1) {
                return;
            }

            this.start = start;
            this.end = end + 1;
        }

        public CharSequence get() {
            return document.getCharsSequence().subSequence(start, end + 1);
        }

        public boolean has() {
            return start != -1;
        }

        public String getPrefix() {
            CharSequence chars = document.getCharsSequence();
            int end = start;
            for (int i = start - 1; i > lineStart; i--) {
                char c = chars.charAt(i);
                if (c == ':' || Character.isWhitespace(c)) {
                    end--;
                }
                else {
                    break;
                }
            }
            return CharMatcher.whitespace().trimFrom(chars.subSequence(lineStart, end));
        }
    }

    @Override
    public @Nonnull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@Nonnull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        boolean enabled = editor != null && e.getData(ConsoleView.KEY) != null && new JsonLineExtractor(editor).has();
        e.getPresentation().setEnabledAndVisible(enabled);
    }

    @Override
    public void actionPerformed(@Nonnull AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        final Project project = e.getData(Project.KEY);
        JsonLineExtractor jsonLineExtractor = project == null || editor == null ? null : new JsonLineExtractor(editor);
        if (jsonLineExtractor == null || !jsonLineExtractor.has()) {
            return;
        }

        FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
        if (selectOpened(editor, jsonLineExtractor, fileEditorManager)) {
            return;
        }

        LightVirtualFile virtualFile = new LightVirtualFile(StringUtil.trimMiddle(jsonLineExtractor.getPrefix(), 50), JsonLanguage.INSTANCE, jsonLineExtractor.get());
        virtualFile.putUserData(LINE_KEY, jsonLineExtractor.getLine());
        virtualFile.putUserData(EDITOR_REF_KEY, new WeakReference<>(editor));

        final PsiFile file = PsiManager.getInstance(project).findFile(virtualFile);
        if (file == null) {
            return;
        }

        UndoUtil.writeInRunUndoTransparentAction(() -> CodeStyleManager.getInstance(project).reformat(file, true));

        virtualFile.setWritable(false);
        FileEditorManager.getInstance(project).openFile(virtualFile, true);
    }

    private static boolean selectOpened(Editor editor, JsonLineExtractor jsonLineExtractor, FileEditorManager fileEditorManager) {
        for (FileEditor fileEditor : fileEditorManager.getAllEditors()) {
            if (fileEditor instanceof TextEditor textEditor) {
                VirtualFile file = FileDocumentManager.getInstance().getFile(textEditor.getEditor().getDocument());
                if (file instanceof LightVirtualFile) {
                    Integer line = LINE_KEY.get(file);
                    if (line != null && line == jsonLineExtractor.getLine()) {
                        WeakReference<Editor> editorReference = EDITOR_REF_KEY.get(file);
                        if (SoftReference.dereference(editorReference) == editor) {
                            fileEditorManager.openFile(file, true, true);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}