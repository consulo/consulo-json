// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.JsonDialectUtil;
import com.intellij.json.JsonElementTypes;
import com.intellij.json.impl.pointer.JsonPointerPosition;
import com.intellij.json.psi.*;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.adapters.JsonJsonPropertyAdapter;
import consulo.document.util.TextRange;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ThreeState;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class JsonOriginalPsiWalker implements JsonLikePsiWalker {
  public static final JsonOriginalPsiWalker INSTANCE = new JsonOriginalPsiWalker();

  public boolean handles(@Nonnull PsiElement element) {
    PsiElement parent = element.getParent();
    return element instanceof JsonFile && JsonDialectUtil.isStandardJson(element)
           || parent != null && (element instanceof JsonElement || element instanceof LeafPsiElement && parent instanceof JsonElement)
             && JsonDialectUtil.isStandardJson(CompletionUtilCore.getOriginalOrSelf(parent));
  }

  @Override
  public boolean isQuotedString(@Nonnull PsiElement element) {
    return element instanceof JsonStringLiteral;
  }

  @Override
  public ThreeState isName(PsiElement element) {
    final PsiElement parent = element.getParent();
    if (parent instanceof JsonObject) {
      return ThreeState.YES;
    } else if (parent instanceof JsonProperty) {
      return PsiTreeUtil.isAncestor(((JsonProperty)parent).getNameElement(), element, false) ? ThreeState.YES : ThreeState.NO;
    }
    return ThreeState.NO;
  }

  @Override
  public boolean isPropertyWithValue(@Nonnull PsiElement element) {
    if (element instanceof JsonStringLiteral || element instanceof JsonReferenceExpression) {
      final PsiElement parent = element.getParent();
      if (!(parent instanceof JsonProperty) || ((JsonProperty)parent).getNameElement() != element) return false;
      element = parent;
    }
    return element instanceof JsonProperty && ((JsonProperty)element).getValue() != null;
  }

  @Override
  public PsiElement findElementToCheck(@Nonnull PsiElement element) {
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
  public @Nullable JsonPointerPosition findPosition(@Nonnull PsiElement element, boolean forceLastTransition) {
    JsonPointerPosition pos = new JsonPointerPosition();
    PsiElement current = element;
    while (! (current instanceof PsiFile)) {
      final PsiElement position = current;
      current = current.getParent();
      if (current instanceof JsonArray array) {
        final List<JsonValue> list = array.getValueList();
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
          final JsonValue value = list.get(i);
          if (value.equals(position)) {
            idx = i;
            break;
          }
        }
        pos.addPrecedingStep(idx);
      } else if (current instanceof JsonProperty) {
        final String propertyName = ((JsonProperty)current).getName();
        current = current.getParent();
        if (!(current instanceof JsonObject)) return null;//incorrect syntax?
        // if either value or not first in the chain - needed for completion variant
        if (position != element || forceLastTransition) {
          pos.addPrecedingStep(propertyName);
        }
      } else if (current instanceof JsonObject && position instanceof JsonProperty) {
        // if either value or not first in the chain - needed for completion variant
        if (position != element || forceLastTransition) {
          final String propertyName = ((JsonProperty)position).getName();
          pos.addPrecedingStep(propertyName);
        }
      } else if (current instanceof PsiFile) {
        break;
      } else {
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
  public boolean hasMissingCommaAfter(@Nonnull PsiElement element) {
    PsiElement current = element instanceof JsonProperty ? element : PsiTreeUtil.getParentOfType(element, JsonProperty.class);
    while (current != null && current.getNode().getElementType() != JsonElementTypes.COMMA) {
      current = current.getNextSibling();
    }
    int commaOffset = current == null ? Integer.MAX_VALUE : current.getTextRange().getStartOffset();
    final int offset = element.getTextRange().getStartOffset();
    final JsonObject object = PsiTreeUtil.getParentOfType(element, JsonObject.class);
    if (object != null) {
      for (JsonProperty property : object.getPropertyList()) {
        final int pOffset = property.getTextRange().getStartOffset();
        if (pOffset >= offset && !PsiTreeUtil.isAncestor(property, element, false)) {
          return pOffset < commaOffset;
        }
      }
    }
    return false;
  }

  @Override
  public Set<String> getPropertyNamesOfParentObject(@Nonnull PsiElement originalPosition, PsiElement computedPosition) {
    final JsonObject object = PsiTreeUtil.getParentOfType(computedPosition, JsonObject.class, false);
    if (object != null) {
      return object.getPropertyList().stream()
        .filter(p -> !requiresNameQuotes() || p.getNameElement() instanceof JsonStringLiteral)
        .map(p -> StringUtil.unquoteString(p.getName())).collect(Collectors.toSet());
    }
    return Collections.emptySet();
  }

  @Override
  public JsonPropertyAdapter getParentPropertyAdapter(@Nonnull PsiElement element) {
    final JsonProperty property = PsiTreeUtil.getParentOfType(element, JsonProperty.class, false);
    if (property == null) return null;
    return new JsonJsonPropertyAdapter(property);
  }

  @Override
  public boolean isTopJsonElement(@Nonnull PsiElement element) {
    return element instanceof PsiFile;
  }

  @Override
  public @Nullable JsonValueAdapter createValueAdapter(@Nonnull PsiElement element) {
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
  public @Nonnull Collection<PsiElement> getRoots(@Nonnull PsiFile file) {
    return file instanceof JsonFile ? ContainerUtil.createMaybeSingletonList(((JsonFile)file).getTopLevelValue()) : ContainerUtil.emptyList();
  }

  @Override
  public @Nullable PsiElement getPropertyNameElement(PsiElement property) {
    return property instanceof JsonProperty ? ((JsonProperty)property).getNameElement() : null;
  }

  @Override
  public TextRange adjustErrorHighlightingRange(@Nonnull PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent instanceof JsonFile) {
      PsiElement child = PsiTreeUtil.skipMatching(element.getFirstChild(), e -> e.getNextSibling(), e -> !(e instanceof JsonElement));
      return child == null ? element.getTextRange() : child.getTextRange();
    }
    return element.getTextRange();
  }

  private static class JsonOriginalSyntaxAdapter implements JsonLikeSyntaxAdapter {
    private static final JsonOriginalSyntaxAdapter INSTANCE = new JsonOriginalSyntaxAdapter();

    @Override
    public @Nonnull PsiElement createProperty(@Nonnull String name, @Nonnull String value, @Nonnull Project project) {
      return new JsonElementGenerator(project).createProperty(name, value);
    }

    @Override
    public @Nonnull PsiElement createEmptyArray(@Nonnull Project project, boolean preferInline) {
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
    public void removeArrayItem(@Nonnull PsiElement item) {
      PsiElement parent = item.getParent();
      if (!(parent instanceof JsonArray)) throw new IllegalArgumentException("Cannot remove item from a non-array element");
      PsiElement prev = skipWsBackward(PsiTreeUtil.prevLeaf(item));
      PsiElement next = skipWsForward(PsiTreeUtil.nextLeaf(item));
      if (prev instanceof LeafPsiElement && ((LeafPsiElement)prev).getElementType() == JsonElementTypes.COMMA) {
        prev.delete();
      }
      else if (next instanceof LeafPsiElement && ((LeafPsiElement)next).getElementType() == JsonElementTypes.COMMA) {
        next.delete();
      }
      item.delete();
    }

    @Override
    public @Nonnull PsiElement addArrayItem(@Nonnull PsiElement array, @Nonnull String itemValue) {
      if (!(array instanceof JsonArray)) throw new IllegalArgumentException("Cannot add item to a non-array element");

      JsonElementGenerator generator = new JsonElementGenerator(array.getProject());
      JsonValue arrayItem = generator.createArrayItemValue(itemValue);
      
      PsiElement addedItem = array.addBefore(arrayItem, array.getLastChild()); // we insert before closing bracket ']'
      if (((JsonArray)array).getValueList().size() > 1) {
        array.addBefore(generator.createComma(), addedItem);
      }
      return addedItem;
    }

    @Override
    public void ensureComma(PsiElement self, PsiElement newElement) {
      if (newElement instanceof JsonProperty && self instanceof JsonProperty) {
        PsiElement sibling = PsiTreeUtil.skipWhitespacesAndCommentsForward(self);
        if (sibling != null && sibling.getText().equals(",")) return;
        self.getParent().addAfter(new JsonElementGenerator(self.getProject()).createComma(), self);
      }
    }

    @Override
    public void removeIfComma(PsiElement forward) {
      if (forward instanceof LeafPsiElement leaf) {
        if (leaf.getElementType() == JsonElementTypes.COMMA) {
          forward.delete();
        }
        if (leaf.getElementType() == JsonElementTypes.R_CURLY && PsiTreeUtil.skipWhitespacesBackward(leaf) instanceof LeafPsiElement prev &&
            prev.getElementType() == JsonElementTypes.COMMA) {
          prev.delete();
        }
      }
    }

    @Override
    public boolean fixWhitespaceBefore(PsiElement initialElement, PsiElement element) {
      return true;
    }

    @Override
    public @Nonnull PsiElement adjustNewProperty(@Nonnull PsiElement element) {
      return element;
    }

    @Override
    public @Nonnull PsiElement adjustPropertyAnchor(@Nonnull LeafPsiElement element) {
      throw new IncorrectOperationException("Shouldn't use leafs for insertion in pure JSON!");
    }
  }
}
