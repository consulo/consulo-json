package com.intellij.json.impl.breadcrumbs;

import com.intellij.json.JsonLanguage;
import com.intellij.json.JsonUtil;
import com.intellij.json.psi.JsonProperty;
import consulo.annotation.component.ExtensionImpl;
import consulo.ide.navigationToolbar.StructureAwareNavBarModelExtension;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 2026-02-05
 */
@ExtensionImpl
public class JsonNavBarModelExtension extends StructureAwareNavBarModelExtension {
    @Nonnull
    @Override
    protected Language getLanguage() {
        return JsonLanguage.INSTANCE;
    }

    @Nullable
    @Override
    public String getPresentableText(Object e) {
        if (e instanceof JsonProperty) {
            return ((JsonProperty) e).getName();
        }
        else if (e instanceof PsiElement psiElement && JsonUtil.isArrayElement(psiElement)) {
            int i = JsonUtil.getArrayIndexOfItem(psiElement);
            if (i != -1) {
                return String.valueOf(i);
            }
        }
        return null;
    }
}
