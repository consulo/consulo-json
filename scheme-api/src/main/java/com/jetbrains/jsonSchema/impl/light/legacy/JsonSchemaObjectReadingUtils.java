// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema.impl.light.legacy;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.impl.light.nodes.EmptyJsonSchemaObject;
import com.jetbrains.jsonSchema.internal.JsonFileResolver;
import com.jetbrains.jsonSchema.internal.JsonSchemaVariantsTreeBuilder;
import com.jetbrains.jsonSchema.internal.RootJsonSchemaObject;
import consulo.annotation.DeprecationInfo;
import consulo.application.util.BombedStringUtil;
import consulo.component.ProcessCanceledException;
import consulo.logging.Logger;
import consulo.util.collection.FactoryMap;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.intellij.json.pointer.JsonPointerUtil.isSelfReference;

public final class JsonSchemaObjectReadingUtils {
    private static final Logger LOG = Logger.getInstance(JsonSchemaObjectReadingUtils.class);
    public static final JsonSchemaObject NULL_OBJ = EmptyJsonSchemaObject.INSTANCE;

    public static boolean hasProperties(JsonSchemaObject schemaObject) {
        return schemaObject.getPropertyNames().hasNext();
    }

    /**
     * @deprecated Use {@link JsonSchemaObject#resolveRefSchema}
     */
    @Deprecated
    @DeprecationInfo("Use JsonSchemaObject#resolveRefSchema")
    public static @Nullable JsonSchemaObject resolveRefSchema(JsonSchemaObject schemaNode, JsonSchemaService service) {
        final String ref = schemaNode.getRef();
        assert !StringUtil.isEmptyOrSpaces(ref);

        JsonSchemaObject value = fetchSchemaFromRefDefinition(ref, schemaNode, service, schemaNode.isRefRecursive());
        if (!JsonFileResolver.isHttpPath(ref)) {
            service.registerReference(ref);
        }
        else if (value != null) {
            VirtualFile virtualFile = service.resolveSchemaFile(value);
            if (virtualFile != null) {
                service.registerReference(virtualFile.getName());
            }
        }
        return value;
    }

    private static final Map<String, JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter> complexReferenceCache
        = FactoryMap.create((key) -> new JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter(key));

    public static @Nullable JsonSchemaObject fetchSchemaFromRefDefinition(String ref,
                                                                          final JsonSchemaObject schema,
                                                                          JsonSchemaService service,
                                                                          boolean recursive) {

        final VirtualFile schemaFile = service.resolveSchemaFile(schema);
        if (schemaFile == null) {
            return null;
        }
        final JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter splitter = Objects.requireNonNull(complexReferenceCache.get(ref));
        String schemaId = splitter.getSchemaId();
        if (schemaId != null) {
            JsonSchemaObject refSchema = resolveSchemaByReference(service, schemaFile, schemaId);
            if (refSchema == null || refSchema == NULL_OBJ) {
                return null;
            }
            return findRelativeDefinition(refSchema, splitter, service);
        }
        JsonSchemaObject rootSchema = service.getSchemaObjectForSchemaFile(schemaFile);
        if (rootSchema == null) {
            LOG.debug(String.format("Schema object not found for %s", schemaFile.getPath()));
            return null;
        }
        return findRelativeDefinition(rootSchema, splitter, service);
    }

    private static @Nullable JsonSchemaObject resolveSchemaByReference(JsonSchemaService service,
                                                                       VirtualFile schemaFile,
                                                                       String schemaId) {
        final VirtualFile refFile = service.findSchemaFileByReference(schemaId, schemaFile);
        if (refFile == null) {
            LOG.debug(String.format("Schema file not found by reference: '%s' from %s", schemaId, schemaFile.getPath()));
            return null;
        }
        JsonSchemaObject refSchema = downloadAndParseRemoteSchema(service, refFile);
        if (refSchema == null) {
            LOG.debug(String.format("Schema object not found by reference: '%s' from %s", schemaId, schemaFile.getPath()));
        }
        return refSchema;
    }

    public static @Nullable JsonSchemaObject downloadAndParseRemoteSchema(JsonSchemaService service, VirtualFile refFile) {
        return service.getSchemaObjectForSchemaFile(refFile);
    }

