// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema.impl.fixes;

import com.jetbrains.jsonSchema.extension.adapter.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import consulo.annotation.access.RequiredWriteAction;
import consulo.json.localize.JsonLocalize;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class RemoveDuplicateArrayItemsFix implements LocalQuickFix {
    private final int[] myIndices;

    public RemoveDuplicateArrayItemsFix(int[] indices) {
        myIndices = indices;
    }

    @Override
    public LocalizeValue getName() {
        return JsonLocalize.removeDuplicatedItems();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getStartElement();
        if (element == null) {
            return;
        }
        JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element, null);
        if (walker == null) {
            return;
        }
        JsonArrayValueAdapter parentArray = findParentArray(walker, element);
        if (parentArray == null) {
            return;
        }
        List<PsiElement> elementsToDelete = new ArrayList<>();
        List<JsonValueAdapter> elements = parentArray.getElements();
        for (int index = 0; index < elements.size(); index++) {
            if (ArrayUtil.indexOf(myIndices, index) >= 0) {
                PsiElement delegate = elements.get(index).getDelegate();
                if (delegate != null) {
                    elementsToDelete.add(delegate);
                }
            }
        }
        for (PsiElement it : elementsToDelete) {
            if (!it.getTextRange().intersects(element.getTextRange())) {
                Objects.requireNonNull(walker.getSyntaxAdapter(project)).removeArrayItem(it);
            }
        }
    }

    private static @Nullable JsonArrayValueAdapter findParentArray(JsonLikePsiWalker walker, PsiElement element) {
        PsiElement current = element;
        while (current != null) {
            JsonValueAdapter adapter = walker.createValueAdapter(current);
            JsonArrayValueAdapter array = adapter == null ? null : adapter.getAsArray();
            if (array != null) {
                return array;
            }
            current = current instanceof PsiFile ? null : current.getParent();
        }
        return null;
    }
}
