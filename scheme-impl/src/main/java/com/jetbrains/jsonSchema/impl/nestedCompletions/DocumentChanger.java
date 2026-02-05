// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.nestedCompletions;

import consulo.document.Document;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.BiConsumer;

public final class DocumentChanger {
  private DocumentChanger() {}

  public static class DocumentChange<T extends Comparable<T>> {
    public final T key;
    public final BiConsumer<Document, T> sideEffect;

    public DocumentChange(T key, BiConsumer<Document, T> sideEffect) {
      this.key = key;
      this.sideEffect = sideEffect;
    }
  }

  public static DocumentChange<Integer> documentChangeAt(int offset, BiConsumer<Document, Integer> task) {
    return new DocumentChange<>(offset, task);
  }

  /** This utility will run tasks from right to left, which makes comprehending mutations easier. */
  @SafeVarargs
  public static <T extends Comparable<T>> void applyChangesOrdered(Document document, DocumentChange<T>... tasks) {
    Arrays.sort(tasks, Comparator.comparing((DocumentChange<T> dc) -> dc.key).reversed());
    for (DocumentChange<T> task : tasks) {
      task.sideEffect.accept(document, task.key);
    }
  }
}
