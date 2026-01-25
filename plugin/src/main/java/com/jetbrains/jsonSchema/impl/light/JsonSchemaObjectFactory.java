package com.jetbrains.jsonSchema.impl.light;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public interface JsonSchemaObjectFactory<T, V extends JsonSchemaObject & JsonSchemaNodePointer<T>> {
  /**
   * @return an instance of schema object backed by physically existing schema node that can be found by a combined json pointer, where
   * the combined pointer is created by concatenation of the current node pointer and childNodeRelativePointer argument.
   */
  @Nullable
  JsonSchemaObject getChildSchemaObjectByName(@Nonnull V parentSchemaObject, @Nonnull String... childNodeRelativePointer);

  /**
   * @return an instance of schema object backed by physically existing schema node that can be found by an absolute json pointer
   */
  @Nullable
  JsonSchemaObject getSchemaObjectByAbsoluteJsonPointer(@Nonnull String jsonPointer);
}
