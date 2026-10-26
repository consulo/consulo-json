// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import com.intellij.json.internal.navigation.JsonQualifiedNameKind;
import com.intellij.json.internal.navigation.JsonQualifiedNameProvider;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.jetbrains.jsonSchema.JsonPointerUtil;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.extension.adapter.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectStorage;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.SyntaxTraverser;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public final class JsonCachedValues {
    private static final Key<CachedValue<JsonSchemaObject>> JSON_OBJECT_CACHE_KEY = Key.create("JsonSchemaObjectCache");

    public static @Nullable JsonSchemaObject getSchemaObject(VirtualFile schemaFile, Project project) {
        return JsonSchemaObjectStorage.getInstance(project).getOrComputeSchemaRootObject(schemaFile);
    }

    public static final String URL_CACHE_KEY = "JsonSchemaUrlCache";
    private static final Key<CachedValue<String>> SCHEMA_URL_KEY = Key.create(URL_CACHE_KEY);

    public static @Nullable String getSchemaUrlFromSchemaProperty(VirtualFile file,
                                                                  Project project) {
        JsonSchemaObject schemaRootOrNull = JsonSchemaObjectStorage.getInstance(project).getComputedSchemaRootOrNull(file);
        if (schemaRootOrNull != null) {
            return schemaRootOrNull.getSchema();
        }

        // likely unneeded execution branch
        String value = JsonSchemaFileValuesIndex.getCachedValue(project, file, URL_CACHE_KEY);
        if (value != null) {
            return JsonSchemaFileValuesIndex.NULL.equals(value) ? null : value;
        }

        PsiFile psiFile = resolveFile(file, project);
        return psiFile == null ? null : getOrCompute(psiFile, JsonCachedValues::fetchSchemaUrl, SCHEMA_URL_KEY);
    }

    private static @Nullable PsiFile resolveFile(VirtualFile file,
                                       Project project) {
        if (project.isDisposed() || !file.isValid()) {
            return null;
        }
        return PsiManager.getInstance(project).findFile(file);
    }

    private static @Nullable String fetchSchemaUrl(@Nullable PsiFile psiFile) {
        if (psiFile == null) {
            return null;
        }
        if (psiFile instanceof JsonFile) {
            String url = JsonSchemaFileValuesIndex.readTopLevelProps(psiFile.getFileType(), psiFile.getText()).get(URL_CACHE_KEY);
            return url == null || JsonSchemaFileValuesIndex.NULL.equals(url) ? null : url;
        }
        else {
            JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(psiFile, JsonSchemaObjectReadingUtils.NULL_OBJ);
            if (walker != null) {
                Collection<PsiElement> roots = walker.getRoots(psiFile);
                for (PsiElement root : ObjectUtil.notNull(roots, List.<PsiElement>of())) {
                    JsonValueAdapter adapter = walker.createValueAdapter(root);
                    JsonObjectValueAdapter object = adapter != null ? adapter.getAsObject() : null;
                    if (object != null) {
                        List<JsonPropertyAdapter> list = object.getPropertyList();
                        for (JsonPropertyAdapter propertyAdapter : list) {
                            if (JsonSchemaFileValuesIndex.SCHEMA_PROPERTY_NAME.equals(propertyAdapter.getName())) {
                                Collection<JsonValueAdapter> values = propertyAdapter.getValues();
                                if (values.size() == 1) {
                                    JsonValueAdapter item = values.iterator().next();
                                    if (item.isStringLiteral()) {
                                        return StringUtil.unquoteString(item.getDelegate().getText());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public static final String ID_CACHE_KEY = "JsonSchemaIdCache";
    public static final String OBSOLETE_ID_CACHE_KEY = "JsonSchemaObsoleteIdCache";
    private static final Key<CachedValue<String>> SCHEMA_ID_CACHE_KEY = Key.create(ID_CACHE_KEY);

    public static @Nullable String getSchemaId(VirtualFile schemaFile,
                                               Project project) {
        //skip content loading for generated schema files (IntellijConfigurationJsonSchemaProviderFactory)
        if (schemaFile instanceof LightVirtualFile) {
            return null;
        }

        JsonSchemaObject schemaRootOrNull = JsonSchemaObjectStorage.getInstance(project).getOrComputeSchemaRootObject(schemaFile);
        if (schemaRootOrNull != null) {
            return schemaRootOrNull.getId();
        }

        // likely unneeded execution branch
        String value = JsonSchemaFileValuesIndex.getCachedValue(project, schemaFile, ID_CACHE_KEY);
        if (value != null && !JsonSchemaFileValuesIndex.NULL.equals(value)) {
            return JsonPointerUtil.normalizeId(value);
        }
        String obsoleteValue = JsonSchemaFileValuesIndex.getCachedValue(project, schemaFile, OBSOLETE_ID_CACHE_KEY);
        if (obsoleteValue != null && !JsonSchemaFileValuesIndex.NULL.equals(obsoleteValue)) {
            return JsonPointerUtil.normalizeId(obsoleteValue);
        }
        if (JsonSchemaFileValuesIndex.NULL.equals(value) || JsonSchemaFileValuesIndex.NULL.equals(obsoleteValue)) {
            return null;
        }

        String result = computeForFile(schemaFile, project, JsonCachedValues::fetchSchemaId, SCHEMA_ID_CACHE_KEY);
        return result == null ? null : JsonPointerUtil.normalizeId(result);
    }

    private static @Nullable <T> T computeForFile(VirtualFile schemaFile,
                                                  Project project,
                                                  Function<? super PsiFile, ? extends @Nullable T> eval,
                                                  Key<CachedValue<T>> cacheKey) {
        PsiFile psiFile = resolveFile(schemaFile, project);
        if (psiFile == null) {
            return null;
        }
        return getOrCompute(psiFile, eval, cacheKey);
    }

    static final String ID_PATHS_CACHE_KEY = "JsonSchemaIdToPointerCache";
    private static final Key<CachedValue<Map<String, String>>> SCHEMA_ID_PATHS_CACHE_KEY = Key.create(ID_PATHS_CACHE_KEY);

    public static Collection<String> getAllIdsInFile(PsiFile psiFile) {
        Map<String, String> map = getOrComputeIdsMap(psiFile);
        return map == null ? List.of() : map.keySet();
    }

    public static @Nullable String resolveId(PsiFile psiFile, String id) {
        Map<String, String> map = getOrComputeIdsMap(psiFile);
        return map == null ? null : map.get(id);
    }

    public static @Nullable Map<String, String> getOrComputeIdsMap(PsiFile psiFile) {
        return getOrCompute(psiFile, JsonCachedValues::computeIdsMap, SCHEMA_ID_PATHS_CACHE_KEY);
    }

    private static Map<String, String> computeIdsMap(PsiFile file) {
        return SyntaxTraverser.psiTraverser(file).filter(JsonProperty.class)
          .filter(p -> "$id".equals(StringUtil.unquoteString(p.getNameElement().getText())))
          .filter(p -> p.getValue() instanceof JsonStringLiteral)
          .toMap(p -> ((JsonStringLiteral)Objects.requireNonNull(p.getValue())).getValue(),
                 p -> JsonQualifiedNameProvider.generateQualifiedName(p.getParent(), JsonQualifiedNameKind.JsonPointer));
    }

    static @Nullable String fetchSchemaId(PsiFile psiFile) {
        if (!(psiFile instanceof JsonFile)) {
            return null;
        }
        Map<String, String> props = JsonSchemaFileValuesIndex.readTopLevelProps(psiFile.getFileType(), psiFile.getText());
        String id = props.get(ID_CACHE_KEY);
        if (id != null && !JsonSchemaFileValuesIndex.NULL.equals(id)) {
            return id;
        }
        String obsoleteId = props.get(OBSOLETE_ID_CACHE_KEY);
        return obsoleteId == null || JsonSchemaFileValuesIndex.NULL.equals(obsoleteId) ? null : obsoleteId;
    }


    private static final Key<CachedValue<List<JsonSchemaCatalogEntry>>> SCHEMA_CATALOG_CACHE_KEY = Key.create("JsonSchemaCatalogCache");

    public static @Nullable List<JsonSchemaCatalogEntry> getSchemaCatalog(VirtualFile catalog,
                                                                          Project project) {
        if (!catalog.isValid()) {
            return null;
        }
        return computeForFile(catalog, project, JsonCachedValues::computeSchemaCatalog, SCHEMA_CATALOG_CACHE_KEY);
    }

    private static @Nullable List<JsonSchemaCatalogEntry> computeSchemaCatalog(PsiFile catalog) {
        if (!catalog.isValid()) {
            return null;
        }
        VirtualFile virtualFile = catalog.getVirtualFile();
        if (virtualFile == null || !virtualFile.isValid()) {
            return null;
        }
        JsonValue value =
          catalog instanceof JsonFile ? ((JsonFile)catalog).getTopLevelValue() : null;
        if (!(value instanceof JsonObject)) {
            return null;
        }

        JsonProperty schemas = ((JsonObject)value).findProperty("schemas");
        if (schemas == null) {
            return null;
        }

        JsonValue schemasValue = schemas.getValue();
        if (!(schemasValue instanceof JsonArray)) {
            return null;
        }
        List<JsonSchemaCatalogEntry> catalogMap = new ArrayList<>();
        fillMap((JsonArray)schemasValue, catalogMap);
        return catalogMap;
    }

    private static void fillMap(JsonArray array, List<JsonSchemaCatalogEntry> catalogMap) {
        for (JsonValue value : array.getValueList()) {
            JsonObject obj = ObjectUtil.tryCast(value, JsonObject.class);
            if (obj == null) {
                continue;
            }
            JsonProperty fileMatch = obj.findProperty("fileMatch");
            Collection<String> masks = fileMatch == null ? List.of() : resolveMasks(fileMatch.getValue());
            String urlString = readStringValue(obj.findProperty("url"));
            if (urlString == null) {
                continue;
            }
            catalogMap.add(new JsonSchemaCatalogEntry(masks, urlString,
                                                      readStringValue(obj.findProperty("name")),
                                                      readStringValue(obj.findProperty("description"))));
        }
    }

    private static @Nullable String readStringValue(@Nullable JsonProperty property) {
        if (property == null) {
            return null;
        }
        JsonValue urlValue = property.getValue();
        if (urlValue instanceof JsonStringLiteral) {
            String urlStringValue = ((JsonStringLiteral)urlValue).getValue();
            if (!StringUtil.isEmpty(urlStringValue)) {
                return urlStringValue;
            }
        }
        return null;
    }

    private static Collection<String> resolveMasks(@Nullable JsonValue value) {
        if (value instanceof JsonStringLiteral) {
            return ContainerUtil.createMaybeSingletonList(((JsonStringLiteral)value).getValue());
        }

        if (value instanceof JsonArray) {
            List<String> strings = new ArrayList<>();
            for (JsonValue val : ((JsonArray)value).getValueList()) {
                if (val instanceof JsonStringLiteral) {
                    strings.add(((JsonStringLiteral)val).getValue());
                }
            }
            return strings;
        }

        return List.of();
    }

    private static @Nullable <T> T getOrCompute(PsiFile psiFile,
                                                Function<? super PsiFile, ? extends @Nullable T> eval,
                                                Key<CachedValue<T>> key) {
        return LanguageCachedValueUtil.getCachedValue(psiFile, key, () -> CachedValueProvider.Result.create(eval.apply(psiFile), psiFile));
    }

    public static final Key<CachedValue<JsonSchemaObject>> OBJECT_FOR_FILE_KEY = new Key<>("JsonCachedValues.OBJ_KEY");

    public static @Nullable JsonSchemaObject computeSchemaForFile(PsiFile file, JsonSchemaService service) {
        PsiFile originalFile = CompletionUtilCore.getOriginalOrSelf(file);
        JsonSchemaObject value = LanguageCachedValueUtil.getCachedValue(originalFile, OBJECT_FOR_FILE_KEY, () -> {
            Pair<@Nullable PsiFile, @Nullable JsonSchemaObject> schema = getSchemaFile(originalFile, service);

            PsiFile psiFile = schema.first;
            JsonSchemaObject object = schema.second == null ? JsonSchemaObjectReadingUtils.NULL_OBJ : schema.second;
            return psiFile == null
                   ? CachedValueProvider.Result.create(object, originalFile, service)
                   : CachedValueProvider.Result.create(object, originalFile, psiFile, service);
        });
        return value == JsonSchemaObjectReadingUtils.NULL_OBJ ? null : value;
    }

    @SuppressWarnings("unchecked")
    public static boolean hasComputedSchemaObjectForFile(PsiFile file) {
        CachedValue<JsonSchemaObject> data = (CachedValue<JsonSchemaObject>)CompletionUtilCore.getOriginalOrSelf(file).getUserData(OBJECT_FOR_FILE_KEY);
        if (data == null) {
            return false;
        }

        Supplier<JsonSchemaObject> cachedValueGetter = data.getUpToDateOrNull();
        if (cachedValueGetter == null) {
            return false;
        }

        JsonSchemaObject upToDateCachedValueOrNull = cachedValueGetter.get();
        return upToDateCachedValueOrNull != null && upToDateCachedValueOrNull != JsonSchemaObjectReadingUtils.NULL_OBJ;
    }

    private static Pair<@Nullable PsiFile, @Nullable JsonSchemaObject> getSchemaFile(PsiFile originalFile,
                                                                          JsonSchemaService service) {
        VirtualFile virtualFile = originalFile.getVirtualFile();
        VirtualFile schemaFile = virtualFile == null ? null : getSchemaFile(virtualFile, service);
        JsonSchemaObject schemaObject = virtualFile == null ? null : service.getSchemaObject(virtualFile);
        PsiFile psiFile = schemaFile == null || !schemaFile.isValid() ? null : originalFile.getManager().findFile(schemaFile);
        return new Pair<>(psiFile, schemaObject);
    }

    static @Nullable VirtualFile getSchemaFile(VirtualFile sourceFile, JsonSchemaService service) {
        Collection<VirtualFile> schemas = service.getSchemasForFile(sourceFile, true, false);
        if (schemas.isEmpty()) {
            return null;
        }
        assert schemas.size() == 1;
        return schemas.iterator().next();
    }
}
