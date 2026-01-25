package com.intellij.json.psi;

import consulo.language.psi.PsiFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public interface JsonFile extends JsonElement, PsiFile {
  /**
   * Returns {@link JsonArray} or {@link JsonObject} value according to JSON standard.
   *
   * @return top-level JSON element if any or {@code null} otherwise
   */
  @Nullable
  JsonValue getTopLevelValue();

  @Nonnull
  List<JsonValue> getAllTopLevelValues();
}
