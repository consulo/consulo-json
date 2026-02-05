// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.impl.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.lang.Language;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.light.nodes.RootJsonSchemaObjectBackedByJackson;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;

public final class JsonSchemaBasedLanguageInjector extends JsonSchemaInjectorBase {
  @Override
  public void getLanguagesToInject(@Nonnull MultiHostRegistrar registrar, @Nonnull PsiElement context) {
    if (!(context instanceof JsonStringLiteral)) return;
    InjectedLanguageData language = getLanguageToInject(context, false);
    if (language == null) return;
    injectForHost(registrar, (JsonStringLiteral)context, language);
  }

  public static @Nullable InjectedLanguageData getLanguageToInject(@Nonnull PsiElement context, boolean relaxPositionCheck) {
    Project project = context.getProject();
    PsiFile containingFile = context.getContainingFile();
    JsonSchemaObject schemaObject = JsonSchemaService.Impl.get(project).getSchemaObject(containingFile);
    if (Registry.is("json.schema.object.v2")
        && schemaObject instanceof RootJsonSchemaObjectBackedByJackson rootSchema
        && !rootSchema.checkHasInjections()) {
      return null;
    }
    if (schemaObject == null) return null;
    JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(context, schemaObject);
    if (walker == null) return null;
    ThreeState isName = walker.isName(context);
    if (relaxPositionCheck && isName == ThreeState.YES
        || !relaxPositionCheck && isName != ThreeState.NO) {
      return null;
    }
    final JsonPointerPosition position = walker.findPosition(context, true);
    if (position == null || position.isEmpty()) return null;
    final Collection<JsonSchemaObject> schemas = new JsonSchemaResolver(project, schemaObject, position, walker.createValueAdapter(context)).resolve();
    for (JsonSchemaObject schema : schemas) {
      String injection = schema.getLanguageInjection();
      if (injection != null) {
        Language language = Language.findLanguageByID(injection);
        if (language != null) {
          return new InjectedLanguageData(language, schema.getLanguageInjectionPrefix(), schema.getLanguageInjectionPostfix());
        }
      }
    }
    return null;
  }
}
