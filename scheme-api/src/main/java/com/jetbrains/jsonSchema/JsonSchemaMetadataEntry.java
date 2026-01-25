package com.jetbrains.jsonSchema;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class JsonSchemaMetadataEntry {
    private final String key;
    private final List<String> values;

    public JsonSchemaMetadataEntry(@NotNull String key, @NotNull List<String> values) {
        this.key = key;
        this.values = values;
    }

    @NotNull
    public String getKey() {
        return key;
    }

    @NotNull
    public List<String> getValues() {
        return values;
    }
}
