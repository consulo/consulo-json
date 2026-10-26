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
package com.jetbrains.jsonSchema.internal;

import com.intellij.json.pointer.JsonPointerPosition;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.extension.adapter.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.tree.JsonSchemaNodeExpansionRequest;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.DEFS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.JSON_DEFINITIONS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.JSON_PROPERTIES;
import static com.jetbrains.jsonSchema.internal.JsonSchemaValidityCache.getOrComputeAdapterValidityAgainstGivenSchema;

public final class JsonSchemaResolver {
    private final Project myProject;
    private final JsonSchemaObject mySchema;
    private final JsonPointerPosition myPosition;
    private final JsonSchemaNodeExpansionRequest myExpansionRequest;


    /**
     * @deprecated Use com.jetbrains.jsonSchema.impl.JsonSchemaResolver#JsonSchemaResolver(
     *  com.intellij.openapi.project.Project,
     *  com.jetbrains.jsonSchema.impl.JsonSchemaObject,
     *  com.intellij.json.pointer.JsonPointerPosition,
     *  com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
     * )
     */
    @Deprecated(forRemoval = true)
    public JsonSchemaResolver(Project project,
                              JsonSchemaObject schema,
                              JsonPointerPosition position) {
        this(project, schema, position, new JsonSchemaNodeExpansionRequest(null, true));
    }

    public JsonSchemaResolver(Project project,
                              JsonSchemaObject schema,
                              JsonPointerPosition position,
                              @Nullable JsonValueAdapter inspectedValueAdapter) {
        this(project, schema, position, new JsonSchemaNodeExpansionRequest(inspectedValueAdapter, true));
    }

    public JsonSchemaResolver(Project project,
                              JsonSchemaObject schema,
                              JsonPointerPosition position,
                              JsonSchemaNodeExpansionRequest expansionRequest) {
        myProject = project;
        mySchema = schema;
        myPosition = position;
        myExpansionRequest = expansionRequest;
    }

    public MatchResult detailedResolve() {
        JsonSchemaTreeNode node = JsonSchemaVariantsTreeBuilder.buildTree(myProject, myExpansionRequest, mySchema, myPosition, false);
        return MatchResult.create(node);
    }

    public Collection<JsonSchemaObject> resolve() {
        MatchResult result = detailedResolve();
        List<JsonSchemaObject> list = new LinkedList<>();
        list.addAll(result.mySchemas);
        for (Collection<? extends JsonSchemaObject> myExcludingSchema : result.myExcludingSchemas) {
            list.addAll(myExcludingSchema);
        }
        return list;
    }

    public @Nullable PsiElement findNavigationTarget(@Nullable PsiElement element) {
        JsonSchemaTreeNode node = JsonSchemaVariantsTreeBuilder
          .buildTree(myProject, myExpansionRequest, mySchema, myPosition, true);
        JsonSchemaObject schema = selectSchema(node, element, myPosition.isEmpty());
        if (schema == null) {
            return null;
        }
        VirtualFile file = JsonSchemaService.Impl.get(myProject).resolveSchemaFile(schema);
        if (file == null) {
            return null;
        }
        PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
        if (psiFile == null) {
            return null;
        }
        JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(psiFile, schema);
        return walker == null ? null : resolvePosition(walker, psiFile, JsonPointerPosition.parsePointer(schema.getPointer()));
    }

