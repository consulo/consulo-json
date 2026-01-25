// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.nestedCompletions;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.extension.JsonSchemaShorthandValueHandler;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.JsonSchemaObject;
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver;
import com.jetbrains.jsonSchema.impl.JsonSchemaType;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.function.BiFunction;

import static com.jetbrains.jsonSchema.impl.light.nodes.LightweightJsonSchemaObjectMerger.isNotBlank;

/**
 * Collects nested completions for a JSON schema object.
 * If node == null, it will just call collector once.
 *
 * @param project The project where the JSON schema is being used.
 * @param node A tree structure that represents a path through which we want nested completions.
 * @param completionPath The path of the completion in the schema.
 * @param collector The callback function to collect the nested completions.
 */
public class NestedCompletions {
  private static final String DUMMY_STRING = "jsonRulezzz111";

  public static void collectNestedCompletions(@Nonnull JsonSchemaObject schemaObject,
                                              @Nonnull Project project,
                                              @Nullable NestedCompletionsNode node,
                                              @Nullable SchemaPath completionPath,
                                              @Nonnull BiFunction<SchemaPath, JsonSchemaObject, CompletionNextStep> collector) {
    CompletionNextStep nextStep = collector.apply(completionPath, schemaObject); // Breadth first
    if (nextStep == CompletionNextStep.Stop) return;

    if (node == null) return;

    for (ChildNode child : node.getChildren()) {
      if (child instanceof ChildNode.OpenNode) {
        ChildNode.OpenNode openNode = (ChildNode.OpenNode) child;
        String name = openNode.getName();
        for (JsonSchemaObject subSchema : findSubSchemasByName(schemaObject, project, name)) {
          SchemaPath newPath = completionPath != null ? completionPath.slash(name) : SchemaPath.of(name);
          collectNestedCompletions(subSchema, project, openNode.getNode(), newPath, collector);
        }
      }
    }
  }

  @Nonnull
  private static Iterable<JsonSchemaObject> findSubSchemasByName(@Nonnull JsonSchemaObject schemaObject,
                                                                  @Nonnull Project project,
                                                                  @Nonnull String name) {
    JsonPointerPosition position = new JsonPointerPosition();
    position.addFollowingStep(name);
    JsonSchemaResolver resolver = new JsonSchemaResolver(project, schemaObject, position, null);
    return resolver.resolve();
  }

  @Nonnull
  public static PsiElement findChildBy(@Nonnull JsonLikePsiWalker walker,
                                       @Nullable SchemaPath path,
                                       @Nonnull PsiElement start) {
    if (path == null) return start;

    JsonObjectValueAdapter objectAdapter = findContainingObjectAdapter(walker, start);
    if (objectAdapter == null) return start;

    JsonValueAdapter result = findChildBy(objectAdapter, path.accessor(), 0);
    return result != null ? result.getDelegate() : start;
  }

  @Nullable
  private static JsonObjectValueAdapter findContainingObjectAdapter(@Nonnull JsonLikePsiWalker walker,
                                                                     @Nonnull PsiElement start) {
    PsiElement current = start;
    while (current != null) {
      JsonValueAdapter adapter = walker.createValueAdapter(current);
      if (adapter != null) {
        JsonObjectValueAdapter objectAdapter = adapter.getAsObject();
        if (objectAdapter != null) return objectAdapter;
      }
      current = current.getParent();
    }
    return null;
  }

  @Nullable
  private static JsonValueAdapter findChildBy(@Nonnull JsonObjectValueAdapter objectAdapter,
                                             @Nonnull List<String> path,
                                             int offset) {
    if (offset > path.size() - 1) return objectAdapter;

    for (JsonPropertyAdapter property : objectAdapter.getPropertyList()) {
      if (path.get(offset).equals(property.getName())) {
        List<JsonValueAdapter> values = property.getValues();
        if (!values.isEmpty()) {
          JsonObjectValueAdapter childObject = values.get(0).getAsObject();
          if (childObject != null) {
            return findChildBy(childObject, path, offset + 1);
          }
        }
      }
    }
    return null;
  }

