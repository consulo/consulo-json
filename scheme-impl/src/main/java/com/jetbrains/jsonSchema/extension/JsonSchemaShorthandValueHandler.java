// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Handles a special "shorthand" form of values in some configuration formats.
 * For example, you can have "feature: enabled" and "feature:\n enabled: true".
 * Currently, it's used only in nested completion, but can be reused in other features.
 */
public interface JsonSchemaShorthandValueHandler {
  ExtensionPointName<JsonSchemaShorthandValueHandler> EXTENSION_POINT_NAME =
    ExtensionPointName.create("com.intellij.json.shorthandValueHandler");

  /**
   * Whether shorthand values can happen in this particular kind of files
   */
  boolean isApplicable(PsiFile file);

  /**
   * Gets a path within a file (nested property names), and a property literal value
   * Returns a key-value for a replacement
   */
  @Nullable
  KeyValue expandShorthandValue(List<String> path, String value);

  /**
   * Gets a path within a file (nested property names), and a property name and value in the full form
   * Returns a collapsed representation of that property
   */
  @Nullable
  default String collapseToShorthandValue(List<String> path, KeyValue data) {
    return null;
  }

  /**
   * Whether the object is collapsible. By default, it's collapsible if there is just one property.
   */
  default boolean isCollapsible(JsonObjectValueAdapter parentObject) {
    return parentObject.getPropertyList().size() == 1;
  }

  class KeyValue {
    private final String key;
    private final String value;

    public KeyValue(String key, String value) {
      this.key = key;
      this.value = value;
    }

    public String getKey() {
      return key;
    }

    public String getValue() {
      return value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      KeyValue keyValue = (KeyValue) o;
      return Objects.equals(key, keyValue.key) && Objects.equals(value, keyValue.value);
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, value);
    }

    @Override
    public String toString() {
      return "KeyValue{key='" + key + "', value='" + value + "'}";
    }
  }
}
