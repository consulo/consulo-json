// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import com.jetbrains.jsonSchema.IfThenElse;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.JsonSchemaVersion;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.List;

import static com.jetbrains.jsonSchema.internal.SchemaKeywords.*;

/**
 * This interface represents a strategy for interpreting various JSON schema versions.
 * <p>
 * Each keyword in the JSON schema is associated with a property in this interface.
 * The property returns the keyword value or null if the keyword is not supported by a particular schema version.
 * <p>
 * Different behavior patterns might be represented by custom interfaces with several available implementations,
 * e.g., there might be several reference resolvers or node validators.
 * <p>
 * IMPORTANT: This class is intentionally marked as internal,
 * thus it might be easily changed and adapted to the new json schema releases without breaking the public API.
 */
public interface JsonSchemaInterpretationStrategy {
    List<String> IF_ELSE_MARKERS = Arrays.asList(IF, THEN, ELSE);
    List<String> APPLICATOR_MARKERS = Arrays.asList(ONE_OF, ALL_OF, ANY_OF);

    @Nullable
    default String getConstKeyword() {
        return CONST;
    }

    @Nullable
    default String getExampleKeyword() {
        return EXAMPLE;
    }

    @Nullable
    default String getDeprecationKeyword() {
        return DEPRECATION;
    }

    @Nullable
    default String getTypeKeyword() {
        return TYPE;
    }

    @Nullable
    default String getMultipleOfKeyword() {
        return MULTIPLE_OF;
    }

    @Nullable
    default String getMaximumKeyword() {
        return MAXIMUM;
    }

    @Nullable
    default String getExclusiveMaximumKeyword() {
        return EXCLUSIVE_MAXIMUM;
    }

    @Nullable
    default String getMinimumKeyword() {
        return MINIMUM;
    }

    @Nullable
    default String getExclusiveMinimumKeyword() {
        return EXCLUSIVE_MINIMUM;
    }

    @Nullable
    default String getMaxLengthKeyword() {
        return MAX_LENGTH;
    }

    @Nullable
    default String getMinLengthKeyword() {
        return MIN_LENGTH;
    }

    @Nullable
    default String getPatternKeyword() {
        return PATTERN;
    }

    @Nullable
    default String getAdditionalPropertiesKeyword() {
        return ADDITIONAL_PROPERTIES;
    }

    @Nullable
    default String getPropertyNamesKeyword() {
        return PROPERTY_NAMES;
    }

    @Nullable
    default String getMaxItemsKeyword() {
        return MAX_ITEMS;
    }

    @Nullable
    default String getMinItemsKeyword() {
        return MIN_ITEMS;
    }

    @Nullable
    default String getUniqueItemsKeyword() {
        return UNIQUE_ITEMS;
    }

    @Nullable
    default String getMaxPropertiesKeyword() {
        return MAX_PROPERTIES;
    }

    @Nullable
    default String getMinPropertiesKeyword() {
        return MIN_PROPERTIES;
    }

    @Nullable
    default String getRequiredKeyword() {
        return REQUIRED;
    }

    @Nullable
    default String getReferenceKeyword() {
        return REF;
    }

    @Nullable
    default String getDefaultKeyword() {
        return DEFAULT;
    }

    @Nullable
    default String getFormatKeyword() {
        return FORMAT;
    }

    @Nullable
    default String getAnchorKeyword() {
        return ANCHOR;
    }

    @Nullable
    default String getDescriptionKeyword() {
        return DESCRIPTION;
    }

    @Nullable
    default String getTitleKeyword() {
        return TITLE;
    }

    @Nullable
    default String getEnumKeyword() {
        return ENUM;
    }

    @Nullable
    default String getAllOfKeyword() {
        return ALL_OF;
    }

    @Nullable
    default String getAnyOfKeyword() {
        return ANY_OF;
    }

    @Nullable
    default String getOneOfKeyword() {
        return ONE_OF;
    }

    @Nullable
    default String getIfKeyword() {
        return IF;
    }

    @Nullable
    default String getThenKeyword() {
        return THEN;
    }

    @Nullable
    default String getElseKeyword() {
        return ELSE;
    }

    @Nullable
    default String getNotKeyword() {
        return NOT;
    }

    @Nullable
    default String getPropertiesKeyword() {
        return JSON_PROPERTIES;
    }