    public static @Nullable JsonSchemaObject findRelativeDefinition(final JsonSchemaObject schema,
                                                                    final JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter splitter,
                                                                    JsonSchemaService service) {
        final String path = splitter.getRelativePath();
        if (StringUtil.isEmptyOrSpaces(path)) {
            final String id = splitter.getSchemaId();
            if (isSelfReference(id)) {
                return schema;
            }
            if (id != null && id.startsWith("#")) {
                JsonSchemaObject rootSchemaObject = schema.getRootSchemaObject();
                if (rootSchemaObject instanceof RootJsonSchemaObject<?, ?> explicitRootSchemaObject) {
                    final String resolvedId = explicitRootSchemaObject.resolveId(id);
                    if (resolvedId == null || id.equals("#" + resolvedId)) {
                        return null;
                    }
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

    public static boolean hasArrayChecks(JsonSchemaObject schemaObject) {
        return schemaObject.isUniqueItems()
            || schemaObject.getContainsSchema() != null
            || schemaObject.getItemsSchema() != null
            || schemaObject.getItemsSchemaList() != null
            || schemaObject.getMinItems() != null
            || schemaObject.getMaxItems() != null;
    }

    public static boolean hasObjectChecks(JsonSchemaObject schemaObject) {
        return hasProperties(schemaObject)
            || schemaObject.getPropertyNamesSchema() != null
            || schemaObject.getPropertyDependencies() != null
            || schemaObject.hasPatternProperties()
            || schemaObject.getRequired() != null
            || schemaObject.getMinProperties() != null
            || schemaObject.getMaxProperties() != null;
    }

    public static boolean hasNumericChecks(JsonSchemaObject schemaObject) {
        return schemaObject.getMultipleOf() != null
            || schemaObject.getExclusiveMinimumNumber() != null
            || schemaObject.getExclusiveMaximumNumber() != null
            || schemaObject.getMaximum() != null
            || schemaObject.getMinimum() != null;
    }

    public static boolean hasStringChecks(JsonSchemaObject schemaObject) {
        return schemaObject.getPattern() != null || schemaObject.getFormat() != null;
    }

    public static @Nullable JsonSchemaType guessType(JsonSchemaObject schemaObject) {
        // if we have an explicit type, here we are
        JsonSchemaType type = schemaObject.getType();
        if (type != null) {
            return type;
        }

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
        if (possibleTypes == null || possibleTypes.isEmpty()) {
            return null;
        }
        if (possibleTypes.size() == 1) {
            return possibleTypes.iterator().next().getDescription();
        }
        if (possibleTypes.contains(JsonSchemaType._any)) {
            return JsonSchemaType._any.getDescription();
        }

        Stream<String> typeDescriptions = possibleTypes.stream().map(t -> t.getDescription()).distinct().sorted();
        boolean isShort = false;
        if (shortDesc) {
            typeDescriptions = typeDescriptions.limit(3);
            if (possibleTypes.size() > 3) {
                isShort = true;
            }
        }
        return typeDescriptions.collect(Collectors.joining(" | ", "", isShort ? "| ..." : ""));
    }

    public static @Nullable String getTypeDescription(JsonSchemaObject schemaObject, boolean shortDesc) {
        JsonSchemaType type = schemaObject.getType();
        if (type != null) {
            return type.getDescription();
        }

        Set<JsonSchemaType> possibleTypes = schemaObject.getTypeVariants();

        String description = getTypesDescription(shortDesc, possibleTypes);
        if (description != null) {
            return description;
        }

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

    public static @Nullable JsonSchemaObject findRelativeDefinition(JsonSchemaObject schemaObject, String ref) {
        if (isSelfReference(ref)) {
            return schemaObject;
        }
        if (!ref.startsWith("#/")) {
            return null;
        }
        return schemaObject.findRelativeDefinition(ref);
    }

    private static @Nullable Integer tryParseInt(String s) {
        try {
            return Integer.parseInt(s);
        }
        catch (Exception ignored) {
            return null;
        }
    }

    public static boolean matchPattern(final Pattern pattern, final String s) {
        try {
            return pattern.matcher(BombedStringUtil.newBombedCharSequence(s, 300)).matches();
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

    public static Pair<@Nullable Pattern, @Nullable String> compilePattern(final String pattern) {
        try {
            return Pair.create(Pattern.compile(adaptSchemaPattern(pattern)), null);
        }
        catch (PatternSyntaxException e) {
            return Pair.create(null, e.getMessage());
        }
    }

    private static String adaptSchemaPattern(String pattern) {
        String adapted = pattern.startsWith("^") || pattern.startsWith("*") || pattern.startsWith(".") ? pattern : (".*" + pattern);
        adapted = adapted.endsWith("+") || adapted.endsWith("*") || adapted.endsWith("$") ? adapted : (adapted + ".*");
        adapted = adapted.replace("\\\\", "\\");
        return adapted;
    }
}
