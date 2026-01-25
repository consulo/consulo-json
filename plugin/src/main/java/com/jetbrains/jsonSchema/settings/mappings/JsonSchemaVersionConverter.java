// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.settings.mappings;

import com.intellij.json.JsonBundle;
import com.intellij.util.xmlb.Converter;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.stream.Stream;

// This converter exists because a long time ago the JsonSchemaVersion class was directly used in the UI state persisting,
// and the version's identity was computed simply by calling toString() method.
// Important pitfall here is that the toString() method uses language-dependent values from the message bundle,
// so it's crucial to restore values using the same bundle messages as before :(
public class JsonSchemaVersionConverter extends Converter<JsonSchemaVersion> {

  @Override
  @Nullable
  public JsonSchemaVersion fromString(String value) {
    return findSuitableVersion(value);
  }

  @Override
  @Nullable
  public String toString(@Nullable JsonSchemaVersion value) {
    return value != null ? value.toString() : null;
  }

  private JsonSchemaVersion findSuitableVersion(String effectiveSerialisedValue) {
    return Arrays.stream(JsonSchemaVersion.values())
      .filter(version -> canBeSerializedInto(version, effectiveSerialisedValue))
      .findFirst()
      .orElse(JsonSchemaVersion.SCHEMA_4);
  }

  private boolean canBeSerializedInto(JsonSchemaVersion version, String effectiveSerialisedValue) {
    return getPossibleSerializedValues(version).anyMatch(it -> it.equals(effectiveSerialisedValue));
  }

  private Stream<String> getPossibleSerializedValues(JsonSchemaVersion version) {
    int versionNumber;
    switch (version) {
      case SCHEMA_4:
        versionNumber = 4;
        break;
      case SCHEMA_6:
        versionNumber = 6;
        break;
      case SCHEMA_7:
        versionNumber = 7;
        break;
      case SCHEMA_2019_09:
        versionNumber = 201909;
        break;
      case SCHEMA_2020_12:
        versionNumber = 202012;
        break;
      default:
        versionNumber = 4;
    }

    return Stream.of(
      JsonBundle.message("schema.of.version", versionNumber),
      JsonBundle.message("schema.of.version.deprecated", versionNumber)
    );
  }
}
