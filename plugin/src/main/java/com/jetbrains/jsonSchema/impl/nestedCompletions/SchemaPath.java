// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.nestedCompletions;

import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Represents a path through a tree structure. Often inversed using accessor() */
public class SchemaPath {
  private final String name;
  @Nullable
  private final SchemaPath previous;

  public SchemaPath(String name, @Nullable SchemaPath previous) {
    this.name = name;
    this.previous = previous;
  }

  public String getName() {
    return name;
  }

  @Nullable
  public SchemaPath getPrevious() {
    return previous;
  }

  public static SchemaPath extend(@Nullable SchemaPath path, String name) {
    return new SchemaPath(name, path);
  }

  public List<String> accessor() {
    List<String> result = new ArrayList<>();
    SchemaPath current = this;
    while (current != null) {
      result.add(current.name);
      current = current.previous;
    }
    Collections.reverse(result);
    return result;
  }

  public String prefix() {
    return accessor().stream().collect(Collectors.joining("."));
  }
}
