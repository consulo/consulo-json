// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.jetbrains.jsonSchema.internal.JsonSchemaResolver;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ThreeState;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.X_INTELLIJ_LANGUAGE_INJECTION;

public abstract class JsonSchemaSpellcheckerClient {
    protected abstract PsiElement getElement();

    protected abstract @Nullable String getValue();

    public boolean matchesNameFromSchema() {
        PsiElement element = getElement();
        return LanguageCachedValueUtil.getProjectPsiDependentCache(element, this::matchesNameFromSchema);
    }

    private boolean matchesNameFromSchema(PsiElement element) {
        VirtualFile file = PsiUtilCore.getVirtualFile(element);
        if (file == null) {
            return false;
        }

        String value = getValue();
        if (StringUtil.isEmpty(value)) {
            return false;
        }

        Project project = element.getProject();
        JsonSchemaService service = JsonSchemaService.Impl.get(project);
        if (!service.isApplicableToFile(file)) {
            return false;
        }
        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null) {
            return false;
        }
        JsonSchemaObject rootSchema = service.getSchemaObject(containingFile);
        if (rootSchema == null) {
            return false;
        }
        if (isXIntellijInjection(service, rootSchema)) {
            return true;
        }

        JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element, rootSchema);
        if (walker == null) {
            return false;
        }
        PsiElement checkable = walker.findElementToCheck(element);
        if (checkable == null) {
            return false;
        }
        ThreeState isName = walker.isName(checkable);
        JsonPointerPosition position = walker.findPosition(checkable, isName == ThreeState.NO);
        if (position == null || position.isEmpty() && isName == ThreeState.NO) {
            return false;
        }

        Collection<JsonSchemaObject> schemas =
            new JsonSchemaResolver(project, rootSchema, position, walker.createValueAdapter(checkable)).resolve();
        if (schemas.isEmpty()) {
            return false;
        }

        return ContainerUtil.exists(schemas, s -> {
            if (s.getPropertyByName(value) != null || s.getMatchingPatternPropertySchema(value) != null) {
                return true;
            }
            List<Object> enumValues = s.getEnum();
            return ContainerUtil.exists(
                enumValues == null ? List.of() : enumValues,
                e -> e instanceof String && StringUtil.unquoteString((String) e).equals(value)
            );
        });
    }

    protected boolean isXIntellijInjection(JsonSchemaService service, JsonSchemaObject rootSchema) {
        if (service.isSchemaFile(rootSchema)) {
            JsonProperty property = ObjectUtil.tryCast(getElement().getParent(), JsonProperty.class);
            if (property != null) {
                if (X_INTELLIJ_LANGUAGE_INJECTION.equals(property.getName())) {
                    return true;
                }
                if ("language".equals(property.getName())) {
                    PsiElement parent = property.getParent();
                    if (parent instanceof JsonObject) {
                        PsiElement grandParent = parent.getParent();
                        if (grandParent instanceof JsonProperty && X_INTELLIJ_LANGUAGE_INJECTION.equals(((JsonProperty) grandParent).getName())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}
