// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.impl.navigation.JsonQualifiedNameKind;
import com.intellij.json.impl.navigation.JsonQualifiedNameProvider;
import com.intellij.json.psi.*;
import com.jetbrains.jsonSchema.JsonPointerUtil;
import com.jetbrains.jsonSchema.JsonSchemaCatalogEntry;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectStorage;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.application.util.registry.Registry;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.impl.internal.psi.AstLoadingFilter;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.SyntaxTraverser;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

public final class JsonCachedValues {
  private static final Key<CachedValue<JsonSchemaObject>> JSON_OBJECT_CACHE_KEY = Key.create("JsonSchemaObjectCache");

  public static @Nullable JsonSchemaObject getSchemaObject(@Nonnull VirtualFile schemaFile, @Nonnull Project project) {
    JsonFileResolver.startFetchingHttpFileIfNeeded(schemaFile, project);
    if (Registry.is("json.schema.object.v2")) {
      return JsonSchemaObjectStorage.getInstance(project)
        .getOrComputeSchemaRootObject(schemaFile);
    }
    else {
      return computeForFile(schemaFile, project, (psiFile) -> {
        return JsonSchemaCacheManager.getInstance(psiFile.getProject()).computeSchemaObject(schemaFile, psiFile);
      }, JSON_OBJECT_CACHE_KEY);
    }
  }

  public static final String URL_CACHE_KEY = "JsonSchemaUrlCache";
  private static final Key<CachedValue<String>> SCHEMA_URL_KEY = Key.create(URL_CACHE_KEY);

  public static @Nullable String getSchemaUrlFromSchemaProperty(@Nonnull VirtualFile file,
                                                                                   @Nonnull Project project) {
    if (Registry.is("json.schema.object.v2")) {
      JsonSchemaObject schemaRootOrNull = JsonSchemaObjectStorage.getInstance(project).getComputedSchemaRootOrNull(file);
      if (schemaRootOrNull != null) {
        return schemaRootOrNull.getSchema();
      }
    }
    String value = JsonSchemaFileValuesIndex.getCachedValue(project, file, URL_CACHE_KEY);
    if (value != null) {
      return JsonSchemaFileValuesIndex.NULL.equals(value) ? null : value;
    }

    PsiFile psiFile = resolveFile(file, project);
    return psiFile == null ? null : getOrCompute(psiFile, JsonCachedValues::fetchSchemaUrl, SCHEMA_URL_KEY);
  }

  private static PsiFile resolveFile(@Nonnull VirtualFile file,
                                     @Nonnull Project project) {
    if (project.isDisposed() || !file.isValid()) return null;
    return PsiManager.getInstance(project).findFile(file);
  }

