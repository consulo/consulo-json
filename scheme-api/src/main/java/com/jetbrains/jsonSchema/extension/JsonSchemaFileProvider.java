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
package com.jetbrains.jsonSchema.extension;

import com.jetbrains.jsonSchema.JsonSchemaVersion;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

public interface JsonSchemaFileProvider {
    boolean isAvailable(VirtualFile file);

    String getName();

    /**
     * @see JsonSchemaProviderFactory#getResourceFile(Class, String)
     */
    @Nullable
    VirtualFile getSchemaFile();

    SchemaType getSchemaType();

    default JsonSchemaVersion getSchemaVersion() {
        return JsonSchemaVersion.SCHEMA_4;
    }

    /**
     * Returns the highlight type for validation problems. The default uses the severity from the inspection profile.
     */
    default ProblemHighlightType getValidationHighlightType() {
        return ProblemHighlightType.GENERIC_ERROR_OR_WARNING;
    }

    /**
     * Information about the provided API (e.g., an API version or the target platform).
     * This is useful for auto-generated schemas targeting multiple versions of the same config.
     */
    default @Nullable String getThirdPartyApiInformation() {
        return null;
    }

    /**
     * @return {@code true} if this schema is shown and selectable by the user in the schema dropdown menu.
     * Some schemas are designed to be auto-assigned and bound to very particular contexts,
     * and thus hidden from the selector.
     */
    default boolean isUserVisible() { return true; }

    /**
     * Presentable name of the schema shown in the UI.
     */
    default String getPresentableName() { return getName(); }

    /**
     * A URL to download an up-to-date schema version.
     * <p>
     * It's recommended to have it matching the URL for the schema in the SchemaStore catalogue.
     * If for some reason you need to have a unique URL for your schema, which is not a schema file (for example, its website),
     * you need to postfix it with '!'.
     */
    default @Nullable String getRemoteSource() { return null; }
}
