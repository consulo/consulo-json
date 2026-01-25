// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.jetbrains.jsonSchema.JsonPointerUtil;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.internal.JsonSchemaObjectImpl;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import consulo.application.util.registry.Registry;
import consulo.component.ProcessCanceledException;
import consulo.logging.Logger;
import consulo.util.collection.FactoryMap;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.http.HttpVirtualFile;
import consulo.virtualFileSystem.http.RemoteFileInfo;
import consulo.virtualFileSystem.http.RemoteFileState;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JsonSchemaObjectReadingUtils {
  private static final Logger LOG = Logger.getInstance(JsonSchemaObjectReadingUtils.class);
  public static final @Nonnull JsonSchemaObject NULL_OBJ = new JsonSchemaObjectImpl("$_NULL_$");

  public static boolean hasProperties(@Nonnull JsonSchemaObject schemaObject) {
    return schemaObject.getPropertyNames().hasNext();
  }


  /**
   * @deprecated Use {@link  com.jetbrains.jsonSchema.impl.light.JsonSchemaRefResolverKt#resolveRefSchema}
   */
  @Deprecated()
  public static @Nullable JsonSchemaObject resolveRefSchema(@Nonnull JsonSchemaObject schemaNode, @Nonnull JsonSchemaService service) {
    final String ref = schemaNode.getRef();
    assert !StringUtil.isEmptyOrSpaces(ref);

    if (schemaNode instanceof JsonSchemaObjectImpl schemaImpl) {
      var refsStorage = schemaImpl.getComputedRefsStorage(service.getProject());
      var schemaObject = refsStorage.getOrDefault(ref, NULL_OBJ);
      if (schemaObject != NULL_OBJ) return schemaObject;
    }

    var value = fetchSchemaFromRefDefinition(ref, schemaNode, service, schemaNode.isRefRecursive());
    if (!JsonFileResolver.isHttpPath(ref)) {
      service.registerReference(ref);
    }
    else if (value != null) {
      // our aliases - if http ref actually refers to a local file with specific ID
      VirtualFile virtualFile = service.resolveSchemaFile(value);
      if (virtualFile != null && !(virtualFile instanceof HttpVirtualFile)) {
        service.registerReference(virtualFile.getName());
      }
    }

    if (schemaNode instanceof JsonSchemaObjectImpl schemaImpl && value instanceof JsonSchemaObjectImpl valueImpl) {
      if (value != NULL_OBJ && !Objects.equals(value.getFileUrl(), schemaNode.getFileUrl())) {
        valueImpl.setBackReference(schemaImpl);
      }
      schemaImpl.getComputedRefsStorage(service.getProject()).put(ref, value);
    }
    return value;
  }

  private static final Map<String, JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter> complexReferenceCache
    = FactoryMap.create((key) -> new JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter(key));

  public static @Nullable JsonSchemaObject fetchSchemaFromRefDefinition(@Nonnull String ref,
                                                                        final @Nonnull JsonSchemaObject schema,
                                                                        @Nonnull JsonSchemaService service,
                                                                        boolean recursive) {

    final VirtualFile schemaFile = service.resolveSchemaFile(schema);
    if (schemaFile == null) return null;
    final JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter splitter;
    if (Registry.is("json.schema.object.v2")) {
      splitter = complexReferenceCache.get(ref);
    }
    else {
      splitter = new JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter(ref);
    }
    String schemaId = splitter.getSchemaId();
    if (schemaId != null) {
      var refSchema = resolveSchemaByReference(service, schemaFile, schemaId);
      if (refSchema == null || refSchema == NULL_OBJ) return null;
      return findRelativeDefinition(refSchema, splitter, service);
    }
    var rootSchema = service.getSchemaObjectForSchemaFile(schemaFile);
    if (rootSchema == null) {
      LOG.debug(String.format("Schema object not found for %s", schemaFile.getPath()));
      return null;
    }
    if (recursive && ref.startsWith("#")) {
      while (rootSchema.isRecursiveAnchor()) {
        var backRef = rootSchema.getBackReference();
        if (backRef == null) break;
        VirtualFile file = ObjectUtils.coalesce(backRef.getRawFile(),
                                                backRef.getFileUrl() == null ? null : JsonFileResolver.urlToFile(backRef.getFileUrl()));
        if (file == null) break;
        try {
          rootSchema = JsonSchemaReader.readFromFile(service.getProject(), file);
        }
        catch (Exception e) {
          break;
        }
      }
    }
    return findRelativeDefinition(rootSchema, splitter, service);
  }

  private static @Nullable JsonSchemaObject resolveSchemaByReference(@Nonnull JsonSchemaService service,
                                                                     @Nonnull VirtualFile schemaFile,
                                                                     @Nonnull String schemaId) {
    final VirtualFile refFile = service.findSchemaFileByReference(schemaId, schemaFile);
    if (refFile == null) {
      LOG.debug(String.format("Schema file not found by reference: '%s' from %s", schemaId, schemaFile.getPath()));
      return null;
    }
    var refSchema = downloadAndParseRemoteSchema(service, refFile);
    if (refSchema == null) {
      LOG.debug(String.format("Schema object not found by reference: '%s' from %s", schemaId, schemaFile.getPath()));
    }
    return refSchema;
  }

  public static @Nullable JsonSchemaObject downloadAndParseRemoteSchema(@Nonnull JsonSchemaService service, @Nonnull VirtualFile refFile) {
    if (refFile instanceof HttpVirtualFile) {
      RemoteFileInfo info = ((HttpVirtualFile)refFile).getFileInfo();
      if (info != null) {
        RemoteFileState state = info.getState();
        if (state == RemoteFileState.DOWNLOADING_NOT_STARTED) {
          JsonSchemaHighlightingSessionStatisticsCollector.getInstance().reportSchemaUsageFeature(JsonSchemaFusCountedFeature.ExecutedHttpVirtualFileDownloadRequest);
          JsonFileResolver.startFetchingHttpFileIfNeeded(refFile, service.getProject());
          return NULL_OBJ;
        }
        else if (state == RemoteFileState.DOWNLOADING_IN_PROGRESS) {
          return NULL_OBJ;
        }
      }
    }
    return service.getSchemaObjectForSchemaFile(refFile);
  }

  public static JsonSchemaObject findRelativeDefinition(final @Nonnull JsonSchemaObject schema,
                                                        final @Nonnull JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter splitter,
                                                        @Nonnull JsonSchemaService service) {
    final String path = splitter.getRelativePath();
    if (StringUtil.isEmptyOrSpaces(path)) {
      final String id = splitter.getSchemaId();
      if (JsonPointerUtil.isSelfReference(id)) {
        return schema;
      }
      if (id != null && id.startsWith("#")) {
        JsonSchemaObject rootSchemaObject = schema.getRootSchemaObject();
        if (rootSchemaObject instanceof RootJsonSchemaObject<?, ?> explicitRootSchemaObject) {
          final String resolvedId = explicitRootSchemaObject.resolveId(id);
          if (resolvedId == null || id.equals("#" + resolvedId)) return null;
          return findRelativeDefinition(schema, new JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter("#" + resolvedId), service);
        }
      }
      return schema;
    }
    final JsonSchemaObject definition = findRelativeDefinition(schema, path);
    if (definition == null) {
      if (LOG.isDebugEnabled()) {
        VirtualFile schemaFile = service.resolveSchemaFile(schema);
        String debugMessage = String.format("Definition not found by reference: '%s' in file %s",
                                            path, schemaFile == null ? "(no file)" : schemaFile.getPath());
        LOG.debug(debugMessage);
      }
    }
    return definition;
  }

  public static boolean hasArrayChecks(@Nonnull JsonSchemaObject schemaObject) {
    return schemaObject.isUniqueItems()
           || schemaObject.getContainsSchema() != null
           || schemaObject.getItemsSchema() != null
           || schemaObject.getItemsSchemaList() != null
           || schemaObject.getMinItems() != null
           || schemaObject.getMaxItems() != null;
  }

  public static boolean hasObjectChecks(@Nonnull JsonSchemaObject schemaObject) {
    return hasProperties(schemaObject)
           || schemaObject.getPropertyNamesSchema() != null
           || schemaObject.getPropertyDependencies() != null
           || schemaObject.hasPatternProperties()
           || schemaObject.getRequired() != null
           || schemaObject.getMinProperties() != null
           || schemaObject.getMaxProperties() != null;
  }

  public static boolean hasNumericChecks(@Nonnull JsonSchemaObject schemaObject) {
    return schemaObject.getMultipleOf() != null
           || schemaObject.getExclusiveMinimumNumber() != null
           || schemaObject.getExclusiveMaximumNumber() != null
           || schemaObject.getMaximum() != null
           || schemaObject.getMinimum() != null;
  }

  public static boolean hasStringChecks(@Nonnull JsonSchemaObject schemaObject) {
    return schemaObject.getPattern() != null || schemaObject.getFormat() != null;
  }

  public static @Nullable JsonSchemaType guessType(@Nonnull JsonSchemaObject schemaObject) {
    // if we have an explicit type, here we are
    JsonSchemaType type = schemaObject.getType();
    if (type != null) return type;

    // process type variants before heuristic type detection
    final Set<JsonSchemaType> typeVariants = schemaObject.getTypeVariants();
    if (typeVariants != null) {
      final int size = typeVariants.size();
      if (size == 1) {
        return typeVariants.iterator().next();
      }
      else if (size >= 2) {
        return null;
      }
    }

    // heuristic type detection based on the set of applied constraints
    boolean hasObjectChecks = hasObjectChecks(schemaObject);
    boolean hasNumericChecks = hasNumericChecks(schemaObject);
    boolean hasStringChecks = hasStringChecks(schemaObject);
    boolean hasArrayChecks = hasArrayChecks(schemaObject);

    if (hasObjectChecks && !hasNumericChecks && !hasStringChecks && !hasArrayChecks) {
      return JsonSchemaType._object;
    }
    if (!hasObjectChecks && hasNumericChecks && !hasStringChecks && !hasArrayChecks) {
      return JsonSchemaType._number;
    }
    if (!hasObjectChecks && !hasNumericChecks && hasStringChecks && !hasArrayChecks) {
      return JsonSchemaType._string;
    }
    if (!hasObjectChecks && !hasNumericChecks && !hasStringChecks && hasArrayChecks) {
      return JsonSchemaType._array;
    }
    return null;
  }

  public static @Nullable String getTypesDescription(boolean shortDesc, @Nullable Collection<JsonSchemaType> possibleTypes) {
    if (possibleTypes == null || possibleTypes.isEmpty()) return null;
    if (possibleTypes.size() == 1) return possibleTypes.iterator().next().getDescription();
    if (possibleTypes.contains(JsonSchemaType._any)) return JsonSchemaType._any.getDescription();

    Stream<String> typeDescriptions = possibleTypes.stream().map(t -> t.getDescription()).distinct().sorted();
    boolean isShort = false;
    if (shortDesc) {
      typeDescriptions = typeDescriptions.limit(3);
      if (possibleTypes.size() > 3) isShort = true;
    }
    return typeDescriptions.collect(Collectors.joining(" | ", "", isShort ? "| ..." : ""));
  }

  public static @Nullable String getTypeDescription(@Nonnull JsonSchemaObject schemaObject, boolean shortDesc) {
    JsonSchemaType type = schemaObject.getType();
    if (type != null) return type.getDescription();

    Set<JsonSchemaType> possibleTypes = schemaObject.getTypeVariants();

    String description = getTypesDescription(shortDesc, possibleTypes);
    if (description != null) return description;

    List<Object> anEnum = schemaObject.getEnum();
    if (anEnum != null) {
      return shortDesc ? "enum" : anEnum.stream().map(o -> o.toString()).collect(Collectors.joining(" | "));
    }

    JsonSchemaType guessedType = guessType(schemaObject);
    if (guessedType != null) {
      return guessedType.getDescription();
    }

    return null;
  }

  public static @Nullable JsonSchemaObject findRelativeDefinition(@Nonnull JsonSchemaObject schemaObject, @Nonnull String ref) {
    if (JsonPointerUtil.isSelfReference(ref)) {
      return schemaObject;
    }
    if (!ref.startsWith("#/")) {
      return null;
    }
    if (Registry.is("json.schema.object.v2") && !(schemaObject instanceof JsonSchemaObjectImpl)) {
      return schemaObject.findRelativeDefinition(ref);
    }
    ref = ref.substring(2);
    final List<String> parts = JsonPointerUtil.split(ref);
    JsonSchemaObject current = schemaObject;
    for (int i = 0; i < parts.size(); i++) {
      if (current == null) return null;
      final String part = parts.get(i);
      if (JSON_DEFINITIONS.equals(part) || DEFS.equals(part)) {
        if (i == (parts.size() - 1)) return null;
        //noinspection AssignmentToForLoopParameter
        final String nextPart = parts.get(++i);
        current = current.getDefinitionByName(JsonPointerUtil.unescapeJsonPointerPart(nextPart));
        continue;
      }
      if (JSON_PROPERTIES.equals(part)) {
        if (i == (parts.size() - 1)) return null;
        //noinspection AssignmentToForLoopParameter
        current = current.getPropertyByName(JsonPointerUtil.unescapeJsonPointerPart(parts.get(++i)));
        continue;
      }
      if (ITEMS.equals(part)) {
        if (i == (parts.size() - 1)) {
          current = current.getItemsSchema();
        }
        else {
          //noinspection AssignmentToForLoopParameter
          Integer next = tryParseInt(parts.get(++i));
          var itemsSchemaList = current.getItemsSchemaList();
          if (itemsSchemaList != null && next != null && next < itemsSchemaList.size()) {
            current = itemsSchemaList.get(next);
          }
        }
        continue;
      }
      if (ADDITIONAL_ITEMS.equals(part)) {
        if (i == (parts.size() - 1)) {
          current = current.getAdditionalItemsSchema();
        }
        continue;
      }

      current = current.getDefinitionByName(part);
    }
    return current;
  }

  private static @Nullable Integer tryParseInt(String s) {
    try {
      return Integer.parseInt(s);
    }
    catch (Exception __) {
      return null;
    }
  }

  public static boolean matchPattern(final @Nonnull Pattern pattern, final @Nonnull String s) {
    try {
      return pattern.matcher(StringUtil.newBombedCharSequence(s, 300)).matches();
    }
    catch (ProcessCanceledException e) {
      // something wrong with the pattern, infinite cycle?
      Logger.getInstance(JsonSchemaObjectReadingUtils.class).info("Pattern matching canceled");
      return false;
    }
    catch (Exception e) {
      // catch exceptions around to prevent things like:
      // https://bugs.openjdk.org/browse/JDK-6984178
      Logger.getInstance(JsonSchemaObjectReadingUtils.class).info(e);
      return false;
    }
  }

  public static Pair<Pattern, String> compilePattern(final @Nonnull String pattern) {
    try {
      return Pair.create(Pattern.compile(adaptSchemaPattern(pattern)), null);
    }
    catch (PatternSyntaxException e) {
      return Pair.create(null, e.getMessage());
    }
  }

  private static @Nonnull String adaptSchemaPattern(String pattern) {
    pattern = pattern.startsWith("^") || pattern.startsWith("*") || pattern.startsWith(".") ? pattern : (".*" + pattern);
    pattern = pattern.endsWith("+") || pattern.endsWith("*") || pattern.endsWith("$") ? pattern : (pattern + ".*");
    pattern = pattern.replace("\\\\", "\\");
    return pattern;
  }
}
