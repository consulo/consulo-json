package com.intellij.json.codeinsight;

import com.intellij.json.JsonLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.language.Language;
import consulo.language.editor.annotation.Annotator;
import consulo.language.editor.annotation.AnnotatorFactory;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 2026-01-23
 */
@ExtensionImpl
public class JsonLiteralAnnotatorFactory implements AnnotatorFactory, DumbAware {
    @Nullable
    @Override
    public Annotator createAnnotator() {
        return new JsonLiteralAnnotator();
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return JsonLanguage.INSTANCE;
    }
}
