// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase;
import jakarta.annotation.Nullable;

public class JsonSchemaRefResolverKt {

    @Nullable
    public static JsonSchemaObject resolveLocalSchemaNode(String maybeEmptyReference,
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

    @Nullable
    private static JsonSchemaObject resolveIdOrDynamicAnchor(String idOrAnchorName,
                                                             JsonSchemaObjectBackedByJacksonBase currentSchemaNode) {
        String maybeExistingIdOrAnchor = idOrAnchorName.substring(idOrAnchorName.indexOf('#') + 1);
        String effectiveSchemaNodePointer = currentSchemaNode.getRootSchemaObject().resolveDynamicAnchor(maybeExistingIdOrAnchor);
        if (effectiveSchemaNodePointer == null) {
            effectiveSchemaNodePointer = currentSchemaNode.getRootSchemaObject().resolveId(maybeExistingIdOrAnchor);
        }
        if (effectiveSchemaNodePointer == null || effectiveSchemaNodePointer.isBlank()) {
            return null;
        }
        return currentSchemaNode.getRootSchemaObject().getSchemaObjectByAbsoluteJsonPointer(effectiveSchemaNodePointer);
    }

    @Nullable
    private static JsonSchemaObject resolveReference(String reference, JsonSchemaObjectBackedByJacksonBase currentSchemaNode) {
        String maybeCorrectJsonPointer = reference.substring(reference.indexOf('#') + 1);
        return currentSchemaNode.getRootSchemaObject().getSchemaObjectByAbsoluteJsonPointer(maybeCorrectJsonPointer);
    }
}
