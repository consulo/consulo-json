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
package com.intellij.json;

import com.intellij.json.psi.JsonStringLiteral;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;

/**
 * Tells if the JSON schema of the file defines a string literal, for example as a property name or an enum value.
 * The spellchecker and Grazie do not check such a literal.
 * <p>
 * The JSON schema implementation is in the JSON schema modules.
 * Without them, no literal matches.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface JsonSchemaNameMatcher {
    boolean matches(JsonStringLiteral literal);

    static boolean matchesNameFromSchema(JsonStringLiteral literal) {
        return Application.get().getExtensionPoint(JsonSchemaNameMatcher.class).anyMatchSafe(it -> it.matches(literal));
    }
}
