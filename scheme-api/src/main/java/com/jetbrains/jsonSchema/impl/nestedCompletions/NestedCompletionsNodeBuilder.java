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
package com.jetbrains.jsonSchema.impl.nestedCompletions;

import java.util.function.Consumer;
import java.util.regex.Pattern;

public interface NestedCompletionsNodeBuilder {
    /**
     * Constructs a tree node that will not forward nested completions from outside down into it's children
     */
    void isolated(String name, Consumer<NestedCompletionsNodeBuilder> childBuilder);

    /**
     * Similar to {@link #isolated(String, Consumer)}, but it matches based on a regex
     * By default, all completions are {@code isolated(".*".toRegex()) {}}
     */
    void isolated(Pattern regex, Consumer<NestedCompletionsNodeBuilder> childBuilder);

    /** Constructs a node that allows completions from outside to nest into this node */
    void open(String name, Consumer<NestedCompletionsNodeBuilder> childBuilder);

    default void open(String name) {
        open(name, builder -> {});
    }
}
