// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.psi;

import com.intellij.json.JsonFileType;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

/**
 * @author Mikhail Golubev
 */
public class JsonElementGenerator {
  private final Project myProject;

  public JsonElementGenerator(@Nonnull Project project) {
    myProject = project;
  }

  /**
   * Create lightweight in-memory {@link JsonFile} filled with {@code content}.
   *
   * @param content content of the file to be created
   * @return created file
   */
  public @Nonnull PsiFile createDummyFile(@Nonnull String content) {
    final PsiFileFactory psiFileFactory = PsiFileFactory.getInstance(myProject);
    return psiFileFactory.createFileFromText("dummy." + JsonFileType.INSTANCE.getDefaultExtension(), JsonFileType.INSTANCE, content);
  }

  /**
   * Create JSON value from supplied content.
   *
   * @param content properly escaped text of JSON value, e.g. Java literal {@code "\"new\\nline\""} if you want to create string literal
   * @param <T>     type of the JSON value desired
   * @return element created from given text
   *
   * @see #createStringLiteral(String)
   */
  public @Nonnull <T extends JsonValue> T createValue(@Nonnull String content) {
    final PsiFile file = createDummyFile("{\"foo\": " + content + "}");
    //noinspection unchecked,ConstantConditions
    return (T)((JsonObject)file.getFirstChild()).getPropertyList().get(0).getValue();
  }

  public @Nonnull JsonObject createObject(@Nonnull String content) {
    final PsiFile file = createDummyFile("{" + content + "}");
    return (JsonObject) file.getFirstChild();
  }

  public @Nonnull JsonArray createEmptyArray() {
    final PsiFile file = createDummyFile("[]");
    return (JsonArray) file.getFirstChild();
  }

  public @Nonnull JsonValue createArrayItemValue(@Nonnull String content) {
    final PsiFile file = createDummyFile("[" + content + "]");
    JsonArray array = (JsonArray)file.getFirstChild();
    return array.getValueList().get(0);
  }

  /**
   * Create JSON string literal from supplied <em>unescaped</em> content.
   *
   * @param unescapedContent unescaped content of string literal, e.g. Java literal {@code "new\nline"} (compare with {@link #createValue(String)}).
   * @return JSON string literal created from given text
   */
  public @Nonnull JsonStringLiteral createStringLiteral(@Nonnull String unescapedContent) {
    return createValue('"' + StringUtil.escapeStringCharacters(unescapedContent) + '"');
  }

  public @Nonnull JsonProperty createProperty(final @Nonnull String name, final @Nonnull String value) {
    final PsiFile file = createDummyFile("{\"" + name + "\": " + value + "}");
    return ((JsonObject) file.getFirstChild()).getPropertyList().get(0);
  }

  public @Nonnull PsiElement createComma() {
    final JsonArray jsonArray1 = createValue("[1, 2]");
    return jsonArray1.getValueList().get(0).getNextSibling();
  }
}
