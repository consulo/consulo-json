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
package com.intellij.json.impl.json5;

import com.intellij.json.JsonDialectUtil;
import com.intellij.json.json5.Json5Language;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalkerFactory;
import com.jetbrains.jsonSchema.walker.JsonOriginalPsiWalker;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class Json5PsiWalkerFactory implements JsonLikePsiWalkerFactory {
    public static final JsonLikePsiWalker WALKER_INSTANCE = new JsonOriginalPsiWalker() {
        @Override
        public boolean requiresNameQuotes() {
            return false;
        }

        @Override
        public boolean allowsSingleQuotes() {
            return true;
        }
    };

    @Override
    public boolean handles(PsiElement element) {
        PsiElement parent = element.getParent();
        return parent != null && JsonDialectUtil.getLanguageOrDefaultJson(parent) == Json5Language.INSTANCE;
    }

    @Override
    public JsonLikePsiWalker create(@Nullable JsonSchemaObject schemaObject) {
        return WALKER_INSTANCE;
    }
}
