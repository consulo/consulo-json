// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.extension.JsonSchemaEnabler;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectStorage;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.json.icon.JsonIconGroup;
import consulo.language.icon.IconDescriptor;
import consulo.language.icon.IconDescriptorUpdater;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Inject;

@ExtensionImpl
public final class JsonSchemaIconProvider implements IconDescriptorUpdater {
    private final Project myProject;
    private final Application myApplication;

    @Inject
    public JsonSchemaIconProvider(Project project, Application application) {
        myProject = project;
        myApplication = application;
    }

    @RequiredReadAction
    @Override
    public void updateIcon(IconDescriptor iconDescriptor, PsiElement element, int flags) {
        if (!(element instanceof PsiFile psiFile)) {
            return;
        }
        VirtualFile file = psiFile.getVirtualFile();
        if (file == null) {
            return;
        }
        if (myApplication.getExtensionPoint(JsonSchemaEnabler.class).anyMatchSafe(e -> e.canBeSchemaFile(file))) {
            JsonSchemaService service = JsonSchemaService.get(myProject);
            if (service.isApplicableToFile(file) && JsonSchemaObjectStorage.getInstance(myProject).getComputedSchemaRootOrNull(file) != null) {
                iconDescriptor.setMainIcon(JsonIconGroup.jsonschema());
            }
        }
    }
}
