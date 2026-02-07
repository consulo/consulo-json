// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import jakarta.annotation.Nonnull;

public class JsonSchemaInheritanceUtil {
    @Nonnull
    public static JsonSchemaObject inheritBaseSchemaIfNeeded(@Nonnull JsonSchemaObject parent, @Nonnull JsonSchemaObject child) {
        JsonSchemaObjectBackedByJacksonBase jacksonChild = null;
        if (child instanceof JsonSchemaObjectBackedByJacksonBase) {
            jacksonChild = (JsonSchemaObjectBackedByJacksonBase) child;
        }

        if (jacksonChild == null) {
            return child;
        }

        RootJsonSchemaObjectBackedByJackson rootObject = jacksonChild.getRootSchemaObject();
        if (rootObject == null) {
            return child;
        }

        JsonSchemaObject inheritedSchema = rootObject.getSchemaInterpretationStrategy()
            .inheritBaseSchema(parent, child);

        if (inheritedSchema != null) {
            return inheritedSchema;
        }

        return child;
    }
}
