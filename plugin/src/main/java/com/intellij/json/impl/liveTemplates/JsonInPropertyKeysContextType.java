// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.liveTemplates;

import com.intellij.json.JsonElementTypes;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonPsiUtil;
import com.intellij.json.psi.JsonValue;
import consulo.json.localize.JsonLocalize;
import consulo.language.editor.template.context.BaseTemplateContextType;
import consulo.language.pattern.PatternCondition;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.ProcessingContext;
import jakarta.annotation.Nonnull;

import static consulo.language.pattern.PlatformPatterns.psiElement;

public final class JsonInPropertyKeysContextType extends BaseTemplateContextType {
    private JsonInPropertyKeysContextType() {
        super("JSON_PROPERTY_KEYS", JsonLocalize.jsonPropertyKeys());
    }

    @Override
    public boolean isInContext(@Nonnull PsiFile file, int offset) {
        return file instanceof JsonFile && psiElement().inside(psiElement(JsonValue.class)
            .with(new PatternCondition<PsiElement>("insidePropertyKey") {
                @Override
                public boolean accepts(@Nonnull PsiElement element,
                                       ProcessingContext context) {
                    return JsonPsiUtil.isPropertyKey(element);
                }
            })).beforeLeaf(psiElement(JsonElementTypes.COLON)).accepts(file.findElementAt(offset));
    }
}