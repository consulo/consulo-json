package com.jetbrains.jsonSchema.impl.light.nodes;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;

class JsonSchemaObjectStorageKt {
    private static final JsonMapper JSON5_OBJECT_MAPPER = JsonMapper.builder(
            JsonFactory.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                .enable(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES)
                .enable(JsonReadFeature.ALLOW_MISSING_VALUES)
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .enable(JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)
                .enable(JsonReadFeature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER)
                .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
                .build()
        )
        .enable(JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION)
        .build();

    private static final ObjectMapper YAML_OBJECT_MAPPER = new ObjectMapper(
        YAMLFactory.builder()
            .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
            .build()
    );

    public static JsonMapper getJson5ObjectMapper() {
        return JSON5_OBJECT_MAPPER;
    }

    public static ObjectMapper getYamlObjectMapper() {
        return YAML_OBJECT_MAPPER;
    }
}
