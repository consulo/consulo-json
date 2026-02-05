// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension;

import consulo.json.localize.JsonLocalize;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.jetbrains.annotations.Nls;

import java.util.Arrays;
import java.util.List;

public class JsonSchemaProjectSelfProviderFactory implements JsonSchemaProviderFactory, DumbAware {

  @Nonnull
  @Override
  public List<JsonSchemaFileProvider> getProviders(@Nonnull Project project) {
    return Arrays.asList(
      new MyJsonSchemaFileProvider(project, new BundledJsonSchemaInfo(
        JsonSchemaVersion.SCHEMA_4,
        "schema.json",
        "4",
        "http://json-schema.org/draft-04/schema")),
      new MyJsonSchemaFileProvider(project, new BundledJsonSchemaInfo(
        JsonSchemaVersion.SCHEMA_6,
        "schema06.json",
        "6",
        "http://json-schema.org/draft-06/schema")),
      new MyJsonSchemaFileProvider(project, new BundledJsonSchemaInfo(
        JsonSchemaVersion.SCHEMA_7,
        "schema07.json",
        "7",
        "http://json-schema.org/draft-07/schema")),
      new MyJsonSchemaFileProvider(project, new BundledJsonSchemaInfo(
        JsonSchemaVersion.SCHEMA_2019_09,
        "schema201909.json",
        "2019-09",
        "https://json-schema.org/draft/2019-09/schema")),
      new MyJsonSchemaFileProvider(project, new BundledJsonSchemaInfo(
        JsonSchemaVersion.SCHEMA_2020_12,
        "schema202012.json",
        "2020-12",
        "https://json-schema.org/draft/2020-12/schema"))
    );
  }

  public static class BundledJsonSchemaInfo {
    private final JsonSchemaVersion version;
    private final String bundledResourceFileName;
    private final @Nls String presentableSchemaId;
    private final String remoteSourceUrl;

    public BundledJsonSchemaInfo(JsonSchemaVersion version, String bundledResourceFileName,
                                 @Nls String presentableSchemaId, String remoteSourceUrl) {
      this.version = version;
      this.bundledResourceFileName = bundledResourceFileName;
      this.presentableSchemaId = presentableSchemaId;
      this.remoteSourceUrl = remoteSourceUrl;
    }

    public JsonSchemaVersion getVersion() {
      return version;
    }

    public String getBundledResourceFileName() {
      return bundledResourceFileName;
    }

    public @Nls String getPresentableSchemaId() {
      return presentableSchemaId;
    }

    public String getRemoteSourceUrl() {
      return remoteSourceUrl;
    }
  }

  static class MyJsonSchemaFileProvider implements JsonSchemaFileProvider {
    private final Project myProject;
    private final BundledJsonSchemaInfo myBundledSchema;

    MyJsonSchemaFileProvider(Project myProject, BundledJsonSchemaInfo myBundledSchema) {
      this.myProject = myProject;
      this.myBundledSchema = myBundledSchema;
    }

    @Override
    public boolean isAvailable(@Nonnull VirtualFile file) {
      if (myProject.isDisposed()) return false;
      JsonSchemaService service = JsonSchemaService.Impl.get(myProject);
      if (!service.isApplicableToFile(file)) return false;
      JsonSchemaVersion instanceSchemaVersion = service.getSchemaVersion(file);
      if (instanceSchemaVersion == null) return false;
      return instanceSchemaVersion == myBundledSchema.version;
    }

    @Nonnull
    @Override
    public JsonSchemaVersion getSchemaVersion() {
      return myBundledSchema.version;
    }

    @Nullable
    @Override
    public VirtualFile getSchemaFile() {
      return JsonSchemaProviderFactory.getResourceFile(
        JsonSchemaProjectSelfProviderFactory.class,
        "/jsonSchema/" + myBundledSchema.bundledResourceFileName
      );
    }

    @Nonnull
    @Override
    public SchemaType getSchemaType() {
      return SchemaType.schema;
    }

    @Nonnull
    @Override
    public String getRemoteSource() {
      return myBundledSchema.remoteSourceUrl;
    }

    @Nonnull
    @Override
    public String getPresentableName() {
      return JsonLocalize.schemaOfVersion(myBundledSchema.presentableSchemaId).get();
    }

    @Nonnull
    @Override
    public String getName() {
      return getPresentableName();
    }
  }
}
