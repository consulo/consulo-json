// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.psi.impl;

import com.intellij.json.psi.JsonPsiUtil;
import consulo.annotation.component.ComponentScope;
import consulo.util.lang.StringUtil;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.util.LowMemoryWatcher;
import consulo.disposer.Disposable;
import jakarta.annotation.Nonnull;
import jakarta.inject.Singleton;

import java.util.concurrent.ConcurrentHashMap;

@Singleton
@ServiceAPI(value = ComponentScope.APPLICATION)
@ServiceImpl
class JsonTextLiteralService implements Disposable {
  private final ConcurrentHashMap<String, String> unquoteAndUnescapeCache;

  public JsonTextLiteralService() {
    this.unquoteAndUnescapeCache = new ConcurrentHashMap<>(2048);
    LowMemoryWatcher.register(() -> unquoteAndUnescapeCache.clear(), this);
  }

  @Nonnull
  public String unquoteAndUnescape(@Nonnull String str) {
    return unquoteAndUnescapeCache.computeIfAbsent(str, key -> {
      String text = JsonPsiUtil.stripQuotes(key);
      return text.indexOf('\\') >= 0 ? StringUtil.unescapeStringCharacters(text) : text;
    });
  }

  @Override
  public void dispose() {
  }

  @Nonnull
  public static JsonTextLiteralService getInstance() {
    return Application.get().getInstance(JsonTextLiteralService.class);
  }
}
