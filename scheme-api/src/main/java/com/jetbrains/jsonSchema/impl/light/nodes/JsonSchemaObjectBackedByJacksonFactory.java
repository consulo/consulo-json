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
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.light.JsonSchemaObjectFactory;
import com.jetbrains.jsonSchema.internal.JsonSchemaReader;
import consulo.application.util.ConcurrentFactoryMap;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.ROOT_POINTER_VARIANTS;

public class JsonSchemaObjectBackedByJacksonFactory implements JsonSchemaObjectFactory<JsonNode, JsonSchemaObjectBackedByJacksonBase> {
    private final RootJsonSchemaObjectBackedByJackson myRootSchemaObject;
    private final Map<String, JsonSchemaObjectBackedByJacksonBase> myRegisteredChildren =
        ConcurrentFactoryMap.createMap(this::computeSchemaObjectByPointer);

    public JsonSchemaObjectBackedByJacksonFactory(RootJsonSchemaObjectBackedByJackson rootSchemaObject) {
        myRootSchemaObject = rootSchemaObject;
    }

    @Override
    public @Nullable JsonSchemaObject getSchemaObjectByAbsoluteJsonPointer(String jsonPointer) {
        JsonSchemaObjectBackedByJacksonBase schemaObject = myRegisteredChildren.get(jsonPointer);
        return schemaObject instanceof MissingJsonSchemaObject ? null : schemaObject;
    }

    @Override
    public @Nullable JsonSchemaObjectBackedByJacksonBase getChildSchemaObjectByName(JsonSchemaObjectBackedByJacksonBase parentSchemaObject,
                                                                                   String... childNodeRelativePointer) {
        if (childNodeRelativePointer.length == 0) {
            return parentSchemaObject;
        }
        String childAbsolutePointer = computeAbsoluteJsonPointer(parentSchemaObject.getPointer(), childNodeRelativePointer);

        if (isRootObjectPointer(childAbsolutePointer)) {
            return myRootSchemaObject;
        }
        JsonSchemaObjectBackedByJacksonBase schemaObject = myRegisteredChildren.get(childAbsolutePointer);
        return schemaObject instanceof MissingJsonSchemaObject ? null : schemaObject;
    }

    private JsonSchemaObjectBackedByJacksonBase computeSchemaObjectByPointer(String jsonPointer) {
        JsonNode resolvedRelativeChildSchemaNode =
            JacksonSchemaNodeAccessor.INSTANCE.resolveNode(myRootSchemaObject.getRawSchemaNode(), jsonPointer);
        if (resolvedRelativeChildSchemaNode == null
            || resolvedRelativeChildSchemaNode.isMissingNode()
            || resolvedRelativeChildSchemaNode.isArray()) {
            return MissingJsonSchemaObject.INSTANCE;
        }
        return new JsonSchemaObjectBackedByJackson(myRootSchemaObject, resolvedRelativeChildSchemaNode, jsonPointer);
    }

    private static String computeAbsoluteJsonPointer(String basePointer, String... relativePointer) {
        String joinedPointer = Arrays.stream(relativePointer)
            .map(JacksonSchemaNodeAccessor::escapeForbiddenJsonPointerSymbols)
            .collect(Collectors.joining("/"));
        return JsonSchemaReader.getNewPointer(joinedPointer, basePointer);
    }

    private static boolean isRootObjectPointer(String jsonPointer) {
        return ROOT_POINTER_VARIANTS.contains(jsonPointer);
    }
}
