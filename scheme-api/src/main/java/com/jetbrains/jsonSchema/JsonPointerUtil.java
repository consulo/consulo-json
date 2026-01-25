// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import consulo.util.io.URLUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

public final class JsonPointerUtil {
  public static @Nonnull String escapeForJsonPointer(@Nonnull String name) {
    if (StringUtil.isEmptyOrSpaces(name)) {
      return URLUtil.encodeURIComponent(name);
    }
    return StringUtil.replace(StringUtil.replace(name, "~", "~0"), "/", "~1");
  }

  public static @Nonnull String unescapeJsonPointerPart(@Nonnull String part) {
    part = URLUtil.unescapePercentSequences(part);
    return StringUtil.replace(StringUtil.replace(part, "~0", "~"), "~1", "/");
  }

  public static boolean isSelfReference(@Nullable String ref) {
    return "#".equals(ref) || "#/".equals(ref) || StringUtil.isEmpty(ref);
  }

  public static @Nonnull List<String> split(@Nonnull String pointer) {
    return StringUtil.split(pointer, "/", true, false);
  }

  public static @Nonnull String normalizeSlashes(@Nonnull String ref) {
    return StringUtil.trimStart(ref.replace('\\', '/'), "/");
  }

  public static @Nonnull String normalizeId(@Nonnull String id) {
    id = id.endsWith("#") ? id.substring(0, id.length() - 1) : id;
    return id.startsWith("#") ? id.substring(1) : id;
  }
}
