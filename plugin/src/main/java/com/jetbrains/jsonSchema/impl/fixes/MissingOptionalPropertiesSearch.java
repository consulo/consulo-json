// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.fixes;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.*;
import com.jetbrains.jsonSchema.impl.JsonValidationError.FixableIssueKind;
import com.jetbrains.jsonSchema.impl.JsonValidationError.MissingMultiplePropsIssueData;
import com.jetbrains.jsonSchema.impl.JsonValidationError.MissingPropertyIssueData;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MissingOptionalPropertiesSearch {

  @RequiresReadLock
  @Nullable
  public static JsonSchemaPropertiesInfo collectMissingPropertiesFromSchema(SmartPsiElementPointer<? extends PsiElement> objectNodePointer,
                                                                             Project project) {
    PsiElement objectNode = objectNodePointer.dereference();
    if (objectNode == null) return null;

    JsonSchemaObject schemaObjectFile = JsonSchemaService.Impl.get(project).getSchemaObject(objectNode.getContainingFile());
    if (schemaObjectFile == null) return null;

    JsonLikePsiWalker psiWalker = JsonLikePsiWalker.getWalker(objectNode, schemaObjectFile);
    if (psiWalker == null) return null;

    JsonLikePsiWalker.JsonPosition position = psiWalker.findPosition(objectNode, true);
    if (position == null) return null;

    JsonValueAdapter valueAdapter = psiWalker.createValueAdapter(objectNode);
    if (valueAdapter == null) return null;

    JsonSchemaAnnotatorChecker checker = new JsonSchemaAnnotatorChecker(project, new JsonComplianceCheckerOptions(false, false, true));
    checker.checkObjectBySchemaRecordErrors(schemaObjectFile, valueAdapter, position);

    JsonValidationError errorsForNode = checker.getErrors().get(objectNode);
    if (errorsForNode == null) return null;

    MissingMultiplePropsIssueData missingRequiredProperties = extractPropertiesOfKind(errorsForNode, FixableIssueKind.MissingProperty);
    MissingMultiplePropsIssueData missingKnownProperties = extractPropertiesOfKind(errorsForNode, FixableIssueKind.MissingOptionalProperty);

    return new JsonSchemaPropertiesInfo(missingRequiredProperties, missingKnownProperties);
  }

  private static MissingMultiplePropsIssueData extractPropertiesOfKind(JsonValidationError foundError,
                                                                         FixableIssueKind kind) {
    Object issueData = null;
    if (foundError.getFixableIssueKind() == kind) {
      issueData = foundError.getIssueData();
    }

    Collection<MissingPropertyIssueData> filteredProperties;
    if (issueData instanceof MissingMultiplePropsIssueData) {
      filteredProperties = filterOutUnwantedProperties(((MissingMultiplePropsIssueData) issueData).getMyMissingPropertyIssues());
    } else if (issueData instanceof MissingPropertyIssueData) {
      filteredProperties = filterOutUnwantedProperties(Collections.singletonList((MissingPropertyIssueData) issueData));
    } else {
      filteredProperties = Collections.emptyList();
    }

    return new MissingMultiplePropsIssueData(filteredProperties);
  }

  private static Collection<MissingPropertyIssueData> filterOutUnwantedProperties(Collection<MissingPropertyIssueData> missingProperties) {
    return missingProperties.stream()
      .filter(prop -> !prop.propertyName.startsWith("$"))
      .collect(Collectors.toList());
  }

  public static class JsonSchemaPropertiesInfo {
    private final MissingMultiplePropsIssueData missingRequiredProperties;
    private final MissingMultiplePropsIssueData missingKnownProperties;

    public JsonSchemaPropertiesInfo(MissingMultiplePropsIssueData missingRequiredProperties,
                                    MissingMultiplePropsIssueData missingKnownProperties) {
      this.missingRequiredProperties = missingRequiredProperties;
      this.missingKnownProperties = missingKnownProperties;
    }

    public MissingMultiplePropsIssueData getMissingRequiredProperties() {
      return missingRequiredProperties;
    }

    public MissingMultiplePropsIssueData getMissingKnownProperties() {
      return missingKnownProperties;
    }

    public boolean hasOnlyRequiredPropertiesMissing() {
      return missingRequiredProperties.getMyMissingPropertyIssues().size() == missingKnownProperties.getMyMissingPropertyIssues().size();
    }

    public boolean hasNoRequiredPropertiesMissing() {
      return missingRequiredProperties.getMyMissingPropertyIssues().isEmpty();
    }
  }
}
