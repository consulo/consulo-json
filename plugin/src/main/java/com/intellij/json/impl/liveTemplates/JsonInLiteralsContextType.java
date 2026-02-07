// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.liveTemplates;

import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonStringLiteral;
import consulo.annotation.component.ExtensionImpl;
import consulo.json.localize.JsonLocalize;
import consulo.language.editor.template.context.BaseTemplateContextType;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nonnull;

import static consulo.language.pattern.PlatformPatterns.psiElement;

@ExtensionImpl
public final class JsonInLiteralsContextType extends BaseTemplateContextType {
    public JsonInLiteralsContextType() {
        super("JSON_STRING_VALUES", JsonLocalize.jsonStringValues());
    }

    @Override
    public boolean isInContext(@Nonnull PsiFile file, int offset) {
        return file instanceof JsonFile && psiElement().inside(JsonStringLiteral.class).accepts(file.findElementAt(offset));
    }
}
