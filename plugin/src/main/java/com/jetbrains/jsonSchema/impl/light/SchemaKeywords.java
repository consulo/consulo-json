// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light;

import java.util.Set;

public class SchemaKeywords {
  public static final String SCHEMA_ROOT_POINTER = "/";
  public static final String DEPRECATION = "deprecationMessage";
  public static final String LANGUAGE = "language";
  public static final String PREFIX = "prefix";
  public static final String SUFFIX = "suffix";
  public static final String TYPE = "type";
  public static final String MULTIPLE_OF = "multipleOf";
  public static final String MAXIMUM = "maximum";
  public static final String MINIMUM = "minimum";
  public static final String EXCLUSIVE_MAXIMUM = "exclusiveMaximum";
  public static final String EXCLUSIVE_MINIMUM = "exclusiveMinimum";
  public static final String MAX_LENGTH = "maxLength";
  public static final String MIN_LENGTH = "minLength";
  public static final String PATTERN = "pattern";
  public static final String ADDITIONAL_PROPERTIES = "additionalProperties";
  public static final String PROPERTY_NAMES = "propertyNames";
  public static final String ADDITIONAL_ITEMS = "additionalItems";
  public static final String PREFIX_ITEMS = "prefixItems";
  public static final String ITEMS = "items";
  public static final String CONTAINS = "contains";
  public static final String MAX_ITEMS = "maxItems";
  public static final String MIN_ITEMS = "minItems";
  public static final String UNIQUE_ITEMS = "uniqueItems";
  public static final String MAX_PROPERTIES = "maxProperties";
  public static final String MIN_PROPERTIES = "minProperties";
  public static final String REQUIRED = "required";
  public static final String REF = "$ref";
  public static final String DYNAMIC_REF = "$dynamicRef";
  public static final String DYNAMIC_ANCHOR = "$dynamicAnchor";
  public static final String RECURSIVE_REF = "$recursiveRef";
  public static final String RECURSIVE_ANCHOR = "$recursiveAnchor";
  public static final String VOCABULARY = "$vocabulary";
  public static final String DEFAULT = "default";
  public static final String FORMAT = "format";
  public static final String ANCHOR = "$anchor";
  public static final String DESCRIPTION = "description";
  public static final String TITLE = "title";
  public static final String PATTERN_PROPERTIES = "patternProperties";
  public static final String DEPENDENCIES = "dependencies";
  public static final String DEPENDENT_SCHEMAS = "dependentSchemas";
  public static final String DEPENDENT_REQUIRED = "dependentRequired";
  public static final String ENUM = "enum";
  public static final String CONST = "const";
  public static final String ALL_OF = "allOf";
  public static final String ANY_OF = "anyOf";
  public static final String ONE_OF = "oneOf";
  public static final String NOT = "not";
  public static final String IF = "if";
  public static final String THEN = "then";
  public static final String ELSE = "else";
  public static final String DEFS = "$defs";
  public static final String EXAMPLE = "example";
  public static final String JSON_ID = "id";
  public static final String JSON_DOLLAR_ID = "$id";
  public static final String UNEVALUATED_ITEMS = "unevaluatedItems";
  public static final String UNEVALUATED_PROPERTIES = "unevaluatedProperties";

  public static final String SCHEMA_KEYWORD_INVARIANT = "$schema";

  public static final String INSTANCE_OF = "instanceof";
  public static final String TYPE_OF = "typeof";
  public static final String JSON_DEFINITIONS = "definitions";
  public static final String JSON_PROPERTIES = "properties";
  public static final String X_INTELLIJ_HTML_DESCRIPTION = "x-intellij-html-description";
  public static final String X_INTELLIJ_LANGUAGE_INJECTION = "x-intellij-language-injection";
  public static final String X_INTELLIJ_CASE_INSENSITIVE = "x-intellij-case-insensitive";
  public static final String X_INTELLIJ_ENUM_METADATA = "x-intellij-enum-metadata";
  public static final String X_INTELLIJ_ENUM_ORDER_SENSITIVE = "x-intellij-enum-order-sensitive";
  public static final String X_INTELLIJ_METADATA = "x-intellij-metadata";

  public static final Set<String> ROOT_POINTER_VARIANTS = Set.of(SCHEMA_ROOT_POINTER, "#/", "#", "");
}
