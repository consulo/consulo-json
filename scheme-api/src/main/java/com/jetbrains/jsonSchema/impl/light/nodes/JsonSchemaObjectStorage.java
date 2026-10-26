// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema.impl.light.nodes;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;

@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@Singleton
public class JsonSchemaObjectStorage {
    public static JsonSchemaObjectStorage getInstance(Project project) {
        return project.getInstance(JsonSchemaObjectStorage.class);
    }

    private record SchemaId(VirtualFile schemaFile, long modificationStamp) {
    }

    private final Map<SchemaId, JsonSchemaObject> myParsedSchemaById =
        ConcurrentFactoryMap.createMap(id -> createRootSchemaObject(id.schemaFile()));

    public @Nullable JsonSchemaObject getOrComputeSchemaRootObject(VirtualFile schemaFile) {
        if (!isSupportedSchemaFile(schemaFile)) {
            return null;
        }

        JsonSchemaObject schemaObject = myParsedSchemaById.get(asSchemaId(schemaFile));
        return schemaObject instanceof MissingJsonSchemaObject ? null : schemaObject;
    }

    public @Nullable JsonSchemaObject getComputedSchemaRootOrNull(VirtualFile maybeSchemaFile) {
        if (!myParsedSchemaById.containsKey(asSchemaId(maybeSchemaFile))) {
            return null;
        }

        JsonSchemaObject schemaObject = myParsedSchemaById.get(asSchemaId(maybeSchemaFile));
        return schemaObject instanceof MissingJsonSchemaObject ? null : schemaObject;
    }

    private boolean isSupportedSchemaFile(VirtualFile maybeSchemaFile) {
        return isSupportedSchemaFileType(maybeSchemaFile.getFileType());
    }

    private boolean isSupportedSchemaFileType(FileType fileType) {
        return mySupportedFileTypeNames.contains(fileType.getId());
    }

    private final Set<String> mySupportedFileTypeNames = Set.of("JSON", "JSON5", "YAML");

    private static SchemaId asSchemaId(VirtualFile file) {
        if (file instanceof LightVirtualFile) {
            return new SchemaId(file, -1);
        }
        else {
            return new SchemaId(file, file.getModificationStamp());
        }
    }

    private static JsonSchemaObject createRootSchemaObject(VirtualFile schemaFile) {
        JsonNode parsedSchemaRoot = parseSchemaFileSafe(schemaFile);
        return parsedSchemaRoot == null
            ? MissingJsonSchemaObject.INSTANCE
            : new RootJsonSchemaObjectBackedByJackson(parsedSchemaRoot, schemaFile);
    }

    private static @Nullable JsonNode parseSchemaFileSafe(VirtualFile schemaFile) {
        String providedFileTypeId = schemaFile.getFileType().getId();
        ObjectMapper suitableReader = switch (providedFileTypeId) {
            case "JSON", "JSON5" -> JsonSchemaObjectStorageKt.json5ObjectMapper;
            case "YAML" -> JsonSchemaObjectStorageKt.yamlObjectMapper;
            default -> null;
        };
        if (suitableReader == null) {
            Logger.getInstance("JsonSchemaReader").warn("Unsupported json schema file type: " + providedFileTypeId);
            return null;
        }
        try (InputStream inputStream = schemaFile.getInputStream()) {
            return suitableReader.readTree(inputStream);
        }
        catch (CancellationException e) {
            throw e;
        }
        catch (Exception exception) {
            Logger.getInstance("JsonSchemaReader2")
                .warn("Unable to parse JSON schema from the given file '" + schemaFile.getName() + "'", exception);
            return null;
        }
    }
}
