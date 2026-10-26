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
package com.jetbrains.jsonSchema.internal;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import consulo.application.progress.ProgressManager;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils.matchPattern;

public final class PatternProperties {
    public final Map<String, JsonSchemaObject> mySchemasMap;
    public final Map<String, Pattern> myCachedPatterns;
    public final Map<String, String> myCachedPatternProperties;

    public Map<String, JsonSchemaObject> getSchemasMap() {
        return mySchemasMap;
    }

    public PatternProperties(Map<String, ? extends JsonSchemaObject> schemasMap) {
        mySchemasMap = new HashMap<>();
        schemasMap.keySet().forEach(key -> mySchemasMap.put(StringUtil.unescapeBackSlashes(key), schemasMap.get(key)));
        myCachedPatterns = new HashMap<>();
        myCachedPatternProperties = ContainerUtil.createConcurrentWeakKeyWeakValueMap();
        mySchemasMap.keySet().forEach(key -> {
            ProgressManager.checkCanceled();
            Pair<@Nullable Pattern, @Nullable String> pair = JsonSchemaObjectReadingUtils.compilePattern(key);
            if (pair.getSecond() == null) {
                assert pair.getFirst() != null;
                myCachedPatterns.put(key, pair.getFirst());
            }
        });
    }

    public @Nullable JsonSchemaObject getPatternPropertySchema(String name) {
        String value = myCachedPatternProperties.get(name);
        if (value != null) {
            assert mySchemasMap.containsKey(value);
            return mySchemasMap.get(value);
        }

        value = myCachedPatterns.keySet().stream()
          .filter(key -> matchPattern(Objects.requireNonNull(myCachedPatterns.get(key)), name))
          .findFirst()
          .orElse(null);
        if (value != null) {
            myCachedPatternProperties.put(name, value);
            assert mySchemasMap.containsKey(value);
            return mySchemasMap.get(value);
        }
        return null;
    }
}
