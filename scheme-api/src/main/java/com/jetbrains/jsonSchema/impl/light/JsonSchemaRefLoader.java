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
package com.jetbrains.jsonSchema.impl.light;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase;
import org.jspecify.annotations.Nullable;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import com.jetbrains.jsonSchema.internal.JsonFileResolver;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;

public interface JsonSchemaRefLoader {
    @Nullable
    JsonSchemaObject load(String reference, JsonSchemaObjectBackedByJacksonBase referenceOwner, JsonSchemaService service);

    public static @Nullable JsonSchemaObject resolveLocalSchemaNode(String maybeEmptyReference,
                                                                    JsonSchemaObjectBackedByJacksonBase currentSchemaNode) {
        if (maybeEmptyReference.startsWith("#/")) {
            return resolveReference(maybeEmptyReference, currentSchemaNode);
        }
        else if (maybeEmptyReference.startsWith("/")) {
            return resolveReference(maybeEmptyReference, currentSchemaNode);
        }
        else if (maybeEmptyReference.equals("#")) {
            return currentSchemaNode.getRootSchemaObject();
        }
        else if (maybeEmptyReference.startsWith("#")) {
            return resolveIdOrDynamicAnchor(maybeEmptyReference, currentSchemaNode);
        }
        else {
            return null;
        }
    }

    public static @Nullable JsonSchemaObject resolveRemoteSchemaByUrl(String reference, JsonSchemaObject schemaNode, JsonSchemaService service) {
        JsonSchemaObject value =
            JsonSchemaObjectReadingUtils.fetchSchemaFromRefDefinition(reference, schemaNode, service, schemaNode.isRefRecursive());
        if (!JsonFileResolver.isHttpPath(reference)) {
            service.registerReference(reference);
        }
        else if (value != null) {
            VirtualFile virtualFile = service.resolveSchemaFile(value);
            if (virtualFile != null) {
                service.registerReference(virtualFile.getName());
            }
        }
        return value;
    }

    private static @Nullable JsonSchemaObject resolveIdOrDynamicAnchor(String idOrAnchorName,
                                                                       JsonSchemaObjectBackedByJacksonBase currentSchemaNode) {
        String maybeExistingIdOrAnchor = StringUtil.trimLeading(idOrAnchorName, '#');
        String effectiveSchemaNodePointer = currentSchemaNode.getRootSchemaObject().resolveDynamicAnchor(maybeExistingIdOrAnchor);
        if (effectiveSchemaNodePointer == null) {
            effectiveSchemaNodePointer = currentSchemaNode.getRootSchemaObject().resolveId(maybeExistingIdOrAnchor);
        }
        if (StringUtil.isEmptyOrSpaces(effectiveSchemaNodePointer)) {
            return null;
        }
        return currentSchemaNode.getRootSchemaObject().getSchemaObjectByAbsoluteJsonPointer(effectiveSchemaNodePointer);
    }

    private static @Nullable JsonSchemaObject resolveReference(String reference, JsonSchemaObjectBackedByJacksonBase currentSchemaNode) {
        String maybeCorrectJsonPointer = StringUtil.trimLeading(reference, '#');
        return currentSchemaNode.getRootSchemaObject().getSchemaObjectByAbsoluteJsonPointer(maybeCorrectJsonPointer);
    }
}
