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
package com.jetbrains.jsonSchema;

import consulo.json.localize.JsonLocalize;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

public enum JsonSchemaVersion {
    SCHEMA_4,
    SCHEMA_6,
    SCHEMA_7,
    SCHEMA_2019_09,
    SCHEMA_2020_12;

    private static final String ourSchemaV4Schema = "http://json-schema.org/draft-04/schema";
    private static final String ourSchemaV6Schema = "http://json-schema.org/draft-06/schema";
    private static final String ourSchemaV7Schema = "http://json-schema.org/draft-07/schema";
    private static final String ourSchemaV201909Schema = "http://json-schema.org/draft/2019-09/schema";
    private static final String ourSchemaV202012Schema = "http://json-schema.org/draft/2020-12/schema";
    private static final String ourSchemaOrgPrefix = "http://json-schema.org/";

    @Override
    public String toString() {
        return JsonLocalize.schemaOfVersion(getPresentableVersionSuffix()).get();
    }

    public String getPresentableVersionSuffix() {
        return switch (this) {
            case SCHEMA_4 -> "4";
            case SCHEMA_6 -> "6";
            case SCHEMA_7 -> "7";
            case SCHEMA_2019_09 -> "2019.09";
            case SCHEMA_2020_12 -> "2020.12";
        };
    }


    public static @Nullable JsonSchemaVersion byId(String schemaId) {
        String id = schemaId;
        if (id.startsWith("https://")) {
            id = "http://" + id.substring("https://".length());
        }
        return switch (StringUtil.trimEnd(id, '#')) {
            case ourSchemaV4Schema -> SCHEMA_4;
            case ourSchemaV6Schema -> SCHEMA_6;
            case ourSchemaV7Schema -> SCHEMA_7;
            case ourSchemaV201909Schema -> SCHEMA_2019_09;
            case ourSchemaV202012Schema -> SCHEMA_2020_12;
            default -> id.startsWith(ourSchemaOrgPrefix) ? SCHEMA_7 : null;
        };
    }

    public static boolean isSchemaSchemaId(@Nullable String id) {
        return id != null && byId(id) != null;
    }
}
