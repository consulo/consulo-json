// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.versions.v201909;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.light.JsonSchemaRefResolver;
import com.jetbrains.jsonSchema.impl.light.SchemaKeywords;
import com.jetbrains.jsonSchema.impl.light.versions.JsonSchemaInterpretationStrategy;
import com.jetbrains.jsonSchema.impl.validations.JsonSchemaValidationsCollector;
import jakarta.annotation.Nullable;

import java.util.Arrays;

public class JsonSchema201909Strategy implements JsonSchemaInterpretationStrategy {
    public static final JsonSchema201909Strategy INSTANCE = new JsonSchema201909Strategy();

    private JsonSchema201909Strategy() {
    }

    @Override
    public String getIdKeyword() {
        return SchemaKeywords.JSON_DOLLAR_ID;
    }

    @Override
    public String getNonPositionalItemsKeyword() {
        return SchemaKeywords.ADDITIONAL_ITEMS;
    }

    @Override
    public String getPositionalItemsKeyword() {
        return SchemaKeywords.ITEMS;
    }

    @Override
    public String getDefinitionsKeyword() {
        return SchemaKeywords.DEFS;
    }

    @Override
    public String getDynamicReferenceKeyword() {
        return SchemaKeywords.RECURSIVE_REF;
    }

    @Override
    public String getDynamicAnchorKeyword() {
        return SchemaKeywords.RECURSIVE_ANCHOR;
    }

    @Override
    public String getDependencySchemasKeyword() {
        return SchemaKeywords.DEPENDENT_SCHEMAS;
    }

    @Override
    public String getPropertyDependenciesKeyword() {
        return SchemaKeywords.DEPENDENT_REQUIRED;
    }

    @Override
    public String getUnevaluatedItemsKeyword() {
        return SchemaKeywords.UNEVALUATED_ITEMS;
    }

    @Override
    public String getUnevaluatedPropertiesKeyword() {
        return SchemaKeywords.UNEVALUATED_PROPERTIES;
    }

    @Override
    public Iterable<JsonSchemaRefResolver> getReferenceResolvers() {
        return Arrays.asList(
            RecursiveRefResolver.INSTANCE,
            LocalSchemaReferenceResolver.INSTANCE,
            RemoteSchemaReferenceResolver.INSTANCE,
            Vocabulary2019Resolver.INSTANCE
        );
    }

    @Override
    public Iterable<JsonSchemaValidation> getValidations(JsonSchemaObject schemaNode, @Nullable JsonSchemaType type, JsonValueAdapter value) {
        return JsonSchemaValidationsCollector.getSchema7AndEarlierValidations(schemaNode, type, value);
    }
}
