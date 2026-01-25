// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.highlighting;

import com.intellij.json.impl.pointer.JsonPointerPosition;
import com.intellij.json.psi.*;
import com.jetbrains.jsonSchema.impl.JsonOriginalPsiWalker;
import consulo.language.editor.rawHighlight.HighlightVisitor;
import consulo.language.editor.rawHighlight.RainbowVisitor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nonnull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class JsonRainbowVisitor extends RainbowVisitor {
  private static final class Holder {
    private static final Map<String, Set<String>> blacklist = createBlacklist();

    private static Map<String, Set<String>> createBlacklist() {
      Map<String, Set<String>> blacklist = new HashMap<>();
      blacklist.put("package.json", Set.of("/dependencies",
                                                      "/devDependencies",
                                                      "/peerDependencies",
                                                      "/scripts",
                                                      "/directories",
                                                      "/optionalDependencies"));
      return blacklist;
    }
  }

  public boolean suitableForFile(@Nonnull PsiFile psiFile) {
    return psiFile instanceof JsonFile;
  }

  @Override
  public void visit(@Nonnull PsiElement element) {
    if (element instanceof JsonProperty) {
      PsiFile file = element.getContainingFile();
      String fileName = file.getName();
      if (Holder.blacklist.containsKey(fileName)) {
        JsonPointerPosition position = JsonOriginalPsiWalker.INSTANCE.findPosition(element, false);
        if (position != null && Holder.blacklist.get(fileName).contains(position.toJsonPointer())) return;
      }
      String name = ((JsonProperty)element).getName();
      addInfo(getInfo(file, ((JsonProperty)element).getNameElement(), name, JsonSyntaxHighlighterFactory.JSON_PROPERTY_KEY));
      JsonValue value = ((JsonProperty)element).getValue();
      if (value instanceof JsonObject) {
        addInfo(getInfo(file, value.getFirstChild(), name, JsonSyntaxHighlighterFactory.JSON_BRACES));
        addInfo(getInfo(file, value.getLastChild(), name, JsonSyntaxHighlighterFactory.JSON_BRACES));
      }
      else if (value instanceof JsonArray) {
        addInfo(getInfo(file, value.getFirstChild(), name, JsonSyntaxHighlighterFactory.JSON_BRACKETS));
        addInfo(getInfo(file, value.getLastChild(), name, JsonSyntaxHighlighterFactory.JSON_BRACKETS));
        for (JsonValue jsonValue : ((JsonArray)value).getValueList()) {
          addSimpleValueInfo(name, file, jsonValue);
        }
      }
      else {
        addSimpleValueInfo(name, file, value);
      }
    }
  }

  private void addSimpleValueInfo(String name, PsiFile file, JsonValue value) {
    if (value instanceof JsonStringLiteral) {
      addInfo(getInfo(file, value, name, JsonSyntaxHighlighterFactory.JSON_STRING));
    }
    else if (value instanceof JsonNumberLiteral) {
      addInfo(getInfo(file, value, name, JsonSyntaxHighlighterFactory.JSON_NUMBER));
    }
    else if (value instanceof JsonLiteral) {
      addInfo(getInfo(file, value, name, JsonSyntaxHighlighterFactory.JSON_KEYWORD));
    }
  }

  @Override
  public @Nonnull HighlightVisitor clone() {
    return new JsonRainbowVisitor();
  }
}