    @Nullable
    default String getPatternPropertiesKeyword() {
        return PATTERN_PROPERTIES;
    }

    @Nullable
    default String getItemsSchemaKeyword() {
        return ITEMS;
    }

    @Nullable
    default String getContainsKeyword() {
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

    Iterable<JsonSchemaRefResolver> getReferenceResolvers();

    Iterable<JsonSchemaValidation> getValidations(JsonSchemaObject schemaNode, @Nullable JsonSchemaType type, JsonValueAdapter value);

    default JsonSchemaObject inheritBaseSchema(JsonSchemaObject baseSchema, JsonSchemaObject childSchema) {
        if (baseSchema == childSchema) {
            return childSchema;
        }
        if (doesAlreadyInheritAnything(baseSchema)) {
            return new InheritedJsonSchemaObjectView(baseSchema, childSchema);
        }
        if (isIfThenElseBranchWithNonEmptyParent(baseSchema, childSchema)) {
            return new InheritedJsonSchemaObjectView(baseSchema, childSchema);
        }
        if (isApplicatorBranchWithNonEmptyParent(baseSchema, childSchema)) {
            return new InheritedJsonSchemaObjectView(baseSchema, childSchema);
        }
        if (!isSameSchemaFileNodes(baseSchema, childSchema)) {
            return new InheritedJsonSchemaObjectView(baseSchema, childSchema);
        }
        return childSchema;
    }

    default boolean doesAlreadyInheritAnything(JsonSchemaObject jsonSchemaObject) {
        return jsonSchemaObject instanceof MergedJsonSchemaObject && ((MergedJsonSchemaObject) jsonSchemaObject).isInherited();
    }

    default boolean isSameSchemaFileNodes(JsonSchemaObject baseSchema, JsonSchemaObject childSchema) {
        String baseFileUrl = baseSchema.getFileUrl();
        String childFileUrl = childSchema.getFileUrl();

        return baseFileUrl == null
            || (baseFileUrl != null && !baseFileUrl.isBlank() && baseFileUrl.equals(childFileUrl))
            || (baseSchema.getRawFile() != null && baseSchema.getRawFile().equals(childSchema.getRawFile()));
    }

    default boolean isApplicatorBranchWithNonEmptyParent(JsonSchemaObject baseSchema, JsonSchemaObject childSchema) {
        if (!baseSchema.hasChildFieldsExcept(APPLICATOR_MARKERS)) {
            return false;
        }

        List<? extends JsonSchemaObject> oneOf = baseSchema.getOneOf();
        if (oneOf != null) {
            for (JsonSchemaObject schema : oneOf) {
                if (schema == childSchema) {
                    return true;
                }
            }
        }

        List<? extends JsonSchemaObject> anyOf = baseSchema.getAnyOf();
        if (anyOf != null) {
            for (JsonSchemaObject schema : anyOf) {
                if (schema == childSchema) {
                    return true;
                }
            }
        }

        List<? extends JsonSchemaObject> allOf = baseSchema.getAllOf();
        if (allOf != null) {
            for (JsonSchemaObject schema : allOf) {
                if (schema == childSchema) {
                    return true;
                }
            }
        }

        return false;
    }

    default boolean isIfThenElseBranchWithNonEmptyParent(JsonSchemaObject baseSchema, JsonSchemaObject childSchema) {
        if (!baseSchema.hasChildFieldsExcept(IF_ELSE_MARKERS)) {
            return false;
        }

        List<IfThenElse> ifThenElse = baseSchema.getIfThenElse();
        if (ifThenElse == null) {
            return false;
        }

        for (IfThenElse condition : ifThenElse) {
            if (condition.getThen() == childSchema || condition.getElse() == childSchema) {
                return true;
            }
        }

        return false;
    }

    static JsonSchemaInterpretationStrategy computeJsonSchemaVersion(@Nullable String schemaFieldValue) {
        JsonSchemaVersion version = JsonSchemaVersion.byId(schemaFieldValue != null ? schemaFieldValue : "");
        switch (version) {
            case SCHEMA_2020_12:
                return JsonSchema202012Strategy.INSTANCE;
            case SCHEMA_2019_09:
                return JsonSchema201909Strategy.INSTANCE;
            case SCHEMA_7:
                return JsonSchema7Strategy.INSTANCE;
            default:
                return JsonSchema6AndEarlierStrategy.INSTANCE;
        }
    }
}
