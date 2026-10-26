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
import consulo.logging.Logger;
import consulo.util.lang.StringUtil;

public final class JsonSchemaObjectRendering {
    private static final Logger LOG = Logger.getInstance(JsonSchemaObjectRendering.class);

    private JsonSchemaObjectRendering() {
    }

    public static String renderSchemaNode(JsonSchemaObject schemaNode, JsonSchemaObjectRenderingLanguage language) {
        if (!(schemaNode instanceof JsonSchemaObjectBackedByJacksonBase jacksonSchemaNode)) {
            LOG.warn("Unsupported JsonSchemaObject implementation provided: " + schemaNode.getClass().getSimpleName());
            return schemaNode.toString();
        }
        JsonNode mappedNode = jacksonSchemaNode.getRawSchemaNode();

        ObjectMapper mapper = switch (language) {
            case JSON -> JsonSchemaObjectStorage.json5ObjectMapper;
            case YAML -> JsonSchemaObjectStorage.yamlObjectMapper;
        };

        return serializeJsonNodeSafe(mappedNode, mapper);
    }

    private static String serializeJsonNodeSafe(JsonNode jsonNode, ObjectMapper serializer) {
        try {
            return StringUtil.trim(serializer.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode), ch -> ch != ' ' && ch != '\n');
        }
        catch (Exception exception) {
            LOG.warn("Error during JsonSchemaObjectSerialization", exception);
            return "";
        }
    }
}
