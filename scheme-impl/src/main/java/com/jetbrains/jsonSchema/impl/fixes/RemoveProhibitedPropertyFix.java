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
package com.jetbrains.jsonSchema.impl.fixes;

import com.jetbrains.jsonSchema.JsonValidationError;
import com.jetbrains.jsonSchema.extension.adapter.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.walker.JsonLikeSyntaxAdapter;
import consulo.annotation.access.RequiredWriteAction;
import consulo.json.localize.JsonLocalize;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;

import java.util.Objects;

public final class RemoveProhibitedPropertyFix implements LocalQuickFix {
    private final JsonValidationError.ProhibitedPropertyIssueData myData;
    private final JsonLikeSyntaxAdapter myQuickFixAdapter;

    public RemoveProhibitedPropertyFix(JsonValidationError.ProhibitedPropertyIssueData data,
                                       JsonLikeSyntaxAdapter quickFixAdapter) {
        myData = data;
        myQuickFixAdapter = quickFixAdapter;
    }

    public LocalizeValue getFamilyName() {
        return JsonLocalize.removeProhibitedProperty();
    }

    @Override
    public LocalizeValue getName() {
        return LocalizeValue.join(getFamilyName(), LocalizeValue.of(" '" + myData.propertyName + "'"));
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getStartElement();
        if (element == null) {
            return;
        }
        JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element);
        if (walker == null) {
            return;
        }
        JsonPropertyAdapter parentProperty = walker.getParentPropertyAdapter(element);
        assert myData.propertyName.equals(Objects.requireNonNull(parentProperty).getName());
        myQuickFixAdapter.removeProperty(parentProperty.getDelegate());
    }
}
