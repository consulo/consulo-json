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
package com.jetbrains.jsonSchema.internal;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectStorage;
import consulo.json.localize.JsonLocalize;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.virtualFileSystem.RawFileLoader;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

public final class JsonSchemaReader {
    public static JsonSchemaObject readFromFile(Project project, VirtualFile file) throws Exception {
        if (!file.isValid()) {
            throw new Exception(JsonLocalize.schemaReaderCantLoadFile(file.getName()).get());
        }

        PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
        JsonSchemaObject object = psiFile == null ? null : new JsonSchemaReader().read(psiFile);
        if (object == null) {
            throw new Exception(JsonLocalize.schemaReaderCantLoadModel(file.getName()).get());
        }
        return object;
    }

    public static LocalizeValue checkIfValidJsonSchema(Project project, VirtualFile file) {
        long length = file.getLength();
        String fileName = file.getName();
        if (length > RawFileLoader.getInstance().getUserContentLoadLimit()) {
            return JsonLocalize.schemaReaderFileTooLarge(fileName, length);
        }
        if (length == 0) {
            return JsonLocalize.schemaReaderFileEmpty(fileName);
        }
        try {
            readFromFile(project, file);
        }
        catch (Exception e) {
            LocalizeValue message = JsonLocalize.schemaReaderFileNotFoundOrError(fileName, String.valueOf(e.getMessage()));
            Logger.getInstance(JsonSchemaReader.class).info("JSON Schema not found or contains error in '" + fileName + "': " + e.getMessage());
            return message;
        }
        return LocalizeValue.empty();
    }

    public static @Nullable JsonSchemaObject getOrComputeSchemaObjectForSchemaFile(VirtualFile schemaFile, Project project) {
        return JsonSchemaObjectStorage.getInstance(project).getOrComputeSchemaRootObject(schemaFile);
    }

    public @Nullable JsonSchemaObject read(PsiFile file) {
        VirtualFile virtualFile = file.getOriginalFile().getVirtualFile();
        return virtualFile == null ? null : getOrComputeSchemaObjectForSchemaFile(virtualFile, file.getProject());
    }

    public static String getNewPointer(String name, String oldPointer) {
        return oldPointer.equals("/") ? oldPointer + name : oldPointer + "/" + name;
    }

    public static @Nullable JsonSchemaType parseType(String typeString) {
        try {
            return JsonSchemaType.valueOf("_" + typeString);
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    }
}
