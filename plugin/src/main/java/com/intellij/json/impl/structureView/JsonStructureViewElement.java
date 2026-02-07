// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.impl.structureView;

import com.intellij.json.psi.*;
import consulo.fileEditor.structureView.StructureViewTreeElement;
import consulo.fileEditor.structureView.tree.TreeElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.navigation.ItemPresentation;
import consulo.util.collection.ContainerUtil;
import jakarta.annotation.Nonnull;

import java.util.List;

/**
 * @author Mikhail Golubev
 */
public final class JsonStructureViewElement implements StructureViewTreeElement {
    private final JsonElement myElement;

    public JsonStructureViewElement(@Nonnull JsonElement element) {
        assert PsiTreeUtil.instanceOf(element, JsonFile.class, JsonProperty.class, JsonObject.class, JsonArray.class);
        myElement = element;
    }

    @Override
    public JsonElement getValue() {
        return myElement;
    }

    @Override
    public void navigate(boolean requestFocus) {
        myElement.navigate(requestFocus);
    }

    @Override
    public boolean canNavigate() {
        return myElement.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
        return myElement.canNavigateToSource();
    }

    @Override
    public @Nonnull ItemPresentation getPresentation() {
        final ItemPresentation presentation = myElement.getPresentation();
        assert presentation != null;
        return presentation;
    }

    @Override
    @Nonnull
    public TreeElement[] getChildren() {
        JsonElement value = null;
        if (myElement instanceof JsonFile) {
            value = ((JsonFile) myElement).getTopLevelValue();
        }
        else if (myElement instanceof JsonProperty) {
            value = ((JsonProperty) myElement).getValue();
        }
        else if (PsiTreeUtil.instanceOf(myElement, JsonObject.class, JsonArray.class)) {
            value = myElement;
        }
        if (value instanceof JsonObject object) {
            return ContainerUtil.map2Array(object.getPropertyList(), TreeElement.class, property -> new JsonStructureViewElement(property));
        }
        else if (value instanceof JsonArray array) {
            final List<TreeElement> childObjects = ContainerUtil.mapNotNull(array.getValueList(), value1 -> {
                if (value1 instanceof JsonObject && !((JsonObject) value1).getPropertyList().isEmpty()) {
                    return new JsonStructureViewElement(value1);
                }
                else if (value1 instanceof JsonArray && PsiTreeUtil.findChildOfType(value1, JsonProperty.class) != null) {
                    return new JsonStructureViewElement(value1);
                }
                return null;
            });
            return childObjects.toArray(TreeElement.EMPTY_ARRAY);
        }
        return EMPTY_ARRAY;
    }
}
