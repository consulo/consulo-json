// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.liveTemplates;

import com.intellij.json.JsonFileType;
import com.intellij.json.psi.JsonFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.json.localize.JsonLocalize;
import consulo.language.editor.template.context.FileTypeBasedContextType;
import consulo.language.psi.PsiFile;
import jakarta.annotation.Nonnull;

/**
 * @author Konstantin.Ulitin
 */
@ExtensionImpl
public final class JsonContextType extends FileTypeBasedContextType {
    public JsonContextType() {
        super("JSON", JsonLocalize.jsonTemplateContextType(), JsonFileType.INSTANCE);
    }

    @Override
    public boolean isInContext(@Nonnull PsiFile file, int offset) {
        return file instanceof JsonFile;
    }
}
