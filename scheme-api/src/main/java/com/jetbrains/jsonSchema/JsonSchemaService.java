// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.List;

@ServiceAPI(ComponentScope.PROJECT)
public interface JsonSchemaService {
  final class Impl {
    public static JsonSchemaService get(@Nonnull Project project) {
      return project.getService(JsonSchemaService.class);
    }
  }

  static boolean isSchemaFile(@Nonnull PsiFile psiFile) {
    if (JsonLikePsiWalker.getWalker(psiFile, JsonSchemaObjectReadingUtils.NULL_OBJ) == null) return false;
    final VirtualFile file = psiFile.getViewProvider().getVirtualFile();
    JsonSchemaService service = Impl.get(psiFile.getProject());
    return service.isSchemaFile(file) && service.isApplicableToFile(file);
  }

  boolean isSchemaFile(@Nonnull VirtualFile file);
  boolean isSchemaFile(@Nonnull JsonSchemaObject schemaObject);

  @Nonnull
  Project getProject();

  @Nullable
  JsonSchemaVersion getSchemaVersion(@Nonnull VirtualFile file);

  @Nonnull
  Collection<VirtualFile> getSchemaFilesForFile(@Nonnull VirtualFile file);

  @Nullable
  VirtualFile getDynamicSchemaForFile(@Nonnull PsiFile psiFile);
  void registerRemoteUpdateCallback(@Nonnull Runnable callback);
  void unregisterRemoteUpdateCallback(@Nonnull Runnable callback);
  void registerResetAction(Runnable action);
  void unregisterResetAction(Runnable action);

  void registerReference(String ref);
  boolean possiblyHasReference(String ref);

  void triggerUpdateRemote();

  @Nullable
  JsonSchemaObject getSchemaObject(@Nonnull VirtualFile file);

  @Nullable
  JsonSchemaObject getSchemaObject(@Nonnull PsiFile file);

  @Nullable
  JsonSchemaObject getSchemaObjectForSchemaFile(@Nonnull VirtualFile schemaFile);

  @Nullable
  VirtualFile findSchemaFileByReference(@Nonnull String reference, @Nullable VirtualFile referent);

  @Nullable
  JsonSchemaFileProvider getSchemaProvider(final @Nonnull VirtualFile schemaFile);

  @Nullable
  JsonSchemaFileProvider getSchemaProvider(final @Nonnull JsonSchemaObject schemaObject);

  @Nullable
  VirtualFile resolveSchemaFile(final @Nonnull JsonSchemaObject schemaObject);

  void reset();

  List<JsonSchemaInfo> getAllUserVisibleSchemas();

  boolean isApplicableToFile(@Nullable VirtualFile file);

  @Nonnull
  JsonSchemaCatalogManager getCatalogManager();
}
