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
package com.jetbrains.jsonSchema.impl;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.light.nodes.JacksonSchemaNodeAccessor;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase;
import consulo.logging.Logger;
import org.jspecify.annotations.Nullable;

public final class JsonSchemaTraversalUtils {
    private static final Logger LOG = Logger.getInstance(JsonSchemaTraversalUtils.class);

    private JsonSchemaTraversalUtils() {
    }

    public static @Nullable String getChildAsText(JsonSchemaObject schemaObject, String... relativeChildPath) {
        if (schemaObject instanceof JsonSchemaObjectBackedByJacksonBase jacksonBacked) {
            JsonNode rawChildNode = jacksonBacked.getRawSchemaNode();
            for (String childName : relativeChildPath) {
                JsonNode resolved = JacksonSchemaNodeAccessor.INSTANCE.resolveRelativeNode(rawChildNode, childName);
                rawChildNode = resolved == null ? MissingNode.getInstance() : resolved;
            }
            return JacksonSchemaNodeAccessor.INSTANCE.readTextNodeValue(rawChildNode, null);
        }
        else {
            LOG.warn("JSON schema traverser does not provide support for " + schemaObject.getClass().getSimpleName());
            return null;
        }
    }
}
