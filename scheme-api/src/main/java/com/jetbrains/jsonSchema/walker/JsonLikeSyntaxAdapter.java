// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.walker;

import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.adapter.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import consulo.language.psi.LeafPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public interface JsonLikeSyntaxAdapter {
    /**
     * Tries to get a property value element for a context element supplied.
     * May modify PSI and create a new property value element if it wasn't there before.
     */
    default PsiElement adjustValue(@Nonnull PsiElement value) {
        return value;
    }

    /**
     * Creates a property element with the given {@code name} and {@code value}.
     * <p>
     * The created element is in an independent PSI tree, and is meant to be inserted in the target tree through some mutating operation,
     * such as {@link PsiElement#replace}. The given {@code project} is used as context for PSI generation
     */
    @Nonnull
    PsiElement createProperty(@Nonnull String name, @Nonnull String value, @Nonnull Project project);

    /**
     * Creates an empty array element.
     * <p>
     * In languages that support single-line and multi-line arrays, {@code preferInline} determines whether to use the single-line form.
     * <p>
     * The created element is in an independent PSI tree, and is meant to be inserted in the target tree through some mutating operation,
     * such as {@link PsiElement#replace}. The given {@code project} is used as context for PSI generation
     */
    @Nonnull
    default PsiElement createEmptyArray(@Nonnull Project project, boolean preferInline) {
        return createEmptyArray(project);
    }

    @Nonnull
    PsiElement createEmptyArray(@Nonnull Project project);

    /**
     * Adds the given {@code itemValue} to the given {@code array} element.
     *
     * @return the {@link PsiElement} representing the item that was added.
     */
    @Nonnull
    PsiElement addArrayItem(@Nonnull PsiElement array, @Nonnull String itemValue);

    void removeArrayItem(@Nonnull PsiElement item);

    void ensureComma(@Nullable PsiElement self, @Nullable PsiElement newElement);

    void removeIfComma(@Nullable PsiElement forward);

    boolean fixWhitespaceBefore(@Nullable PsiElement initialElement, @Nullable PsiElement element);

    @Nonnull
    default String getDefaultValueFromType(@Nullable JsonSchemaType type) {
        return type == null ? "" : type.getDefaultValue();
    }

    @Nonnull
    PsiElement adjustNewProperty(@Nonnull PsiElement element);

    @Nonnull
    PsiElement adjustPropertyAnchor(@Nonnull LeafPsiElement element);

    /**
     * Inserts a property into a JSON-like object. If the given {@code contextForInsertion} is an empty value that can act as an object (such as an
     * empty YAML Document or a simple indent in the position of a property value), the property is inserted as part of a new object.
     *
     * @param contextForInsertion either the object itself or the property before which the new property is inserted. It can also be an empty
     *                            object value (such as an empty YAML Document or a simple indent in the position of a property value).
     * @param newProperty         the property element to insert
     * @return the property element that was actually added (either {@code newProperty} or its copy).
     */
    @Nonnull
    default PsiElement addProperty(@Nonnull PsiElement contextForInsertion, @Nonnull PsiElement newProperty) {
        JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(contextForInsertion);
        var parentPropertyAdapter = walker != null ? walker.getParentPropertyAdapter(contextForInsertion) : null;
        boolean isProcessingProperty = parentPropertyAdapter != null && parentPropertyAdapter.getDelegate() == contextForInsertion;

        PsiElement newElement;
        JsonValueAdapter valueAdapter = walker != null ? walker.createValueAdapter(contextForInsertion) : null;
        if (valueAdapter != null && valueAdapter.isEmptyAdapter()) {
            PsiElement parent = contextForInsertion instanceof LeafPsiElement
                ? adjustPropertyAnchor((LeafPsiElement) contextForInsertion)
                : contextForInsertion;
            // This newProperty.getParent() relies on the fact that the property was created within an object in its dummy environment.
            // This might not always hold, so it would be better not to rely on it.
            newElement = parent.addBefore(newProperty.getParent(), null);
        }
        else if (contextForInsertion instanceof LeafPsiElement) {
            newElement = adjustPropertyAnchor((LeafPsiElement) contextForInsertion).addBefore(newProperty, null);
        }
        else if (isProcessingProperty) {
            newElement = contextForInsertion.getParent().addBefore(newProperty, contextForInsertion);
            ensureComma(PsiTreeUtil.skipWhitespacesAndCommentsBackward(newElement), newElement);
        }
        else {
            // In case of an object, we want to insert after the last property and potential comments, but before whatever syntax marks the end
            // of the object (e.g. a } in JSON, but nothing in YAML). We can't just use 'contextForInsertion.getLastChild()' because it's actually
            // the last property itself in YAML (since there is no syntax for closing the object).
            JsonObjectValueAdapter objectAdapter = valueAdapter != null ? valueAdapter.getAsObject() : null;
            if (objectAdapter == null) {
                throw new IllegalStateException("contextForInsertion must be an object-like element");
            }
            var propertyList = objectAdapter.getPropertyList();
            PsiElement lastPropertyElement = propertyList.isEmpty() ? null : propertyList.get(propertyList.size() - 1).getDelegate();
            PsiElement anchor;
            if (lastPropertyElement != null) {
                anchor = PsiTreeUtil.skipWhitespacesAndCommentsForward(lastPropertyElement);
            }
            else {
                anchor = contextForInsertion.getLastChild();
            }
            if (anchor != null && ",".equals(anchor.getText())) {
                anchor = PsiTreeUtil.skipWhitespacesAndCommentsForward(anchor);
            }
            newElement = contextForInsertion.addBefore(newProperty, anchor);
            ensureComma(lastPropertyElement, newElement);
        }

        PsiElement adjusted = adjustNewProperty(newElement);
        ensureComma(adjusted, PsiTreeUtil.skipWhitespacesAndCommentsForward(newElement));

        return adjusted;
    }

    /**
     * Deletes a property performing the cleanup if needed
     *
     * @param property property to delete
     */
    default void removeProperty(@Nonnull PsiElement property) {
        PsiElement forward = PsiTreeUtil.skipWhitespacesForward(property);
        property.delete();
        removeIfComma(forward);
    }
}
