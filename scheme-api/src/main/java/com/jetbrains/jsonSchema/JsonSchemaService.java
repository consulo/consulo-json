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
package com.jetbrains.jsonSchema;

import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

@ServiceAPI(ComponentScope.PROJECT)
public interface JsonSchemaService {
    final class Impl {
        public static JsonSchemaService get(Project project) {
            return project.getInstance(JsonSchemaService.class);
        }
    }

    static JsonSchemaService get(Project project) {
        return project.getInstance(JsonSchemaService.class);
    }

    static boolean isSchemaFile(PsiFile psiFile) {
        if (JsonLikePsiWalker.getWalker(psiFile, JsonSchemaObjectReadingUtils.NULL_OBJ) == null) {
            return false;
        }
        VirtualFile file = psiFile.getViewProvider().getVirtualFile();
        JsonSchemaService service = Impl.get(psiFile.getProject());
        return service.isSchemaFile(file) && service.isApplicableToFile(file);
    }

    boolean isSchemaFile(VirtualFile file);

    boolean isSchemaFile(JsonSchemaObject schemaObject);

    Project getProject();

    @Nullable
    JsonSchemaVersion getSchemaVersion(VirtualFile file);

    Collection<VirtualFile> getSchemaFilesForFile(VirtualFile file);

    Collection<VirtualFile> getSchemasForFile(VirtualFile file, boolean single, boolean onlyUserSchemas);

    @Nullable
    VirtualFile getDynamicSchemaForFile(PsiFile psiFile);

    void registerRemoteUpdateCallback(Runnable callback);

    void unregisterRemoteUpdateCallback(Runnable callback);

    void registerResetAction(Runnable action);

    void unregisterResetAction(Runnable action);

    void registerReference(String ref);

    boolean possiblyHasReference(String ref);

    void triggerUpdateRemote();

    @Nullable
    JsonSchemaObject getSchemaObject(VirtualFile file);

    @Nullable
    JsonSchemaObject getSchemaObject(PsiFile file);

    @Nullable
    JsonSchemaObject getSchemaObjectForSchemaFile(VirtualFile schemaFile);

    @Nullable
    VirtualFile findSchemaFileByReference(String reference, VirtualFile referent);

    /**
     * Returns whether references originating from {@code referent} must be confined to the project.
     * The result may change when the project trust state changes and must not be cached.
     */
    boolean shouldRestrictSchemaReferences(VirtualFile referent);

    /**
     * Finds a locally registered schema by its {@code $id} without going to the network.
     * Checks only schemas registered via providers (catalog, user mappings, bundled).
     */
    default @Nullable VirtualFile findBuiltInSchemaByReference(String reference) {
        return null;
    }

    @Nullable
    JsonSchemaFileProvider getSchemaProvider(VirtualFile schemaFile);

    @Nullable
    JsonSchemaFileProvider getSchemaProvider(JsonSchemaObject schemaObject);

    /**
     * Returns the provider that maps {@code file} to the schema of {@code schemaObject}.
     * Returns null when no provider does this. For example, the "$schema" property can select the schema.
     */
    default @Nullable JsonSchemaFileProvider getSchemaProviderForFile(VirtualFile file, JsonSchemaObject schemaObject) {
        return null;
    }

    @Nullable
    VirtualFile resolveSchemaFile(JsonSchemaObject schemaObject);

    void reset();

    List<JsonSchemaInfo> getAllUserVisibleSchemas();

    boolean isApplicableToFile(@Nullable VirtualFile file);

    JsonSchemaCatalogManager getCatalogManager();
}
