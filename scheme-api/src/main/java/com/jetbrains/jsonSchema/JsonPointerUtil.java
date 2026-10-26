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

import consulo.util.io.URLUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class JsonPointerUtil {
    public static String escapeForJsonPointer(String name) {
        if (StringUtil.isEmptyOrSpaces(name)) {
            return URLUtil.encodeURIComponent(name);
        }
        return StringUtil.replace(StringUtil.replace(name, "~", "~0"), "/", "~1");
    }

    public static String unescapeJsonPointerPart(String pointerPart) {
        String part = URLUtil.unescapePercentSequences(pointerPart);
        return StringUtil.replace(StringUtil.replace(part, "~0", "~"), "~1", "/");
    }

    public static boolean isSelfReference(@Nullable String ref) {
        return "#".equals(ref) || "#/".equals(ref) || StringUtil.isEmpty(ref);
    }

    public static List<String> split(String pointer) {
        return StringUtil.split(pointer, "/", true, false);
    }

    public static String normalizeSlashes(String ref) {
        return StringUtil.trimStart(ref.replace('\\', '/'), "/");
    }

    public static String normalizeId(String schemaId) {
        String id = schemaId.endsWith("#") ? schemaId.substring(0, schemaId.length() - 1) : schemaId;
        return id.startsWith("#") ? id.substring(1) : id;
    }
}
