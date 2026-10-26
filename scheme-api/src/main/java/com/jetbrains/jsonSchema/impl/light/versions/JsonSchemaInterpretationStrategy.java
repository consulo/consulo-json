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
package com.jetbrains.jsonSchema.impl.light.versions;

import com.jetbrains.jsonSchema.IfThenElse;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.JsonSchemaVersion;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.light.JsonSchemaRefLoader;
import com.jetbrains.jsonSchema.impl.light.nodes.InheritedJsonSchemaObjectView;
import com.jetbrains.jsonSchema.impl.light.versions.v201909.JsonSchema201909Strategy;
import com.jetbrains.jsonSchema.impl.light.versions.v202012.JsonSchema202012Strategy;
import com.jetbrains.jsonSchema.internal.MergedJsonSchemaObject;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.ADDITIONAL_PROPERTIES;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.ALL_OF;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.ANCHOR;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.ANY_OF;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.CONST;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.CONTAINS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.DEFAULT;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.DEPRECATION;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.DESCRIPTION;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.ELSE;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.ENUM;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.EXAMPLE;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.EXCLUSIVE_MAXIMUM;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.EXCLUSIVE_MINIMUM;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.FORMAT;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.IF;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.ITEMS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.JSON_PROPERTIES;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.MAXIMUM;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.MAX_ITEMS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.MAX_LENGTH;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.MAX_PROPERTIES;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.MINIMUM;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.MIN_ITEMS;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.MIN_LENGTH;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.MIN_PROPERTIES;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.MULTIPLE_OF;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.NOT;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.ONE_OF;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.PATTERN;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.PATTERN_PROPERTIES;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.PROPERTY_NAMES;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.REF;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.REQUIRED;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.THEN;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.TITLE;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.TYPE;
import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.UNIQUE_ITEMS;

/**
 * This interface represents a strategy for interpreting various JSON schema versions.
 * <p>
 * Each keyword in the JSON schema is associated with a property in this interface.
 * The property returns the keyword value or null if the keyword is not supported by a particular schema version.
 * <p>
 * Different behavior patterns might be represented by custom interfaces with several available implementations,
 * e.g., there might be several reference loaders or node validators.
 * <p>
 * IMPORTANT: This class is intentionally marked as internal,
 * thus it might be easily changed and adapted to the new json schema releases without breaking the public API.
 */
public interface JsonSchemaInterpretationStrategy {
    List<String> IF_ELSE_MARKERS = List.of(IF, THEN, ELSE);
    List<String> APPLICATOR_MARKERS = List.of(ONE_OF, ALL_OF, ANY_OF);

    static JsonSchemaInterpretationStrategy computeJsonSchemaVersion(@Nullable String schemaFieldValue) {
        JsonSchemaVersion version = JsonSchemaVersion.byId(schemaFieldValue != null ? schemaFieldValue : "");
        if (version == null) {
            return JsonSchema6AndEarlierStrategy.INSTANCE;
        }
        return switch (version) {
            case SCHEMA_2020_12 -> JsonSchema202012Strategy.INSTANCE;
            case SCHEMA_2019_09 -> JsonSchema201909Strategy.INSTANCE;
            case SCHEMA_7 -> JsonSchema7Strategy.INSTANCE;
            default -> JsonSchema6AndEarlierStrategy.INSTANCE;
        };
    }

    default @Nullable String getConstKeyword() {
        return CONST;
    }

    default @Nullable String getExampleKeyword() {
        return EXAMPLE;
    }

    default @Nullable String getDeprecationKeyword() {
        return DEPRECATION;
    }

    default @Nullable String getTypeKeyword() {
        return TYPE;
    }

    default @Nullable String getMultipleOfKeyword() {
        return MULTIPLE_OF;
    }

    default @Nullable String getMaximumKeyword() {
        return MAXIMUM;
    }

    default @Nullable String getExclusiveMaximumKeyword() {
        return EXCLUSIVE_MAXIMUM;
    }

    default @Nullable String getMinimumKeyword() {
        return MINIMUM;
    }

    default @Nullable String getExclusiveMinimumKeyword() {
        return EXCLUSIVE_MINIMUM;
    }

    default @Nullable String getMaxLengthKeyword() {
        return MAX_LENGTH;
    }

    default @Nullable String getMinLengthKeyword() {
        return MIN_LENGTH;
    }

    default @Nullable String getPatternKeyword() {
        return PATTERN;
    }

    default @Nullable String getAdditionalPropertiesKeyword() {
        return ADDITIONAL_PROPERTIES;
    }

    default @Nullable String getPropertyNamesKeyword() {
        return PROPERTY_NAMES;
    }

    default @Nullable String getMaxItemsKeyword() {
        return MAX_ITEMS;
    }

    default @Nullable String getMinItemsKeyword() {
        return MIN_ITEMS;
    }

    default @Nullable String getUniqueItemsKeyword() {
        return UNIQUE_ITEMS;
    }

    default @Nullable String getMaxPropertiesKeyword() {
        return MAX_PROPERTIES;
    }

    default @Nullable String getMinPropertiesKeyword() {
        return MIN_PROPERTIES;
    }

