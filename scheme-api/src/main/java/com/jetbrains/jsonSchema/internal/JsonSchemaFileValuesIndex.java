// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import com.intellij.json.JsonFileType;
import com.intellij.json.json5.Json5FileType;
import com.intellij.json.syntax.JsonSyntaxLexer;
import com.intellij.json.syntax.json5.Json5SyntaxLexer;
import consulo.annotation.component.ExtensionImpl;
import consulo.index.io.DataIndexer;
import consulo.index.io.EnumeratorStringDescriptor;
import consulo.index.io.ID;
import consulo.index.io.KeyDescriptor;
import consulo.index.io.data.DataExternalizer;
import consulo.language.ast.IElementType;
import consulo.language.ast.TokenType;
import consulo.language.lexer.Lexer;
import consulo.language.psi.stub.FileBasedIndex;
import consulo.language.psi.stub.FileBasedIndexExtension;
import consulo.language.psi.stub.FileContent;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.json.JsonElementTypes.*;

@ExtensionImpl
public final class JsonSchemaFileValuesIndex extends FileBasedIndexExtension<String, String> {
  public static final ID<String, String> INDEX_ID = ID.create("json.file.root.values");
  private static final int VERSION = 5;
  public static final String NULL = "$NULL$";
  public static final String SCHEMA_PROPERTY_NAME = "$schema";

  @Override
  public @Nonnull ID<String, String> getName() {
    return INDEX_ID;
  }

  private final DataIndexer<String, String, FileContent> myIndexer =
    new DataIndexer<>() {
      @Override
      public @Nonnull Map<String, String> map(@Nonnull FileContent inputData) {
        return readTopLevelProps(inputData.getFileType(), inputData.getContentAsText());
      }
    };

  @Override
  public @Nonnull DataIndexer<String, String, FileContent> getIndexer() {
    return myIndexer;
  }

  @Override
  public @Nonnull KeyDescriptor<String> getKeyDescriptor() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public @Nonnull DataExternalizer<String> getValueExternalizer() {
    return EnumeratorStringDescriptor.INSTANCE;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

  @Override
  public @Nonnull FileBasedIndex.InputFilter getInputFilter() {
    return (project, virtualFile) -> virtualFile.getFileType() instanceof JsonFileType;
  }

  @Override
  public boolean dependsOnFileContent() {
    return true;
  }

  public static @Nullable String getCachedValue(Project project, VirtualFile file, String requestedKey) {
    if (project.isDisposed() || !file.isValid() || DumbService.isDumb(project)) return NULL;
    return FileBasedIndex.getInstance().getFileData(INDEX_ID, file, project).get(requestedKey);
  }

  static @Nonnull Map<String, String> readTopLevelProps(@Nonnull FileType fileType, @Nonnull CharSequence content) {
    if (!(fileType instanceof JsonFileType)) return new HashMap<>();

    Lexer lexer = fileType == Json5FileType.INSTANCE ?
                  new Json5SyntaxLexer() :
                  new JsonSyntaxLexer();
    final HashMap<String, String> map = new HashMap<>();
    lexer.start(content);

    // We only care about properties at the root level having the form of "property" : "value".
    int nesting = 0;
    boolean idFound = false;
    boolean obsoleteIdFound = false;
    boolean schemaFound = false;
    while (!(idFound && schemaFound && obsoleteIdFound) && lexer.getTokenStart() < lexer.getBufferEnd()) {
      IElementType token = lexer.getTokenType();
      // Nesting level can only change at curly braces.
      if (token == L_CURLY) {
        nesting++;
      }
      else if (token == R_CURLY) {
        nesting--;
      }
      else if (nesting == 1 &&
               (token == DOUBLE_QUOTED_STRING
                || token == SINGLE_QUOTED_STRING
                || token == IDENTIFIER)) {
        // We are looking for two special properties at the root level.
        switch (lexer.getTokenText()) {
          case "$id", "\"$id\"", "'$id'" -> idFound |= captureValueIfString(lexer, map, JsonCachedValues.ID_CACHE_KEY);
          case "id", "\"id\"", "'id'" -> obsoleteIdFound |= captureValueIfString(lexer, map, JsonCachedValues.OBSOLETE_ID_CACHE_KEY);
          case SCHEMA_PROPERTY_NAME, "\"$schema\"", "'$schema'" -> schemaFound |= captureValueIfString(lexer, map, JsonCachedValues.URL_CACHE_KEY);
        }
      }
      lexer.advance();
    }
    if (!map.containsKey(JsonCachedValues.ID_CACHE_KEY)) map.put(JsonCachedValues.ID_CACHE_KEY, NULL);
    if (!map.containsKey(JsonCachedValues.OBSOLETE_ID_CACHE_KEY)) map.put(JsonCachedValues.OBSOLETE_ID_CACHE_KEY, NULL);
    if (!map.containsKey(JsonCachedValues.URL_CACHE_KEY)) map.put(JsonCachedValues.URL_CACHE_KEY, NULL);
    return map;
  }

  private static boolean captureValueIfString(@Nonnull Lexer lexer, @Nonnull HashMap<String, String> destMap, @Nonnull String key) {
    IElementType token;
    lexer.advance();
    token = skipWhitespacesAndGetTokenType(lexer);
    if (token == COLON) {
      lexer.advance();
      token = skipWhitespacesAndGetTokenType(lexer);
      if (token == DOUBLE_QUOTED_STRING || token == SINGLE_QUOTED_STRING) {
        String text = lexer.getTokenText();
        destMap.put(key, text.length() <= 1 ? "" : text.substring(1, text.length() - 1));
        return true;
      }
    }
    return false;
  }

  private static @Nullable IElementType skipWhitespacesAndGetTokenType(@Nonnull Lexer lexer) {
    while ( lexer.getTokenType() == TokenType.WHITE_SPACE ||
           lexer.getTokenType() == LINE_COMMENT ||
           lexer.getTokenType() == BLOCK_COMMENT) {
      lexer.advance();
    }
    return lexer.getTokenType();
  }
}
