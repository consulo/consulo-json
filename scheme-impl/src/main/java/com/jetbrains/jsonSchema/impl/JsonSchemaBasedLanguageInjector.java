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

import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonStringLiteral;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.light.nodes.RootJsonSchemaObjectBackedByJackson;
import com.jetbrains.jsonSchema.internal.JsonSchemaResolver;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.inject.MultiHostRegistrar;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.util.lang.ThreeState;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;

@ExtensionImpl
public final class JsonSchemaBasedLanguageInjector extends JsonSchemaInjectorBase {
    @Override
    public void injectLanguages(MultiHostRegistrar registrar, PsiElement context) {
        if (!(context instanceof JsonStringLiteral)) {
            return;
        }
        InjectedLanguageData language = getLanguageToInject(context, false);
        if (language == null) {
            return;
        }
        injectForHost(registrar, (JsonStringLiteral) context, language);
    }

    public static @Nullable InjectedLanguageData getLanguageToInject(PsiElement context, boolean relaxPositionCheck) {
        Project project = context.getProject();
        PsiFile containingFile = Objects.requireNonNull(context.getContainingFile());
        JsonSchemaObject schemaObject = JsonSchemaService.get(project).getSchemaObject(containingFile);
        if (schemaObject instanceof RootJsonSchemaObjectBackedByJackson rootSchema && !rootSchema.checkHasInjections()) {
            return null;
        }
        if (schemaObject == null) {
            return null;
        }
        JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(context, schemaObject);
        if (walker == null) {
            return null;
        }
        ThreeState isName = walker.isName(context);
        if (relaxPositionCheck && isName == ThreeState.YES
            || !relaxPositionCheck && isName != ThreeState.NO) {
            return null;
        }
        JsonPointerPosition position = walker.findPosition(context, true);
        if (position == null || position.isEmpty()) {
            return null;
        }
        Collection<JsonSchemaObject> schemas =
            new JsonSchemaResolver(project, schemaObject, position, walker.createValueAdapter(context)).resolve();
        for (JsonSchemaObject schema : schemas) {
            String injection = schema.getLanguageInjection();
            if (injection != null) {
                Language language = Language.findLanguageByID(injection);
                if (language != null) {
                    return new InjectedLanguageData(language, schema.getLanguageInjectionPrefix(), schema.getLanguageInjectionPostfix());
                }
            }
        }
        return null;
    }
}
