package com.jetbrains.jsonSchema;

import jakarta.annotation.Nonnull;

import java.util.List;

public class JsonSchemaMetadataEntry {
    private final String key;
    private final List<String> values;

    public JsonSchemaMetadataEntry(@Nonnull String key, @Nonnull List<String> values) {
        this.key = key;
        this.values = values;
    }

    @Nonnull
    public String getKey() {
        return key;
    }

    @Nonnull
    public List<String> getValues() {
        return values;
    }
}