  public static void expandMissingPropertiesAndMoveCaret(@Nonnull InsertionContext context,
                                                         @Nullable SchemaPath completionPath) {
    List<String> path = completionPath != null ? completionPath.accessor() : null;
    if (path == null || path.isEmpty()) return;

    PsiElement element = context.getFile().findElementAt(context.getStartOffset());
    if (element == null) return;
    element = element.getParent();
    if (element == null) return;

    JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element);
    if (walker == null) return;

    JsonObjectValueAdapter parentObject = getOrCreateParentObject(element, walker, context, path);
    if (parentObject == null) return;

    PsiElement newElement = doExpand(parentObject, path, walker, element, 0, null);
    if (newElement == null) return;

    JsonPropertyAdapter parentProperty = walker.getParentPropertyAdapter(element);
    PsiElement toDelete = null;
    if (parentProperty != null) {
      JsonValueAdapter nameValueAdapter = parentProperty.getNameValueAdapter();
      if (nameValueAdapter != null && nameValueAdapter.getDelegate() == element) {
        toDelete = parentProperty.getDelegate();
      }
    }
    if (toDelete == null) {
      toDelete = element;
    }
    cleanupWhitespacesAndDelete(toDelete, walker);

    // the inserted element might contain invalid psi and be re-invalidated after the document commit,
    // that's why we preserve the range instead and try restoring what was under
    SmartPsiFileRange pointer = SmartPointerManager.getInstance(context.getProject())
      .createSmartPsiFileRangePointer(newElement.getContainingFile(), newElement.getTextRange());

    PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(context.getDocument());

