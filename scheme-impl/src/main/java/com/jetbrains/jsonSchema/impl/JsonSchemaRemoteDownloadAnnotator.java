// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.jetbrains.jsonSchema.internal.JsonFileResolver;
import com.jetbrains.jsonSchema.remote.JsonSchemaRemoteContentService;
import com.jetbrains.jsonSchema.remote.RemoteDownloadFailure;
import consulo.application.dumb.DumbAware;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.json.localize.JsonLocalize;
import consulo.language.editor.annotation.AnnotationHolder;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;

public class JsonSchemaRemoteDownloadAnnotator implements Annotator, DumbAware {
    @Override
    public void annotate(PsiElement element, AnnotationHolder holder) {
        TextRange range;
        String url;
        if (element instanceof JsonStringLiteral literal
            && literal.getParent() instanceof JsonProperty property
            && "$schema".equals(property.getName())) {
            range = literal.getTextRange();
            url = JsonFileResolver.replaceUnsafeSchemaStoreUrls(literal.getValue());
        }
        else if (element instanceof PsiComment) {
            String text = element.getText();
            TextRange r = JsonSchemaByCommentProvider.detectInComment(text);
            if (r == null) {
                return;
            }
            range = element.getTextRange().cutOut(r);
            url = r.substring(text);
        }
        else {
            return;
        }
        if (url == null) {
            return;
        }
        if (!JsonFileResolver.isHttpPath(url)) {
            return;
        }
        JsonSchemaRemoteContentService remote = JsonSchemaRemoteContentService.getInstance(element.getProject());
        if (!remote.isAllowed()) {
            return;
        }
        if (remote.hasInFlight(url)) {
            return;
        }
        if (remote.peekCachedFile(url) != null) {
            return;
        }
        if (remote.isUnavailable(url)) {
            holder.newAnnotation(HighlightSeverity.WARNING, JsonLocalize.jsonSchemaRemoteDownloadUnavailable(url))
                .range(range)
                .withFix(new RetrySchemaDownloadFix(url))
                .create();
            return;
        }
        if (remote.lastFailure(url) == RemoteDownloadFailure.Rejected) {
            holder.newAnnotation(HighlightSeverity.WARNING, JsonLocalize.jsonSchemaRemoteDownloadUnavailable(url))
                .range(range)
                .create();
            return;
        }
        if (remote.lastFailure(url) == RemoteDownloadFailure.Failed) {
            holder.newAnnotation(HighlightSeverity.WEAK_WARNING, JsonLocalize.jsonSchemaRemoteDownloadFailed())
                .range(range)
                .withFix(new RetrySchemaDownloadFix(url))
                .create();
        }
    }

    private static final class RetrySchemaDownloadFix implements IntentionAction {
        private final String myUrl;

        private RetrySchemaDownloadFix(String url) {
            myUrl = url;
        }

        @Override
        public LocalizeValue getText() {
            return JsonLocalize.jsonSchemaRemoteDownloadRetry();
        }

        @Override
        public boolean isAvailable(Project project, Editor editor, PsiFile psiFile) {
            JsonSchemaRemoteContentService remote = JsonSchemaRemoteContentService.getInstance(project);
            return remote.lastFailure(myUrl) == RemoteDownloadFailure.Failed || remote.isUnavailable(myUrl);
        }

        @Override
        public void invoke(Project project, Editor editor, PsiFile psiFile) {
            JsonSchemaRemoteContentService.getInstance(project).retryDownload(myUrl);
        }

        @Override
        public boolean startInWriteAction() {
            return false;
        }
    }
}
