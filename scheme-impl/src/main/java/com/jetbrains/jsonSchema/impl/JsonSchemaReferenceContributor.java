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

import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.jetbrains.jsonSchema.JsonSchemaService;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.pattern.FilterPattern;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.pattern.PsiElementPattern;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReferenceContributor;
import consulo.language.psi.PsiReferenceRegistrar;
import consulo.language.psi.filter.ElementFilter;
import consulo.util.lang.ObjectUtil;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@ExtensionImpl
public final class JsonSchemaReferenceContributor extends PsiReferenceContributor {
    private static final class Holder {
        private static final PsiElementPattern.Capture<JsonValue> REF_PATTERN = createPropertyValuePattern("$ref", true, false);
        private static final PsiElementPattern.Capture<JsonValue> REC_REF_PATTERN = createPropertyValuePattern("$recursiveRef", true, false);
        private static final PsiElementPattern.Capture<JsonValue> SCHEMA_PATTERN = createPropertyValuePattern("$schema", false, true);
        private static final PsiElementPattern.Capture<JsonStringLiteral> REQUIRED_PROP_PATTERN = createRequiredPropPattern();
    }

    @Override
    public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(Holder.REF_PATTERN, new JsonPointerReferenceProvider(false));
        registrar.registerReferenceProvider(Holder.REC_REF_PATTERN, new JsonPointerReferenceProvider(false));
        registrar.registerReferenceProvider(Holder.SCHEMA_PATTERN, new JsonPointerReferenceProvider(true));
        registrar.registerReferenceProvider(Holder.REQUIRED_PROP_PATTERN, new JsonRequiredPropsReferenceProvider());
    }

    @Override
    public Language getLanguage() {
        return JsonLanguage.INSTANCE;
    }

    private static PsiElementPattern.Capture<JsonValue> createPropertyValuePattern(
        @SuppressWarnings("SameParameterValue") String propertyName,
        boolean schemaOnly,
        boolean rootOnly
    ) {
        return PlatformPatterns.psiElement(JsonValue.class).and(new FilterPattern(new ElementFilter() {
            @Override
            public boolean isAcceptable(Object element, @Nullable PsiElement context) {
                if (element instanceof JsonValue) {
                    JsonProperty property = ObjectUtil.tryCast(((JsonValue) element).getParent(), JsonProperty.class);
                    if (property != null && property.getValue() == element && propertyName.equals(property.getName())) {
                        PsiFile file = property.getContainingFile();
                        if (rootOnly && (!(file instanceof JsonFile) || ((JsonFile) file).getTopLevelValue() != property.getParent())) {
                            return false;
                        }
                        if (schemaOnly && !JsonSchemaService.isSchemaFile(CompletionUtilCore.getOriginalOrSelf(file))) {
                            return false;
                        }
                        return true;
                    }
                }
                return false;
            }

            @Override
            public boolean isClassAcceptable(Class hintClass) {
                return true;
            }
        }));
    }

    private static PsiElementPattern.Capture<JsonStringLiteral> createRequiredPropPattern() {
        return PlatformPatterns.psiElement(JsonStringLiteral.class).and(new FilterPattern(new ElementFilter() {
            @Override
            public boolean isAcceptable(Object element, @Nullable PsiElement context) {
                if (!(element instanceof JsonStringLiteral)) {
                    return false;
                }
                PsiElement parent = ((JsonStringLiteral) element).getParent();
                if (!(parent instanceof JsonArray)) {
                    return false;
                }
                PsiElement property = parent.getParent();
                if (!(property instanceof JsonProperty)) {
                    return false;
                }
                return "required".equals(((JsonProperty) property).getName()) &&
                    JsonSchemaService.isSchemaFile(Objects.requireNonNull(((JsonStringLiteral) element).getContainingFile()));
            }

            @Override
            public boolean isClassAcceptable(Class hintClass) {
                return true;
            }
        }));
    }
}
