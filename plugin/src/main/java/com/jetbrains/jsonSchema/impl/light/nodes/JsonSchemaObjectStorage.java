// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.nodes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.util.ConcurrentFactoryMap;
import consulo.language.file.light.LightVirtualFile;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.http.HttpVirtualFile;
import consulo.virtualFileSystem.http.RemoteFileState;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;

@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public class JsonSchemaObjectStorage {
  private static final Logger LOG = Logger.getInstance(JsonSchemaObjectStorage.class);
  private static final Set<String> SUPPORTED_FILE_TYPE_NAMES = Set.of("JSON", "JSON5", "YAML");

  public static JsonSchemaObjectStorage getInstance(@NotNull Project project) {
    return project.getInstance(JsonSchemaObjectStorage.class);
  }

  private static class SchemaId {
    private final VirtualFile schemaFile;
    private final long modificationStamp;

    SchemaId(@NotNull VirtualFile schemaFile, long modificationStamp) {
      this.schemaFile = schemaFile;
      this.modificationStamp = modificationStamp;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      SchemaId schemaId = (SchemaId) o;
      return modificationStamp == schemaId.modificationStamp && schemaFile.equals(schemaId.schemaFile);
    }

    @Override
    public int hashCode() {
      return Objects.hash(schemaFile, modificationStamp);
    }
  }

  private final Map<SchemaId, JsonSchemaObject> parsedSchemaById =
    ConcurrentFactoryMap.createMap(id -> createRootSchemaObject(id.schemaFile));

  @Nullable
  public JsonSchemaObject getOrComputeSchemaRootObject(@NotNull VirtualFile schemaFile) {
    if (!isSupportedSchemaFile(schemaFile)) return null;

    JsonSchemaObject result = parsedSchemaById.get(asSchemaId(schemaFile));
    return result instanceof MissingJsonSchemaObject ? null : result;
  }

  @Nullable
  public JsonSchemaObject getComputedSchemaRootOrNull(@NotNull VirtualFile maybeSchemaFile) {
    SchemaId schemaId = asSchemaId(maybeSchemaFile);
    if (!parsedSchemaById.containsKey(schemaId)) return null;

    JsonSchemaObject result = parsedSchemaById.get(schemaId);
    return result instanceof MissingJsonSchemaObject ? null : result;
  }

  private boolean isSupportedSchemaFile(@NotNull VirtualFile maybeSchemaFile) {
    return isSupportedSchemaFileType(maybeSchemaFile.getFileType())
           && (!(maybeSchemaFile instanceof HttpVirtualFile) || isLoadedHttpFile((HttpVirtualFile) maybeSchemaFile));
  }

  private boolean isSupportedSchemaFileType(@NotNull FileType fileType) {
    return SUPPORTED_FILE_TYPE_NAMES.contains(fileType.getName());
  }

  private boolean isLoadedHttpFile(@NotNull HttpVirtualFile maybeHttpFile) {
    return maybeHttpFile.getFileInfo() != null && maybeHttpFile.getFileInfo().getState() == RemoteFileState.DOWNLOADED;
  }

  @NotNull
  private SchemaId asSchemaId(@NotNull VirtualFile file) {
    if (file instanceof LightVirtualFile) {
      return new SchemaId(file, -1);
    } else {
      return new SchemaId(file, file.getModificationStamp());
    }
  }

  @NotNull
  private JsonSchemaObject createRootSchemaObject(@NotNull VirtualFile schemaFile) {
    JsonNode parsedSchemaRoot = parseSchemaFileSafe(schemaFile);
    return parsedSchemaRoot == null
           ? MissingJsonSchemaObject.INSTANCE
           : new RootJsonSchemaObjectBackedByJackson(parsedSchemaRoot, schemaFile);
  }

  @Nullable
  private JsonNode parseSchemaFileSafe(@NotNull VirtualFile schemaFile) {
    String providedFileTypeId = schemaFile.getFileType().getName();
    ObjectMapper suitableReader;

    switch (providedFileTypeId) {
      case "JSON":
      case "JSON5":
        suitableReader = JsonSchemaObjectStorageKt.getJson5ObjectMapper();
        break;
      case "YAML":
        suitableReader = JsonSchemaObjectStorageKt.getYamlObjectMapper();
        break;
      default:
        LOG.warn("Unsupported json schema file type: " + providedFileTypeId);
        return null;
    }

    try (InputStream inputStream = schemaFile.getInputStream()) {
      return suitableReader.readTree(inputStream);
    } catch (CancellationException e) {
      throw e;
    } catch (Exception exception) {
      LOG.warn("Unable to parse JSON schema from the given file '" + schemaFile.getName() + "'", exception);
      return null;
    }
  }
}

