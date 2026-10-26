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

import com.intellij.json.pointer.JsonPointerPosition;
import com.jetbrains.jsonSchema.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.JsonValidationError.FixableIssueKind;
import com.jetbrains.jsonSchema.JsonValidationError.MissingMultiplePropsIssueData;
import com.jetbrains.jsonSchema.JsonValidationError.MissingPropertyIssueData;
import com.jetbrains.jsonSchema.JsonValidationError;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.internal.JsonSchemaAnnotatorChecker;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class MissingOptionalPropertiesSearch {
    private MissingOptionalPropertiesSearch() {
    }

    @RequiredReadAction
    public static @Nullable JsonSchemaPropertiesInfo collectMissingPropertiesFromSchema(SmartPsiElementPointer<? extends PsiElement> objectNodePointer,
                                                                                        Project project) {
        PsiElement objectNode = objectNodePointer.getElement();
        if (objectNode == null) {
            return null;
        }
        JsonSchemaObject schemaObjectFile = JsonSchemaService.get(project).getSchemaObject(Objects.requireNonNull(objectNode.getContainingFile()));
        if (schemaObjectFile == null) {
            return null;
        }
        JsonLikePsiWalker psiWalker = JsonLikePsiWalker.getWalker(objectNode, schemaObjectFile);
        if (psiWalker == null) {
            return null;
        }
        JsonPointerPosition position = psiWalker.findPosition(objectNode, true);
        if (position == null) {
            return null;
        }
        JsonValueAdapter valueAdapter = psiWalker.createValueAdapter(objectNode);
        if (valueAdapter == null) {
            return null;
        }
        JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker(project, new JsonComplianceCheckerOptions(false, false, true));
        checker.checkObjectBySchemaRecordErrors(schemaObjectFile, valueAdapter, position);
        JsonValidationError errorsForNode = checker.getErrors().get(objectNode);
        if (errorsForNode == null) {
            return null;
        }

        MissingMultiplePropsIssueData missingRequiredProperties = extractPropertiesOfKind(errorsForNode, FixableIssueKind.MissingProperty);
        MissingMultiplePropsIssueData missingKnownProperties =
            extractPropertiesOfKind(errorsForNode, FixableIssueKind.MissingOptionalProperty);
        return new JsonSchemaPropertiesInfo(missingRequiredProperties, missingKnownProperties);
    }

    private static MissingMultiplePropsIssueData extractPropertiesOfKind(JsonValidationError foundError, FixableIssueKind kind) {
        JsonValidationError.IssueData issueData = foundError.getFixableIssueKind() == kind ? foundError.getIssueData() : null;

        Collection<MissingPropertyIssueData> filteredProperties;
        if (issueData instanceof MissingMultiplePropsIssueData multiplePropsIssueData) {
            filteredProperties = filterOutUnwantedProperties(multiplePropsIssueData.myMissingPropertyIssues);
        }
        else if (issueData instanceof MissingPropertyIssueData propertyIssueData) {
            filteredProperties = filterOutUnwantedProperties(List.of(propertyIssueData));
        }
        else {
            filteredProperties = List.of();
        }
        return new MissingMultiplePropsIssueData(filteredProperties);
    }

    private static Collection<MissingPropertyIssueData> filterOutUnwantedProperties(Collection<MissingPropertyIssueData> missingProperties) {
        return missingProperties.stream().filter(it -> !it.propertyName.startsWith("$")).toList();
    }
}
