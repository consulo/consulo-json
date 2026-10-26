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
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import consulo.language.psi.ElementManipulators;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.util.ProcessingContext;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public final class JsonRequiredPropsReferenceProvider extends PsiReferenceProvider {
    @Override
    public PsiReference[] getReferencesByElement(PsiElement element, ProcessingContext context) {
        return new PsiReference[]{new JsonRequiredPropReference((JsonStringLiteral) element)};
    }

    public static @Nullable JsonObject findPropertiesObject(PsiElement element) {
        PsiElement parent = getParentSafe(getParentSafe(getParentSafe(element)));
        if (!(parent instanceof JsonObject)) {
            return null;
        }
        Optional<JsonProperty> propertiesProp =
            ((JsonObject) parent).getPropertyList().stream().filter(p -> "properties".equals(p.getName())).findFirst();
        if (propertiesProp.isPresent()) {
            JsonValue value = propertiesProp.get().getValue();
            if (value instanceof JsonObject) {
                return (JsonObject) value;
            }
        }
        return null;
    }

    private static @Nullable PsiElement getParentSafe(@Nullable PsiElement element) {
        return element == null ? null : element.getParent();
    }

    private static final class JsonRequiredPropReference extends JsonSchemaBaseReference<JsonStringLiteral> {
        JsonRequiredPropReference(JsonStringLiteral element) {
            super(element, ElementManipulators.getValueTextRange(element));
        }

        @Override
        public @Nullable PsiElement resolveInner() {
            JsonObject propertiesObject = findPropertiesObject(getElement());
            if (propertiesObject != null) {
                String name = getElement().getValue();
                for (JsonProperty property : propertiesObject.getPropertyList()) {
                    if (name.equals(property.getName())) {
                        return property;
                    }
                }
            }
            return null;
        }
    }
}
