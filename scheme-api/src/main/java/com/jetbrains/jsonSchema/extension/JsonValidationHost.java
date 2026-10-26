// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package com.jetbrains.jsonSchema.extension;

import com.jetbrains.jsonSchema.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.JsonValidationError;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.internal.MatchResult;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public interface JsonValidationHost {
    void error(final String error, final PsiElement holder, JsonErrorPriority priority);

    void error(final PsiElement newHolder, JsonValidationError error);

    void error(final String error, final PsiElement holder,
               JsonValidationError.FixableIssueKind fixableIssueKind,
               JsonValidationError.@Nullable IssueData data,
               JsonErrorPriority priority);

    void typeError(final PsiElement value, @Nullable JsonSchemaType currentType, final JsonSchemaType... allowedTypes);

    MatchResult resolve(JsonSchemaObject schemaObject, @Nullable JsonValueAdapter inspectedElementAdapter);

    @Nullable
    JsonValidationHost checkByMatchResult(JsonValueAdapter adapter, MatchResult result, JsonComplianceCheckerOptions options);

    boolean isValid();

    void checkObjectBySchemaRecordErrors(JsonSchemaObject schema, JsonValueAdapter object);

    void addErrorsFrom(JsonValidationHost otherHost);

    boolean hasRecordedErrorsFor(JsonValueAdapter inspectedValueAdapter);

    Map<PsiElement, JsonValidationError> getErrors();
}
