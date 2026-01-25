// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.jsonSchema.extension;

import com.jetbrains.jsonSchema.JsonSchemaService;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.ApplicationManager;
import consulo.application.WriteAction;
import consulo.application.dumb.PossiblyDumbAware;
import consulo.component.extension.ExtensionPointName;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.NonNls;
import jakarta.annotation.Nullable;

import java.net.URL;
import java.util.List;

/**
 * Implement to contribute JSON Schemas for particular JSON documents to enable validation/completion based on JSON Schema.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface JsonSchemaProviderFactory extends PossiblyDumbAware {
  ExtensionPointName<JsonSchemaProviderFactory> EP_NAME = ExtensionPointName.create(JsonSchemaProviderFactory.class);
  Logger LOG = Logger.getInstance(JsonSchemaProviderFactory.class);

  /**
   * Called in smart mode by default. Implement {@link consulo.application.dumb.DumbAware} to be called in dumb mode.
   */
  @Nonnull
  List<JsonSchemaFileProvider> getProviders(@Nonnull Project project);

  /**
   * Finds a {@link VirtualFile} instance corresponding to a specified resource path (relative or absolute).
   *
   * @param resourcePath  String identifying a resource (relative or absolute)
   *                      See {@link Class#getResource(String)} for more details
   * @return VirtualFile instance, or null if not found
   */
  static @Nullable VirtualFile getResourceFile(@Nonnull Class<?> baseClass, @NonNls @Nonnull String resourcePath) {
    URL url = baseClass.getResource(resourcePath);
    if (url == null) {
      LOG.error("Cannot find resource " + resourcePath);
      return null;
    }
    VirtualFile file = VirtualFileUtil.findFileByURL(url);
    if (file != null) {
      return file;
    }
    LOG.info("File not found by " + url + ", performing refresh...");
    ApplicationManager.getApplication().invokeLaterOnWriteThread(() -> {
      VirtualFile refreshed = WriteAction.compute(() -> {
        return VirtualFileManager.getInstance().refreshAndFindFileByUrl(VirtualFileUtil.convertFromUrl(url));
      });
      if (refreshed != null) {
        LOG.info("Refreshed " + url + " successfully");
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
          JsonSchemaService service = project.getService(JsonSchemaService.class);
          service.reset();
        }
      }
      else {
        LOG.error("Cannot refresh and find file by " + resourcePath);
      }
    });
    return null;
  }
}