    TextRange range = pointer.getRange();
    if (range != null) {
      PsiElement e = context.getFile().findElementAt(range.getStartOffset());
      PsiElement psiElement = rewindToMeaningfulLeaf(e);
      if (psiElement != null) {
        context.getEditor().getCaretModel().moveToOffset(psiElement.getTextRange().getEndOffset());
      }
    }
  }

  @Nullable
  private static JsonObjectValueAdapter getOrCreateParentObject(@Nonnull PsiElement element,
                                                                @Nonnull JsonLikePsiWalker walker,
                                                                @Nonnull InsertionContext context,
                                                                @Nonnull List<String> path) {
    PsiElement container = element.getParent();
    if (container == null) return null;

    JsonValueAdapter containerAdapter = walker.createValueAdapter(container);
    if (containerAdapter != null && containerAdapter.isObject()) {
      return containerAdapter.getAsObject();
    }

    PsiElement grandParent = container.getParent();
    if (grandParent != null) {
      JsonValueAdapter grandParentAdapter = walker.createValueAdapter(grandParent);
      if (grandParentAdapter != null && grandParentAdapter.isObject()) {
        // the first condition is a hack for yaml, we need to invent a better solution here
        if (isNotBlank(walker.getDefaultObjectValue()) && walker.getParentPropertyAdapter(container) != null) {
          return grandParentAdapter.getAsObject();
        }
      }
    }

    PsiElement replacedElement = replaceAtCaretAndGetParentObject(element, walker, context, path);
    JsonValueAdapter replacedAdapter = walker.createValueAdapter(replacedElement);
    return replacedAdapter != null ? replacedAdapter.getAsObject() : null;
  }

  @Nullable
  public static PsiElement rewindToMeaningfulLeaf(@Nullable PsiElement element) {
    PsiElement meaningfulLeaf = PsiTreeUtil.getDeepestLast(element);
    while (meaningfulLeaf instanceof PsiWhiteSpace
           || meaningfulLeaf instanceof PsiErrorElement
           || (meaningfulLeaf instanceof LeafPsiElement && ",".equals(meaningfulLeaf.getText()))) {
      meaningfulLeaf = PsiTreeUtil.prevLeaf(meaningfulLeaf);
    }
    return meaningfulLeaf;
  }

  @Nonnull
  private static PsiElement replaceAtCaretAndGetParentObject(@Nonnull PsiElement element,
                                                             @Nonnull JsonLikePsiWalker walker,
                                                             @Nonnull InsertionContext context,
                                                             @Nonnull List<String> path) {
    PsiElement newProperty = walker.getSyntaxAdapter(context.getProject())
      .createProperty(path.get(0), DUMMY_STRING, context.getProject());

    JsonPropertyAdapter propertyAdapter = walker.getParentPropertyAdapter(newProperty);
    if (propertyAdapter != null) {
      List<JsonValueAdapter> values = propertyAdapter.getValues();
      if (!values.isEmpty()) {
        values.get(0).getDelegate().replace(element.copy());
      }
    }

    JsonPropertyAdapter parentAdapter = element.getParent() != null
                                        ? walker.getParentPropertyAdapter(element.getParent())
                                        : null;
    if (parentAdapter != null) {
      JsonValueAdapter nameValueAdapter = parentAdapter.getNameValueAdapter();
      if (nameValueAdapter != null && nameValueAdapter.getDelegate() == element) {
        return element.getParent().replace(newProperty).getParent();
      }
    }

    return element.replace(newProperty.getParent());
  }

  private static void cleanupWhitespacesAndDelete(@Nonnull PsiElement element,
                                                  @Nonnull JsonLikePsiWalker walker) {
    Set<PsiElement> toCleanup = new HashSet<>();

    // cleanup redundant whitespace
    PsiElement next = element.getNextSibling();
    while (next != null && next.getText().isBlank()) {
      PsiElement n = next;
      next = next.getNextSibling();
      toCleanup.add(n);
    }

    PsiElement prev = element.getPrevSibling();
    while (prev != null && prev.getText().isBlank()) {
      PsiElement n = prev;
      prev = prev.getPrevSibling();
      if (walker.getParentPropertyAdapter(prev) == null || element.getNextSibling() == null) {
        toCleanup.add(n);
      }
    }

    // we have to collect elements to avoid getting siblings of already deleted items
    for (PsiElement e : toCleanup) {
      e.delete();
    }
    element.delete();
  }

  @Nonnull
  private static JsonPropertyAdapter addNewPropertyWithObjectValue(@Nonnull JsonObjectValueAdapter parentObject,
                                                                   @Nonnull String propertyName,
                                                                   @Nonnull JsonLikePsiWalker walker,
                                                                   @Nonnull PsiElement element,
                                                                   @Nullable PsiElement fakeProperty) {
    Project project = parentObject.getDelegate().getProject();
    PsiElement newProperty = walker.getSyntaxAdapter(project).createProperty(propertyName, DUMMY_STRING, project);

    JsonPropertyAdapter propertyAdapter = walker.getParentPropertyAdapter(newProperty);
    if (propertyAdapter != null) {
      List<JsonValueAdapter> values = propertyAdapter.getValues();
      if (!values.isEmpty()) {
        values.get(0).getDelegate().replace(element.copy());
      }
    }

    PsiElement addedProperty = addBeforeOrAfter(parentObject, newProperty, element, fakeProperty);
    return walker.getParentPropertyAdapter(addedProperty);
  }

  @Nullable
  private static PsiElement doExpand(@Nonnull JsonObjectValueAdapter parentObject,
                                    @Nonnull List<String> completionPath,
                                    @Nonnull JsonLikePsiWalker walker,
                                    @Nonnull PsiElement element,
                                    int index,
                                    @Nullable PsiElement fakeProperty) {
    JsonPropertyAdapter parentProperty = walker.getParentPropertyAdapter(element);

    JsonPropertyAdapter property = null;
    for (JsonPropertyAdapter prop : parentObject.getPropertyList()) {
      if (completionPath.get(index).equals(prop.getName())) {
        // we match properties both by name and by parent
        // for languages with exotic syntaxes, there can be multiple same-named properties
        // at different levels within the same object
        if (parentProperty == null || walker.haveSameParentWithinObject(parentProperty.getDelegate(), prop.getDelegate())) {
          property = prop;
          break;
        }
      }
    }

    if (property == null) {
      property = addNewPropertyWithObjectValue(parentObject, completionPath.get(index), walker, element, fakeProperty);
    }

    if (fakeProperty != null) {
      cleanupWhitespacesAndDelete(fakeProperty, walker);
    }

    List<JsonValueAdapter> values = property.getValues();
    if (values.size() != 1) return null;
    JsonValueAdapter value = values.get(0);

    if (index + 1 < completionPath.size()) {
      Project project = parentObject.getDelegate().getProject();
      PsiElement fake = walker.getSyntaxAdapter(project).createProperty(DUMMY_STRING, DUMMY_STRING, project);

      PsiElement originalObject = value.isObject() ? value.getDelegate() : expandOrNull(walker, value);
      PsiElement newValue = originalObject != null ? originalObject : value.getDelegate().replace(fake.getParent());

      switchToObjectSeparator(walker, property.getDelegate());

      JsonValueAdapter newValueAdapter = walker.createValueAdapter(newValue);
      if (newValueAdapter == null || !newValueAdapter.isObject()) return null;

      JsonObjectValueAdapter newValueAsObject = newValueAdapter.getAsObject();
      return doExpand(newValueAsObject, completionPath, walker, element, index + 1,
                      originalObject != null ? null : newValueAsObject.getPropertyList().get(0).getDelegate());
    } else {
      PsiElement movedElement;
      if (value.isObject()) {
        JsonPropertyAdapter parentPropAdapter = walker.getParentPropertyAdapter(element);
        PsiElement elementToAdd;

        if (parentPropAdapter != null) {
          JsonValueAdapter nameValueAdapter = parentPropAdapter.getNameValueAdapter();
          if (nameValueAdapter != null && nameValueAdapter.getDelegate() == element) {
            elementToAdd = parentPropAdapter.getDelegate();
          } else if (walker.createValueAdapter(element) != null && walker.createValueAdapter(element).isStringLiteral()) {
            PsiElement prop = walker.getSyntaxAdapter(element.getProject())
              .createProperty(StringUtil.unquoteString(element.getText()), DUMMY_STRING, element.getProject());
            removePropertyValue(walker, prop);
            elementToAdd = prop;
          } else {
            elementToAdd = element.copy();
          }
        } else if (walker.createValueAdapter(element) != null && walker.createValueAdapter(element).isStringLiteral()) {
          PsiElement prop = walker.getSyntaxAdapter(element.getProject())
            .createProperty(StringUtil.unquoteString(element.getText()), DUMMY_STRING, element.getProject());
          removePropertyValue(walker, prop);
          elementToAdd = prop;
        } else {
          elementToAdd = element.copy();
        }

        movedElement = addBeforeOrAfter(value, elementToAdd, element, fakeProperty);
      } else {
        PsiElement newElement = replaceValueForNesting(walker, value, element);
        switchToObjectSeparator(walker, property.getDelegate());
        movedElement = newElement;
      }
      return movedElement;
    }
  }

  private static void switchToObjectSeparator(@Nonnull JsonLikePsiWalker walker, @Nonnull PsiElement node) {
    String nonObjectSeparator = walker.getPropertyValueSeparator(null).trim();
    String objectSeparator = walker.getPropertyValueSeparator(JsonSchemaType._object).trim();

    if (!nonObjectSeparator.equals(objectSeparator)) {
      for (PsiElement child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
        if (child instanceof LeafPsiElement && child.getParent() == node && nonObjectSeparator.equals(child.getText())) {
          if (objectSeparator.isBlank()) {
            deleteWithWsAround(child, false);
          } else {
            child.replace(createLeaf(objectSeparator, child));
          }
          break;
        }
      }
    }
  }

  private static void removePropertyValue(@Nonnull JsonLikePsiWalker walker, @Nonnull PsiElement element) {
    JsonPropertyAdapter adapter = walker.getParentPropertyAdapter(element);
    if (adapter != null) {
      List<JsonValueAdapter> values = adapter.getValues();
      if (values.size() == 1) {
        values.get(0).getDelegate().delete();
      }
    }

    String separator = walker.getPropertyValueSeparator(null).trim();
    for (PsiElement child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
      if (separator.equals(child.getText())) {
        deleteWithWsAround(child, true);
        break;
      }
    }
  }

  private static void deleteWithWsAround(@Nonnull PsiElement element, boolean deleteBefore) {
    if (deleteBefore) {
      PsiElement prev = element.getPrevSibling();
      if (prev != null && prev.getText().isBlank()) {
        prev.delete();
      }
    }
    PsiElement next = element.getNextSibling();
    if (next != null && next.getText().isBlank()) {
      next.delete();
    }
    element.delete();
  }

  @Nonnull
  private static PsiElement addBeforeOrAfter(@Nonnull JsonValueAdapter value,
                                            @Nonnull PsiElement elementToAdd,
                                            @Nonnull PsiElement element,
                                            @Nullable PsiElement fakeProperty) {
    List<JsonPropertyAdapter> properties = value.getAsObject() != null
                                           ? value.getAsObject().getPropertyList()
                                           : Collections.emptyList();

    JsonPropertyAdapter firstProperty = !properties.isEmpty() ? properties.get(0) : null;
    JsonPropertyAdapter lastPropertyBefore = null;

    for (JsonPropertyAdapter prop : properties) {
      if (element.getTextRange().getStartOffset() >= prop.getDelegate().getTextRange().getEndOffset()) {
        lastPropertyBefore = prop;
      }
    }

    if (lastPropertyBefore != null) {
      PsiElement newElement = lastPropertyBefore.getDelegate().getParent()
        .addAfter(elementToAdd, lastPropertyBefore.getDelegate());

      if (lastPropertyBefore.getDelegate() != fakeProperty) {
        JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element);
        if (walker != null) {
          walker.getSyntaxAdapter(element.getProject()).ensureComma(lastPropertyBefore.getDelegate(), newElement);
        }
      }
      return newElement;
    } else {
      PsiElement parent = firstProperty != null ? firstProperty.getDelegate().getParent() : value.getDelegate();
      PsiElement newElement = parent.addBefore(elementToAdd, firstProperty != null ? firstProperty.getDelegate() : null);

      if (firstProperty != null && firstProperty.getDelegate() != fakeProperty) {
        JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(newElement);
        if (walker != null) {
          walker.getSyntaxAdapter(newElement.getProject()).ensureComma(newElement, firstProperty.getDelegate());
        }
      }
      return newElement;
    }
  }

  @Nullable
  private static PsiElement expandOrNull(@Nonnull JsonLikePsiWalker walker,
                                        @Nonnull JsonValueAdapter shorthandValue) {
    Project project = shorthandValue.getDelegate().getProject();

    List<String> stepNames = null;
    JsonPointerPosition position = walker.findPosition(shorthandValue.getDelegate(), false);
    if (position != null) {
      stepNames = position.getStepNames();
    }

    JsonSchemaShorthandValueHandler.KeyValue expandedValue = expandShorthandIfApplicable(shorthandValue, stepNames);
    if (expandedValue == null) return null;

    PsiElement expandedProperty = walker.getSyntaxAdapter(project)
      .createProperty(expandedValue.getKey(), expandedValue.getValue(), project);
    return shorthandValue.getDelegate().replace(expandedProperty.getParent());
  }

  @Nonnull
  private static PsiElement replaceValueForNesting(@Nonnull JsonLikePsiWalker walker,
                                                  @Nonnull JsonValueAdapter value,
                                                  @Nonnull PsiElement element) {
    Project project = value.getDelegate().getProject();
    String name = StringUtil.unquoteString(element.getText());

    PsiElement newProperty = walker.getSyntaxAdapter(project).createProperty(name, DUMMY_STRING, project);

    List<String> stepNames = null;
    JsonPointerPosition position = walker.findPosition(value.getDelegate(), false);
    if (position != null) {
      stepNames = position.getStepNames();
    }

    JsonSchemaShorthandValueHandler.KeyValue expandedValue = expandShorthandIfApplicable(value, stepNames);

    PsiElement parentObject = newProperty.getParent();
    JsonValueAdapter parentAdapter = walker.createValueAdapter(parentObject);
    if (parentAdapter != null && parentAdapter.getAsObject() != null) {
      JsonPropertyAdapter singleProp = parentAdapter.getAsObject().getPropertyList().get(0);
      removePropertyValue(walker, singleProp.getDelegate());
    }

    PsiElement newValue = value.getDelegate().replace(parentObject);

    JsonValueAdapter newValueAdapter = walker.createValueAdapter(newValue);
    if (newValueAdapter == null || newValueAdapter.getAsObject() == null) return newValue;

    JsonObjectValueAdapter parentObjectAdapter = newValueAdapter.getAsObject();
    JsonPropertyAdapter newPropertyAdapter = parentObjectAdapter.getPropertyList().get(0);

    if (expandedValue != null) {
      String expandedKey = expandedValue.getKey();
      String propName = newPropertyAdapter.getName();
      String propText = StringUtil.unquoteString(newPropertyAdapter.getDelegate().getText());

      // if we are expanding the same property as in completion - don't perform the expansion here
      // the completion will expand itself
      if (!expandedKey.equals(propName) && !expandedKey.equals(propText)) {
        PsiElement expandedProperty = walker.getSyntaxAdapter(project)
          .createProperty(expandedValue.getKey(), expandedValue.getValue(), project);
        PsiElement addedExpandedProperty = parentObjectAdapter.getDelegate()
          .addBefore(expandedProperty, newPropertyAdapter.getDelegate());
        walker.getSyntaxAdapter(project).ensureComma(addedExpandedProperty, newPropertyAdapter.getDelegate());
      }
    }

    return newPropertyAdapter.getDelegate();
  }

  @Nullable
  private static JsonSchemaShorthandValueHandler.KeyValue expandShorthandIfApplicable(@Nonnull JsonValueAdapter value,
                                                                                      @Nullable List<String> elementPath) {
    if (elementPath == null) return null;

    if (!value.isNumberLiteral() && !value.isStringLiteral() && !value.isBooleanLiteral() && !value.isNull()) {
      return null;
    }

    String shorthandValue = StringUtil.unquoteString(value.getDelegate().getText());

    for (JsonSchemaShorthandValueHandler handler : JsonSchemaShorthandValueHandler.EXTENSION_POINT_NAME.getExtensionList()) {
      if (handler.isApplicable(value.getDelegate().getContainingFile())) {
        JsonSchemaShorthandValueHandler.KeyValue result = handler.expandShorthandValue(elementPath, shorthandValue);
        if (result != null) {
          return result;
        }
      }
    }

    return null;
  }

  @Nonnull
  private static LeafPsiElement createLeaf(@Nonnull String content, @Nonnull PsiElement context) {
    PsiFileFactory psiFileFactory = PsiFileFactory.getInstance(context.getProject());
    String extension = context.getContainingFile().getVirtualFile().getExtension();
    PsiFile file = psiFileFactory.createFileFromText("dummy." + extension,
                                                     context.getContainingFile().getFileType(),
                                                     content);

    return PsiTreeUtil.findChildrenOfType(file, LeafPsiElement.class).stream()
      .filter(leaf -> content.equals(leaf.getText()))
      .findFirst()
      .orElseThrow();
  }
}

enum CompletionNextStep {
  Continue,
  Stop
}
