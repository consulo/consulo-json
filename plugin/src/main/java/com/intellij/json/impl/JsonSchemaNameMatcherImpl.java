// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.intellij.json.impl;

import com.jetbrains.jsonSchema.JsonSchemaSpellcheckerClient;
import com.intellij.json.JsonSchemaNameMatcher;
import com.intellij.json.psi.JsonStringLiteral;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;

@ExtensionImpl
public final class JsonSchemaNameMatcherImpl implements JsonSchemaNameMatcher {
    @Override
    public boolean matches(JsonStringLiteral literal) {
        return new JsonSchemaSpellcheckerClientForJson(literal).matchesNameFromSchema();
    }

    private static final class JsonSchemaSpellcheckerClientForJson extends JsonSchemaSpellcheckerClient {
        private final JsonStringLiteral myLiteral;

        private JsonSchemaSpellcheckerClientForJson(JsonStringLiteral literal) {
            myLiteral = literal;
        }

        @Override
        protected PsiElement getElement() {
            return myLiteral;
        }

        @Override
        protected String getValue() {
            return myLiteral.getValue();
        }
    }
}
