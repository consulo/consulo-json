// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.impl.nestedCompletions;

import consulo.document.Document;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.BiConsumer;

public final class DocumentChanger {
    private DocumentChanger() {
    }

    public static DocumentChange<Integer> documentChangeAt(int offset, BiConsumer<Document, Integer> task) {
        return new DocumentChange<>(offset, task);
    }

    /**
     * This utility will run tasks from right to left, which makes comprehending mutations easier.
     */
    @SafeVarargs
    public static <T extends Comparable<T>> void applyChangesOrdered(Document document, DocumentChange<T>... tasks) {
        Arrays.sort(tasks, Comparator.comparing((DocumentChange<T> dc) -> dc.getKey()).reversed());
        for (DocumentChange<T> task : tasks) {
            task.getSideEffect().accept(document, task.getKey());
        }
    }
}