    default @Nullable String getRequiredKeyword() {
        return REQUIRED;
    }

    default @Nullable String getReferenceKeyword() {
        return REF;
    }

    default @Nullable String getDefaultKeyword() {
        return DEFAULT;
    }

    default @Nullable String getFormatKeyword() {
        return FORMAT;
    }

    default @Nullable String getAnchorKeyword() {
        return ANCHOR;
    }

    default @Nullable String getDescriptionKeyword() {
        return DESCRIPTION;
    }

    default @Nullable String getTitleKeyword() {
        return TITLE;
    }

    default @Nullable String getEnumKeyword() {
        return ENUM;
    }

    default @Nullable String getAllOfKeyword() {
        return ALL_OF;
    }

    default @Nullable String getAnyOfKeyword() {
        return ANY_OF;
    }

    default @Nullable String getOneOfKeyword() {
        return ONE_OF;
    }

    default @Nullable String getIfKeyword() {
        return IF;
    }

    default @Nullable String getThenKeyword() {
        return THEN;
    }

    default @Nullable String getElseKeyword() {
        return ELSE;
    }

    default @Nullable String getNotKeyword() {
        return NOT;
    }

    default @Nullable String getPropertiesKeyword() {
        return JSON_PROPERTIES;
    }

    default @Nullable String getPatternPropertiesKeyword() {
        return PATTERN_PROPERTIES;
    }

    default @Nullable String getItemsSchemaKeyword() {
        return ITEMS;
    }

    default @Nullable String getContainsKeyword() {
        return CONTAINS;
    }

    @Nullable
    String getPropertyDependenciesKeyword();

    @Nullable
    String getDependencySchemasKeyword();

    @Nullable
    String getIdKeyword();

    @Nullable
    String getNonPositionalItemsKeyword();

    @Nullable
    String getPositionalItemsKeyword();

    @Nullable
    String getDefinitionsKeyword();

    @Nullable
    String getDynamicReferenceKeyword();

    @Nullable
    String getDynamicAnchorKeyword();

    @Nullable
    String getUnevaluatedItemsKeyword();

    @Nullable
    String getUnevaluatedPropertiesKeyword();

    List<JsonSchemaRefLoader> getRefLoaders();

    Stream<JsonSchemaValidation> getValidations(JsonSchemaObject schemaNode, @Nullable JsonSchemaType type, JsonValueAdapter value);

    default JsonSchemaObject inheritBaseSchema(JsonSchemaObject baseSchema, JsonSchemaObject childSchema) {
        if (baseSchema.equals(childSchema)) {
            return childSchema;
        }
        else if (doesAlreadyInheritAnything(baseSchema)) {
            return new InheritedJsonSchemaObjectView(baseSchema, childSchema);
        }
        else if (isIfThenElseBranchWithNonEmptyParent(baseSchema, childSchema)) {
            return new InheritedJsonSchemaObjectView(baseSchema, childSchema);
        }
        else if (isApplicatorBranchWithNonEmptyParent(baseSchema, childSchema)) {
            return new InheritedJsonSchemaObjectView(baseSchema, childSchema);
        }
        else if (!isSameSchemaFileNodes(baseSchema, childSchema)) {
            return new InheritedJsonSchemaObjectView(baseSchema, childSchema);
        }
        else {
            return childSchema;
        }
    }

    default boolean doesAlreadyInheritAnything(JsonSchemaObject jsonSchemaObject) {
        return jsonSchemaObject instanceof MergedJsonSchemaObject mergedJsonSchemaObject && mergedJsonSchemaObject.isInherited();
    }

    private boolean isSameSchemaFileNodes(JsonSchemaObject baseSchema, JsonSchemaObject childSchema) {
        String baseFileUrl = baseSchema.getFileUrl();
        return baseFileUrl == null
            || !baseFileUrl.isBlank() && baseFileUrl.equals(childSchema.getFileUrl())
            || baseSchema.getRawFile() != null && Objects.equals(baseSchema.getRawFile(), childSchema.getRawFile());
    }

    private boolean isApplicatorBranchWithNonEmptyParent(JsonSchemaObject baseSchema, JsonSchemaObject childSchema) {
        if (!baseSchema.hasChildFieldsExcept(APPLICATOR_MARKERS)) {
            return false;
        }

        return Stream.of(baseSchema.getOneOf(), baseSchema.getAnyOf(), baseSchema.getAllOf())
            .filter(Objects::nonNull)
            .flatMap(list -> list.stream())
            .anyMatch(it -> it.equals(childSchema));
    }

    private boolean isIfThenElseBranchWithNonEmptyParent(JsonSchemaObject baseSchema, JsonSchemaObject childSchema) {
        if (!baseSchema.hasChildFieldsExcept(IF_ELSE_MARKERS)) {
            return false;
        }

        List<IfThenElse> ifThenElse = baseSchema.getIfThenElse();
        if (ifThenElse == null) {
            return false;
        }
        return ifThenElse.stream().anyMatch(condition -> childSchema.equals(condition.getThen()) || childSchema.equals(condition.getElse()));
    }
}