  private static @Nullable String fetchSchemaUrl(@Nullable PsiFile psiFile) {
    if (psiFile == null) return null;
    if (psiFile instanceof JsonFile) {
      String url = JsonSchemaFileValuesIndex.readTopLevelProps(psiFile.getFileType(), psiFile.getText()).get(URL_CACHE_KEY);
      return url == null || JsonSchemaFileValuesIndex.NULL.equals(url) ? null : url;
    }
    else {
      JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(psiFile, JsonSchemaObjectReadingUtils.NULL_OBJ);
      if (walker != null) {
        Collection<PsiElement> roots = walker.getRoots(psiFile);
        for (PsiElement root : ObjectUtils.notNull(roots, List.<PsiElement>of())) {
          JsonValueAdapter adapter = walker.createValueAdapter(root);
          JsonObjectValueAdapter object = adapter != null ? adapter.getAsObject() : null;
          if (object != null) {
            List<JsonPropertyAdapter> list = object.getPropertyList();
            for (JsonPropertyAdapter propertyAdapter : list) {
              if (JsonSchemaFileValuesIndex.SCHEMA_PROPERTY_NAME.equals(propertyAdapter.getName())) {
                Collection<JsonValueAdapter> values = propertyAdapter.getValues();
                if (values.size() == 1) {
                  JsonValueAdapter item = ContainerUtil.getFirstItem(values);
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

  public static @Nullable String getSchemaId(final @Nonnull VirtualFile schemaFile,
                                             final @Nonnull Project project) {
    //skip content loading for generated schema files (IntellijConfigurationJsonSchemaProviderFactory)
    if (schemaFile instanceof LightVirtualFile) return null;

    if (Registry.is("json.schema.object.v2")) {
      JsonSchemaObject schemaRootOrNull = JsonSchemaObjectStorage.getInstance(project).getOrComputeSchemaRootObject(schemaFile);
      if (schemaRootOrNull != null) {
        return schemaRootOrNull.getId();
      }
    }
    String value = JsonSchemaFileValuesIndex.getCachedValue(project, schemaFile, ID_CACHE_KEY);
    if (value != null && !JsonSchemaFileValuesIndex.NULL.equals(value)) return JsonPointerUtil.normalizeId(value);
    String obsoleteValue = JsonSchemaFileValuesIndex.getCachedValue(project, schemaFile, OBSOLETE_ID_CACHE_KEY);
    if (obsoleteValue != null && !JsonSchemaFileValuesIndex.NULL.equals(obsoleteValue)) return JsonPointerUtil.normalizeId(obsoleteValue);
    if (JsonSchemaFileValuesIndex.NULL.equals(value) || JsonSchemaFileValuesIndex.NULL.equals(obsoleteValue)) return null;

    final String result = computeForFile(schemaFile, project, JsonCachedValues::fetchSchemaId, SCHEMA_ID_CACHE_KEY);
    return result == null ? null : JsonPointerUtil.normalizeId(result);
  }

  private static @Nullable <T> T computeForFile(final @Nonnull VirtualFile schemaFile,
                                                final @Nonnull Project project,
                                                @Nonnull Function<? super PsiFile, ? extends T> eval,
                                                @Nonnull Key<CachedValue<T>> cacheKey) {
    final PsiFile psiFile = resolveFile(schemaFile, project);
    if (psiFile == null) return null;
    return getOrCompute(psiFile, eval, cacheKey);
  }

  static final String ID_PATHS_CACHE_KEY = "JsonSchemaIdToPointerCache";
  private static final Key<CachedValue<Map<String, String>>> SCHEMA_ID_PATHS_CACHE_KEY = Key.create(ID_PATHS_CACHE_KEY);

  public static Collection<String> getAllIdsInFile(PsiFile psiFile) {
    Map<String, String> map = getOrComputeIdsMap(psiFile);
    return map == null ? ContainerUtil.emptyList() : map.keySet();
  }

  public static @Nullable String resolveId(PsiFile psiFile, String id) {
    Map<String, String> map = getOrComputeIdsMap(psiFile);
    return map == null ? null : map.get(id);
  }

  public static @Nullable Map<String, String> getOrComputeIdsMap(PsiFile psiFile) {
    return getOrCompute(psiFile, JsonCachedValues::computeIdsMap, SCHEMA_ID_PATHS_CACHE_KEY);
  }

  private static @Nonnull Map<String, String> computeIdsMap(PsiFile file) {
    return SyntaxTraverser.psiTraverser(file).filter(JsonProperty.class)
      .filter(p -> "$id".equals(StringUtil.unquoteString(p.getNameElement().getText())))
      .filter(p -> p.getValue() instanceof JsonStringLiteral)
      .toMap(p -> ((JsonStringLiteral)Objects.requireNonNull(p.getValue())).getValue(),
             p -> JsonQualifiedNameProvider.generateQualifiedName(p.getParent(), JsonQualifiedNameKind.JsonPointer));
  }

  static @Nullable String fetchSchemaId(@Nonnull PsiFile psiFile) {
    if (!(psiFile instanceof JsonFile)) return null;
    final Map<String, String> props = JsonSchemaFileValuesIndex.readTopLevelProps(psiFile.getFileType(), psiFile.getText());
    final String id = props.get(ID_CACHE_KEY);
    if (id != null && !JsonSchemaFileValuesIndex.NULL.equals(id)) return id;
    final String obsoleteId = props.get(OBSOLETE_ID_CACHE_KEY);
    return obsoleteId == null || JsonSchemaFileValuesIndex.NULL.equals(obsoleteId) ? null : obsoleteId;
  }


  private static final Key<CachedValue<List<JsonSchemaCatalogEntry>>> SCHEMA_CATALOG_CACHE_KEY = Key.create("JsonSchemaCatalogCache");

  public static @Nullable List<JsonSchemaCatalogEntry> getSchemaCatalog(final @Nonnull VirtualFile catalog,
                                                                        final @Nonnull Project project) {
    if (!catalog.isValid()) return null;
    return computeForFile(catalog, project, JsonCachedValues::computeSchemaCatalog, SCHEMA_CATALOG_CACHE_KEY);
  }

  private static List<JsonSchemaCatalogEntry> computeSchemaCatalog(PsiFile catalog) {
    if (!catalog.isValid()) return null;
    VirtualFile virtualFile = catalog.getVirtualFile();
    if (virtualFile == null || !virtualFile.isValid()) return null;
    JsonValue value =
      AstLoadingFilter.forceAllowTreeLoading(catalog, () -> catalog instanceof JsonFile ? ((JsonFile)catalog).getTopLevelValue() : null);
    if (!(value instanceof JsonObject)) return null;

    JsonProperty schemas = ((JsonObject)value).findProperty("schemas");
    if (schemas == null) return null;

    JsonValue schemasValue = schemas.getValue();
    if (!(schemasValue instanceof JsonArray)) return null;
    List<JsonSchemaCatalogEntry> catalogMap = new ArrayList<>();
    fillMap((JsonArray)schemasValue, catalogMap);
    return catalogMap;
  }

  private static void fillMap(@Nonnull JsonArray array, @Nonnull List<JsonSchemaCatalogEntry> catalogMap) {
    for (JsonValue value : array.getValueList()) {
      JsonObject obj = ObjectUtils.tryCast(value, JsonObject.class);
      if (obj == null) continue;
      JsonProperty fileMatch = obj.findProperty("fileMatch");
      Collection<String> masks = fileMatch == null ? ContainerUtil.emptyList() : resolveMasks(fileMatch.getValue());
      final String urlString = readStringValue(obj.findProperty("url"));
      if (urlString == null) continue;
      catalogMap.add(new JsonSchemaCatalogEntry(masks, urlString,
                                                readStringValue(obj.findProperty("name")),
                                                readStringValue(obj.findProperty("description"))));
    }
  }

  private static @Nullable @NlsSafe String readStringValue(@Nullable JsonProperty property) {
    if (property == null) return null;
    JsonValue urlValue = property.getValue();
    if (urlValue instanceof JsonStringLiteral) {
      String urlStringValue = ((JsonStringLiteral)urlValue).getValue();
      if (!StringUtil.isEmpty(urlStringValue)) {
        return urlStringValue;
      }
    }
    return null;
  }

  private static @Nonnull Collection<String> resolveMasks(@Nullable JsonValue value) {
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

    return ContainerUtil.emptyList();
  }

  private static @Nullable <T> T getOrCompute(@Nonnull PsiFile psiFile,
                                              @Nonnull Function<? super PsiFile, ? extends T> eval,
                                              @Nonnull Key<CachedValue<T>> key) {
    return CachedValuesManager.getCachedValue(psiFile, key, () -> CachedValueProvider.Result.create(eval.fun(psiFile), psiFile));
  }

  public static final Key<CachedValue<JsonSchemaObject>> OBJECT_FOR_FILE_KEY = new Key<>("JsonCachedValues.OBJ_KEY");

  static @Nullable JsonSchemaObject computeSchemaForFile(@Nonnull PsiFile file, @Nonnull JsonSchemaService service) {
    final PsiFile originalFile = CompletionUtil.getOriginalOrSelf(file);
    JsonSchemaObject value = CachedValuesManager.getCachedValue(originalFile, OBJECT_FOR_FILE_KEY, () -> {
      Pair<PsiFile, JsonSchemaObject> schema = getSchemaFile(originalFile, service);

      PsiFile psiFile = schema.first;
      JsonSchemaObject object = schema.second == null ? JsonSchemaObjectReadingUtils.NULL_OBJ : schema.second;
      return psiFile == null
             ? CachedValueProvider.Result.create(object, originalFile, service)
             : CachedValueProvider.Result.create(object, originalFile, psiFile, service);
    });
    return value == JsonSchemaObjectReadingUtils.NULL_OBJ ? null : value;
  }

  @SuppressWarnings("unchecked")
  public static boolean hasComputedSchemaObjectForFile(@Nonnull PsiFile file) {
    CachedValueBase<JsonSchemaObject> data = (CachedValueBase<JsonSchemaObject>)CompletionUtil.getOriginalOrSelf(file).getUserData(OBJECT_FOR_FILE_KEY);
    if (data == null) return false;

    Getter<JsonSchemaObject> cachedValueGetter = data.getUpToDateOrNull();
    if (cachedValueGetter == null) return false;

    JsonSchemaObject upToDateCachedValueOrNull = cachedValueGetter.get();
    return upToDateCachedValueOrNull != null && upToDateCachedValueOrNull != JsonSchemaObjectReadingUtils.NULL_OBJ;
  }

  private static @Nonnull Pair<PsiFile, JsonSchemaObject> getSchemaFile(@Nonnull PsiFile originalFile,
                                                                        @Nonnull JsonSchemaService service) {
    VirtualFile virtualFile = originalFile.getVirtualFile();
    VirtualFile schemaFile = virtualFile == null ? null : getSchemaFile(virtualFile, service);
    JsonSchemaObject schemaObject = virtualFile == null ? null : service.getSchemaObject(virtualFile);
    PsiFile psiFile = schemaFile == null || !schemaFile.isValid() ? null : originalFile.getManager().findFile(schemaFile);
    return new Pair<>(psiFile, schemaObject);
  }

  static VirtualFile getSchemaFile(@Nonnull VirtualFile sourceFile, @Nonnull JsonSchemaService service) {
    JsonSchemaServiceImpl serviceImpl = (JsonSchemaServiceImpl)service;
    Collection<VirtualFile> schemas = serviceImpl.getSchemasForFile(sourceFile, true, false);
    if (schemas.isEmpty()) return null;
    assert schemas.size() == 1;
    return schemas.iterator().next();
  }
}
