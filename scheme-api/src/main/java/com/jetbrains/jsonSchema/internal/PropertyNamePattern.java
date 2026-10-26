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

import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.regex.Pattern;

import static com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils.matchPattern;

public final class PropertyNamePattern {
    public final String myPattern;
    public final @Nullable Pattern myCompiledPattern;
    public final @Nullable String myPatternError;
    public final Map<String, Boolean> myValuePatternCache;

    public PropertyNamePattern(String pattern) {
        myPattern = StringUtil.unescapeBackSlashes(pattern);
        Pair<@Nullable Pattern, @Nullable String> pair = JsonSchemaObjectReadingUtils.compilePattern(pattern);
        myPatternError = pair.getSecond();
        myCompiledPattern = pair.getFirst();
        myValuePatternCache = ContainerUtil.createConcurrentWeakKeyWeakValueMap();
    }

    public @Nullable String getPatternError() {
        return myPatternError;
    }

    public boolean checkByPattern(String name) {
        if (myPatternError != null) {
            return true;
        }
        if (Boolean.TRUE.equals(myValuePatternCache.get(name))) {
            return true;
        }
        assert myCompiledPattern != null;
        boolean matches = matchPattern(myCompiledPattern, name);
        myValuePatternCache.put(name, matches);
        return matches;
    }

    public String getPattern() {
        return myPattern;
    }
}
