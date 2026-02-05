// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import consulo.json.localize.JsonLocalize;
import com.intellij.json.psi.*;
import com.intellij.openapi.paths.WebReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.JsonPointerReferenceProvider;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public final class JsonSchemaRefReferenceInspection extends JsonSchemaBasedInspectionBase {

  @Override
  protected PsiElementVisitor doBuildVisitor(@Nonnull JsonValue root,
                                             @Nullable JsonSchemaObject schema,
                                             @Nonnull JsonSchemaService service,
                                             @Nonnull ProblemsHolder holder,
                                             @Nonnull LocalInspectionToolSession session) {
    boolean checkRefs = schema != null && service.isSchemaFile(schema);
    return new JsonElementVisitor() {
      @Override
      public void visitElement(@Nonnull PsiElement element) {
        if (element == root) {
          if (element instanceof JsonObject) {
            final JsonProperty schemaProp = ((JsonObject)element).findProperty("$schema");
            if (schemaProp != null) {
              doCheck(schemaProp.getValue());
            }
          }
        }
        super.visitElement(element);
      }

      @Override
      public void visitProperty(@Nonnull JsonProperty o) {
        if (!checkRefs) return;
        if ("$ref".equals(o.getName())) {
          doCheck(o.getValue());
        }
        super.visitProperty(o);
      }

      private void doCheck(JsonValue value) {
        if (!(value instanceof JsonStringLiteral)) return;
        for (PsiReference reference : value.getReferences()) {
          if (reference instanceof WebReference) continue;
          final PsiElement resolved = reference.resolve();
          if (resolved == null) {
            holder.registerProblem(reference, getReferenceErrorDesc(reference), ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          }
        }
      }

      private static @InspectionMessage String getReferenceErrorDesc(PsiReference reference) {
        final String text = reference.getCanonicalText();
        if (reference instanceof FileReference) {
          final int hash = text.indexOf('#');
          return JsonLocalize.jsonSchemaRefFileNotFound(hash == -1 ? text : text.substring(0, hash).get());
        }
        if (reference instanceof JsonPointerReferenceProvider.JsonSchemaIdReference) {
          return JsonLocalize.jsonSchemaRefCannotResolveId(text).get();
        }
        final int lastSlash = text.lastIndexOf('/');
        if (lastSlash == -1) {
          return JsonLocalize.jsonSchemaRefCannotResolvePath(text).get();
        }
        final String substring = text.substring(text.lastIndexOf('/') + 1);

        try {
          Integer.parseInt(substring);
          return JsonLocalize.jsonSchemaRefNoArrayElement(substring).get();
        }
        catch (Exception e) {
          return JsonLocalize.jsonSchemaRefNoProperty(substring).get();
        }
      }
    };
  }
}
