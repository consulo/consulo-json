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

import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

public final class JsonSchemaObjectStorageKt {
    public static final JsonMapper json5ObjectMapper = JsonMapper.builder(
        JsonFactory.builder()
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
            .enable(JsonReadFeature.ALLOW_UNQUOTED_PROPERTY_NAMES)
            .enable(JsonReadFeature.ALLOW_MISSING_VALUES)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .enable(JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)
            .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
            .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
            .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .build()
    ).build();

    public static final YAMLMapper yamlObjectMapper = YAMLMapper.builder(
        YAMLFactory.builder()
            .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
            .build()
    ).build();

    private JsonSchemaObjectStorageKt() {
    }
}
