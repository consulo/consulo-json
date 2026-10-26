// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.walker;

import com.intellij.json.JsonDialectUtil;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonElement;
import com.intellij.json.psi.JsonElementGenerator;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonReferenceExpression;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.jetbrains.jsonSchema.extension.adapter.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.internal.adapter.JsonJsonPropertyAdapter;
import consulo.document.util.TextRange;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.psi.LeafPsiElement;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ThreeState;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class JsonOriginalPsiWalker implements JsonLikePsiWalker {
    public static final JsonOriginalPsiWalker INSTANCE = new JsonOriginalPsiWalker();

    public boolean handles(PsiElement element) {
        PsiElement parent = element.getParent();
        return element instanceof JsonFile && JsonDialectUtil.isStandardJson(element)
               || parent != null && (element instanceof JsonElement || element instanceof LeafPsiElement && parent instanceof JsonElement)
                 && JsonDialectUtil.isStandardJson(CompletionUtilCore.getOriginalOrSelf(parent));
    }

    @Override
    public boolean isQuotedString(PsiElement element) {
        return element instanceof JsonStringLiteral;
    }

    @Override
    public ThreeState isName(PsiElement element) {
        PsiElement parent = element.getParent();
        if (parent instanceof JsonObject) {
            return ThreeState.YES;
        }
        else if (parent instanceof JsonProperty) {
            return PsiTreeUtil.isAncestor(((JsonProperty)parent).getNameElement(), element, false) ? ThreeState.YES : ThreeState.NO;
        }
        return ThreeState.NO;
    }

    @Override
    public boolean isPropertyWithValue(PsiElement element) {
        if (element instanceof JsonStringLiteral || element instanceof JsonReferenceExpression) {
            PsiElement parent = element.getParent();
            if (!(parent instanceof JsonProperty) || ((JsonProperty)parent).getNameElement() != element) {
                return false;
            }
            element = parent;
        }
        return element instanceof JsonProperty && ((JsonProperty)element).getValue() != null;
    }

    @Override
    public @Nullable PsiElement findElementToCheck(PsiElement element) {
        PsiElement current = element;
        while (current != null && !(current instanceof PsiFile)) {
            if (current instanceof JsonValue || current instanceof JsonProperty) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    @Override
    public @Nullable JsonPointerPosition findPosition(PsiElement element, boolean forceLastTransition) {
        JsonPointerPosition pos = new JsonPointerPosition();
        PsiElement current = element;
        while (! (current instanceof PsiFile)) {
            PsiElement position = current;
            current = current.getParent();
            if (current instanceof JsonArray array) {
                List<JsonValue> list = array.getValueList();
                int idx = -1;
                for (int i = 0; i < list.size(); i++) {
                    JsonValue value = list.get(i);
                    if (value.equals(position)) {
                        idx = i;
                        break;
                    }
                }
                pos.addPrecedingStep(idx);
            }
            else if (current instanceof JsonProperty) {
                String propertyName = ((JsonProperty)current).getName();
                current = current.getParent();
                if (!(current instanceof JsonObject)) return null;//incorrect syntax?
                // if either value or not first in the chain - needed for completion variant
                if (position != element || forceLastTransition) {
                    pos.addPrecedingStep(propertyName);
                }
            }
            else if (current instanceof JsonObject && position instanceof JsonProperty) {
                // if either value or not first in the chain - needed for completion variant
                if (position != element || forceLastTransition) {
                    String propertyName = ((JsonProperty)position).getName();
                    pos.addPrecedingStep(propertyName);
                }
            }
            else if (current instanceof PsiFile) {
                break;
            }
            else {
                return null;//something went wrong
            }
        }
        return pos;
    }

    @Override
    public boolean requiresNameQuotes() {
        return true;
    }

    @Override
    public boolean allowsSingleQuotes() {
        return false;
    }

    @Override
    public boolean hasMissingCommaAfter(PsiElement element) {
        PsiElement current = element instanceof JsonProperty ? element : PsiTreeUtil.getParentOfType(element, JsonProperty.class);
        while (current != null && current.getNode().getElementType() != JsonElementTypes.COMMA) {
            current = current.getNextSibling();
        }
        int commaOffset = current == null ? Integer.MAX_VALUE : current.getTextRange().getStartOffset();
        int offset = element.getTextRange().getStartOffset();
        JsonObject object = PsiTreeUtil.getParentOfType(element, JsonObject.class);
        if (object != null) {
            for (JsonProperty property : object.getPropertyList()) {
                int pOffset = property.getTextRange().getStartOffset();
                if (pOffset >= offset && !PsiTreeUtil.isAncestor(property, element, false)) {
                    return pOffset < commaOffset;
                }
            }
        }
        return false;
    }

    @Override
    public Set<String> getPropertyNamesOfParentObject(PsiElement originalPosition, PsiElement computedPosition) {
        JsonObject object = PsiTreeUtil.getParentOfType(computedPosition, JsonObject.class, false);
        if (object != null) {
            return object.getPropertyList().stream()
              .filter(p -> !requiresNameQuotes() || p.getNameElement() instanceof JsonStringLiteral)
              .map(p -> StringUtil.unquoteString(p.getName())).collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    @Override
    public @Nullable JsonPropertyAdapter getParentPropertyAdapter(PsiElement element) {
        JsonProperty property = PsiTreeUtil.getParentOfType(element, JsonProperty.class, false);
        if (property == null) {
            return null;
        }
        return new JsonJsonPropertyAdapter(property);
    }

    @Override
    public boolean isTopJsonElement(PsiElement element) {
        return element instanceof PsiFile;
    }

    @Override
    public @Nullable JsonValueAdapter createValueAdapter(PsiElement element) {
        if (element instanceof JsonProperty) {
            JsonPropertyAdapter parentPropertyAdapter = getParentPropertyAdapter(element);
            return parentPropertyAdapter == null ? null : parentPropertyAdapter.getNameValueAdapter();
        }
        return element instanceof JsonValue ? JsonJsonPropertyAdapter.createAdapterByType((JsonValue)element) : null;
    }

    @Override
    public JsonLikeSyntaxAdapter getSyntaxAdapter(Project project) {
        return JsonOriginalSyntaxAdapter.INSTANCE;
    }

    @Override
    public @Nullable PsiElement getParentContainer(PsiElement element) {
        return PsiTreeUtil.getParentOfType(PsiTreeUtil.getParentOfType(element, JsonProperty.class),
                                    JsonObject.class, JsonArray.class);
    }

    @Override
    public Collection<PsiElement> getRoots(PsiFile file) {
        return file instanceof JsonFile ? ContainerUtil.createMaybeSingletonList(((JsonFile)file).getTopLevelValue()) : List.of();
    }

    @Override
    public @Nullable PsiElement getPropertyNameElement(@Nullable PsiElement property) {
        return property instanceof JsonProperty ? ((JsonProperty)property).getNameElement() : null;
    }

    @Override
    public TextRange adjustErrorHighlightingRange(PsiElement element) {
        PsiElement parent = element.getParent();
        if (parent instanceof JsonFile) {
            PsiElement child = skipMatching(element.getFirstChild(), e -> e.getNextSibling(), e -> !(e instanceof JsonElement));
            return child == null ? element.getTextRange() : child.getTextRange();
        }
        return element.getTextRange();
    }

    private static @Nullable PsiElement skipMatching(@Nullable PsiElement element,
                                                     Function<PsiElement, @Nullable PsiElement> next,
                                                     Predicate<PsiElement> condition) {
        if (element != null) {
            for (PsiElement e = next.apply(element); e != null; e = next.apply(e)) {
                if (!condition.test(e)) {
                    return e;
                }
            }
        }
        return null;
    }

    private static class JsonOriginalSyntaxAdapter implements JsonLikeSyntaxAdapter {
        private static final JsonOriginalSyntaxAdapter INSTANCE = new JsonOriginalSyntaxAdapter();

        @Override
        public PsiElement createProperty(String name, String value, Project project) {
            return new JsonElementGenerator(project).createProperty(name, value);
        }

        @Override
        public PsiElement createEmptyArray(Project project, boolean preferInline) {
            return new JsonElementGenerator(project).createEmptyArray();
        }

        private static @Nullable PsiElement skipWsBackward(@Nullable PsiElement item) {
            while (item instanceof PsiWhiteSpace || item instanceof PsiComment) {
                item = PsiTreeUtil.prevLeaf(item);
            }
            return item;
        }

        private static @Nullable PsiElement skipWsForward(@Nullable PsiElement item) {
            while (item instanceof PsiWhiteSpace || item instanceof PsiComment) {
                item = PsiTreeUtil.nextLeaf(item);
            }
            return item;
        }


        @Override
        public void removeArrayItem(PsiElement item) {
            PsiElement parent = item.getParent();
            if (!(parent instanceof JsonArray)) {
                throw new IllegalArgumentException("Cannot remove item from a non-array element");
            }
            PsiElement prev = skipWsBackward(PsiTreeUtil.prevLeaf(item));
            PsiElement next = skipWsForward(PsiTreeUtil.nextLeaf(item));
            if (prev instanceof LeafPsiElement && PsiUtilCore.getElementType(prev) == JsonElementTypes.COMMA) {
                prev.delete();
            }
            else if (next instanceof LeafPsiElement && PsiUtilCore.getElementType(next) == JsonElementTypes.COMMA) {
                next.delete();
            }
            item.delete();
        }

        @Override
        public PsiElement addArrayItem(PsiElement array, String itemValue) {
            if (!(array instanceof JsonArray)) {
                throw new IllegalArgumentException("Cannot add item to a non-array element");
            }

            JsonElementGenerator generator = new JsonElementGenerator(array.getProject());
            JsonValue arrayItem = generator.createArrayItemValue(itemValue);

            PsiElement addedItem = array.addBefore(arrayItem, array.getLastChild()); // we insert before closing bracket ']'
            if (((JsonArray)array).getValueList().size() > 1) {
                array.addBefore(generator.createComma(), addedItem);
            }
            return addedItem;
        }

        @Override
        public void ensureComma(@Nullable PsiElement self, @Nullable PsiElement newElement) {
            if (newElement instanceof JsonProperty && self instanceof JsonProperty) {
                PsiElement sibling = PsiTreeUtil.skipWhitespacesAndCommentsForward(self);
                if (sibling != null && sibling.getText().equals(",")) {
                    return;
                }
                PsiElement parent = self.getParent();
                assert parent != null;
                parent.addAfter(new JsonElementGenerator(self.getProject()).createComma(), self);
            }
        }

        @Override
        public void removeIfComma(@Nullable PsiElement forward) {
            if (forward instanceof LeafPsiElement leaf) {
                if (PsiUtilCore.getElementType(leaf) == JsonElementTypes.COMMA) {
                    forward.delete();
                }
                if (PsiUtilCore.getElementType(leaf) == JsonElementTypes.R_CURLY && PsiTreeUtil.skipWhitespacesBackward(leaf) instanceof LeafPsiElement prev &&
                    PsiUtilCore.getElementType(prev) == JsonElementTypes.COMMA) {
                    prev.delete();
                }
            }
        }

        @Override
        public boolean fixWhitespaceBefore(@Nullable PsiElement initialElement, @Nullable PsiElement element) {
            return true;
        }

        @Override
        public PsiElement adjustNewProperty(PsiElement element) {
            return element;
        }

        @Override
        public PsiElement adjustPropertyAnchor(LeafPsiElement element) {
            throw new IncorrectOperationException("Shouldn't use leafs for insertion in pure JSON!");
        }
    }
}
