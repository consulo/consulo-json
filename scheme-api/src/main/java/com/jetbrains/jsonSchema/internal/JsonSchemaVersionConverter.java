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
package com.jetbrains.jsonSchema.internal;

import com.jetbrains.jsonSchema.JsonSchemaVersion;
import consulo.json.localize.JsonLocalize;
import consulo.util.xml.serializer.Converter;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// This converter exists because a long time ago the JsonSchemaVersion class was directly used in the UI state persisting,
// and the version's identity was computed simply by calling toString() method.
// Important pitfall here is that the toString() method uses language-dependent values from the message bundle,
// so it's crucial to restore values using the same bundle messages as before :(
public class JsonSchemaVersionConverter extends Converter<JsonSchemaVersion> {
    private static final Pattern GROUPING_SEPARATORS = Pattern.compile("[\\s\\u00A0\\u202F\\u2019',.]");

    @Override
    public @Nullable JsonSchemaVersion fromString(String value) {
        return findSuitableVersion(value);
    }

    @Override
    public String toString(JsonSchemaVersion value) {
        return value.toString();
    }

    private JsonSchemaVersion findSuitableVersion(String effectiveSerialisedValue) {
        return Arrays.stream(JsonSchemaVersion.values())
            .filter(version -> canBeSerializedInto(version, effectiveSerialisedValue))
            .findFirst()
            .orElse(JsonSchemaVersion.SCHEMA_4);
    }

    private boolean canBeSerializedInto(JsonSchemaVersion version, String effectiveSerialisedValue) {
        String normalizedValue = normalizeGroupingSeparators(effectiveSerialisedValue);
        return getPossibleSerializedValues(version).anyMatch(possibleValue ->
            possibleValue.equals(effectiveSerialisedValue) || normalizeGroupingSeparators(possibleValue).equals(normalizedValue));
    }

    private Stream<String> getPossibleSerializedValues(JsonSchemaVersion version) {
        String versionSuffix = getPresentableVersionSuffix(version);
        return Stream.of(
            JsonLocalize.schemaOfVersion(versionSuffix).get(),
            JsonLocalize.schemaOfVersionDeprecated(versionSuffix).get()
        );
    }

    private static String getPresentableVersionSuffix(JsonSchemaVersion version) {
        return switch (version) {
            case SCHEMA_4 -> "4";
            case SCHEMA_6 -> "6";
            case SCHEMA_7 -> "7";
            case SCHEMA_2019_09 -> "2019.09";
            case SCHEMA_2020_12 -> "2020.12";
        };
    }

    private static String normalizeGroupingSeparators(String value) {
        return GROUPING_SEPARATORS.matcher(value).replaceAll("");
    }
}