    public static @Nullable PsiElement resolvePosition(JsonLikePsiWalker walker,
                                                        @Nullable PsiElement element,
                                                        JsonPointerPosition position) {
        PsiElement psiElement = element instanceof PsiFile ? ContainerUtil.getFirstItem(walker.getRoots((PsiFile)element)) : element;
        if (psiElement == null) {
            return null;
        }
        JsonValueAdapter value = walker.createValueAdapter(psiElement);
        while (position != null && !position.isEmpty()) {
            if (value instanceof JsonObjectValueAdapter) {
                String name = position.getFirstName();
                if (name == null) {
                    return null;
                }
                JsonPropertyAdapter property = findProperty((JsonObjectValueAdapter)value, name);
                if (property != null) {
                    value = getValue(property);
                    if (value == null) {
                        return null;
                    }
                }
                else {
                    JsonPropertyAdapter props = findProperty((JsonObjectValueAdapter)value, JSON_PROPERTIES);
                    if (props != null) {
                        value = getValue(props);
                        continue;
                    }

                    JsonPropertyAdapter defs = findProperty((JsonObjectValueAdapter)value, JSON_DEFINITIONS);
                    if (defs != null) {
                        value = getValue(defs);
                        continue;
                    }

                    JsonPropertyAdapter defs9 = findProperty((JsonObjectValueAdapter)value, DEFS);
                    if (defs9 != null) {
                        value = getValue(defs9);
                        continue;
                    }
                    return null;
                }
            }
            else if (value instanceof JsonArrayValueAdapter) {
                int index = position.getFirstIndex();
                if (index >= 0) {
                    List<JsonValueAdapter> values = ((JsonArrayValueAdapter)value).getElements();
                    if (values.size() > index) {
                        value = values.get(index);
                    }
                    else {
                        return null;
                    }
                }
            }
            position = position.skip(1);
        }
        if (value == null) {
            return null;
        }

        PsiElement delegate = value.getDelegate();
        PsiElement propertyNameElement = walker.getPropertyNameElement(delegate.getParent());
        return propertyNameElement == null ? delegate : propertyNameElement;
    }

    private static @Nullable JsonValueAdapter getValue(JsonPropertyAdapter property) {
        Collection<JsonValueAdapter> values = property.getValues();
        return values.size() == 1 ? values.iterator().next() : null;
    }

    private static @Nullable JsonPropertyAdapter findProperty(JsonObjectValueAdapter value, String name) {
        List<JsonPropertyAdapter> list = value.getPropertyList();
        return ContainerUtil.find(list, p -> name.equals(p.getName()));
    }

    public static @Nullable JsonSchemaObject selectSchema(JsonSchemaTreeNode resolveRoot,
                                                    @Nullable PsiElement element,
                                                    boolean topLevelSchema) {
        MatchResult matchResult = MatchResult.create(resolveRoot);
        List<JsonSchemaObject> schemas = new ArrayList<>(matchResult.mySchemas);
        schemas.addAll(matchResult.myExcludingSchemas.stream().flatMap(Collection::stream).toList());

        JsonSchemaObject firstSchema = getFirstValidSchema(schemas);
        if (element == null || schemas.size() == 1 || firstSchema == null) {
            return firstSchema;
        }
        // actually we pass any schema here
        JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element, firstSchema);
        JsonValueAdapter adapter;
        if (walker == null || (adapter = walker.createValueAdapter(element)) == null) {
            return null;
        }

        JsonValueAdapter parentAdapter;
        if (topLevelSchema) {
            parentAdapter = null;
        }
        else {
            PsiElement parentValue = walker.getParentContainer(element);
            if (parentValue == null || (parentAdapter = walker.createValueAdapter(parentValue)) == null) {
                return null;
            }
        }

        Ref<JsonSchemaObject> schemaRef = new Ref<>();
        MatchResult.iterateTree(resolveRoot, node -> {
            JsonSchemaTreeNode parent = node.getParent();
            if (node.getSchema() == null || parentAdapter != null && parent != null && parent.isNothing()) {
                return true;
            }
            if (!isCorrect(adapter, node.getSchema())) {
                return true;
            }
            if (parentAdapter == null ||
                parent == null ||
                parent.getSchema() == null ||
                parent.isAny() ||
                isCorrect(parentAdapter, parent.getSchema())) {
                schemaRef.set(node.getSchema());
                return false;
            }
            return true;
        });
        return schemaRef.get();
    }

    private static @Nullable JsonSchemaObject getFirstValidSchema(List<JsonSchemaObject> schemas) {
        return schemas.stream().findFirst().orElse(null);
    }

    public static boolean isCorrect(JsonValueAdapter value, JsonSchemaObject schema) {
        return getOrComputeAdapterValidityAgainstGivenSchema(value, schema);
    }
}
