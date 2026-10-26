// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema.impl.nestedCompletions;

import com.intellij.json.pointer.JsonPointerPosition;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.JsonSchemaShorthandValueHandler;
import com.jetbrains.jsonSchema.extension.adapter.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.internal.JsonSchemaResolver;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import com.jetbrains.jsonSchema.walker.JsonLikeSyntaxAdapter;
import consulo.application.Application;
import consulo.document.util.Segment;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.psi.LeafPsiElement;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileFactory;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiFileRange;
import consulo.language.psi.SyntaxTraverser;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

public final class NestedCompletions {
    private static final String dummyString = "jsonRulezzz111";

    private NestedCompletions() {
    }

    /**
     * Collects nested completions for a JSON schema object.
     * If {@code node == null}, it will just call collector once.
     *
     * @param project        The project where the JSON schema is being used.
     * @param node           A tree structure that represents a path through which we want nested completions.
     * @param completionPath The path of the completion in the schema.
     * @param collector      The callback function to collect the nested completions.
     */
    public static void collectNestedCompletions(JsonSchemaObject schemaObject,
                                                Project project,
                                                @Nullable NestedCompletionsNode node,
                                                @Nullable SchemaPath completionPath,
                                                BiFunction<@Nullable SchemaPath, JsonSchemaObject, CompletionNextStep> collector) {
        CompletionNextStep nextStep = collector.apply(completionPath, schemaObject); // Breadth first
        if (nextStep == CompletionNextStep.Stop) {
            return;
        }

        if (node == null) {
            return;
        }
        for (ChildNode child : node.getChildren()) {
            if (child instanceof ChildNode.OpenNode openNode) {
                String name = openNode.getName();
                for (JsonSchemaObject subSchema : findSubSchemasByName(schemaObject, project, name)) {
                    collectNestedCompletions(subSchema, project, openNode.getNode(), SchemaPath.div(completionPath, name), collector);
                }
            }
        }
    }

    private static Iterable<JsonSchemaObject> findSubSchemasByName(JsonSchemaObject schemaObject, Project project, String name) {
        JsonPointerPosition position = new JsonPointerPosition();
        position.addFollowingStep(name);
        return new JsonSchemaResolver(project, schemaObject, position, (JsonValueAdapter) null).resolve();
    }

    public static PsiElement findChildBy(JsonLikePsiWalker walker, @Nullable SchemaPath path, PsiElement start) {
        if (path != null) {
            JsonObjectValueAdapter containingObjectAdapter = findContainingObjectAdapter(walker, start);
            JsonValueAdapter child = containingObjectAdapter == null ? null : findChildBy(containingObjectAdapter, path.accessor(), 0);
            if (child != null) {
                return child.getDelegate();
            }
        }
        return start;
    }

    private static @Nullable JsonObjectValueAdapter findContainingObjectAdapter(JsonLikePsiWalker walker, PsiElement start) {
        for (PsiElement element = start; element != null; element = element.getParent()) {
            JsonValueAdapter adapter = walker.createValueAdapter(element);
            JsonObjectValueAdapter asObject = adapter == null ? null : adapter.getAsObject();
            if (asObject != null) {
                return asObject;
            }
        }
        return null;
    }

    public static @Nullable JsonValueAdapter findChildBy(JsonObjectValueAdapter objectAdapter, List<String> path, int offset) {
        if (offset > path.size() - 1) {
            return objectAdapter;
        }
        for (JsonPropertyAdapter property : objectAdapter.getPropertyList()) {
            if (path.get(offset).equals(property.getName())) {
                Collection<JsonValueAdapter> values = property.getValues();
                JsonValueAdapter first = values.isEmpty() ? null : values.iterator().next();
                JsonObjectValueAdapter asObject = first == null ? null : first.getAsObject();
                return asObject == null ? null : findChildBy(asObject, path, offset + 1);
            }
        }
        return null;
    }

    public static void expandMissingPropertiesAndMoveCaret(InsertionContext context, @Nullable SchemaPath completionPath) {
        List<String> path = completionPath == null ? null : completionPath.accessor();
        if (path == null || path.isEmpty()) {
            return;
        }
        PsiElement elementAt = context.getFile().findElementAt(context.getStartOffset());
        PsiElement element = elementAt == null ? null : elementAt.getParent();
        if (element == null) {
            return;
        }
        JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element);
        if (walker == null) {
            return;
        }
        JsonObjectValueAdapter parentObject = getOrCreateParentObject(element, walker, context, path);
        if (parentObject == null) {
            return;
        }
        PsiElement newElement = doExpand(parentObject, path, walker, element, 0, null);
        if (newElement == null) {
            return;
        }
        JsonPropertyAdapter parentPropertyAdapter = walker.getParentPropertyAdapter(element);
        JsonValueAdapter nameValueAdapter = parentPropertyAdapter == null ? null : parentPropertyAdapter.getNameValueAdapter();
        PsiElement toDelete = parentPropertyAdapter != null && nameValueAdapter != null && nameValueAdapter.getDelegate() == element
            ? parentPropertyAdapter.getDelegate()
            : element;
        cleanupWhitespacesAndDelete(toDelete, walker);
        // the inserted element might contain invalid psi and be re-invalidated after the document commit,
        // that's why we preserve the range instead and try restoring what was under
        SmartPsiFileRange pointer = SmartPointerManager.getInstance(context.getProject())
            .createSmartPsiFileRangePointer(Objects.requireNonNull(newElement.getContainingFile()), newElement.getTextRange());
        PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(context.getDocument());
        Segment range = pointer.getRange();
        assert range != null;
        PsiElement e = context.getFile().findElementAt(range.getStartOffset());
        PsiElement psiElement = rewindToMeaningfulLeaf(e);
        if (psiElement != null) {
            context.getEditor().getCaretModel().moveToOffset(psiElement.getTextRange().getEndOffset());
        }
    }

    private static @Nullable JsonObjectValueAdapter getOrCreateParentObject(PsiElement element,
                                                                            JsonLikePsiWalker walker,
                                                                            InsertionContext context,
                                                                            List<String> path) {
        PsiElement container = element.getParent();
        if (container == null) {
            return null;
        }
        JsonValueAdapter containerAdapter = walker.createValueAdapter(container);
        JsonObjectValueAdapter containerObject = containerAdapter == null ? null : containerAdapter.getAsObject();
        if (containerObject != null) {
            return containerObject;
        }
        PsiElement containerParent = container.getParent();
        JsonValueAdapter containerParentAdapter = containerParent == null ? null : walker.createValueAdapter(containerParent);
        JsonObjectValueAdapter containerParentObject = containerParentAdapter == null ? null : containerParentAdapter.getAsObject();
        // the first condition is a hack for yaml, we need to invent a better solution here
        if (containerParentObject != null &&
            !StringUtil.isEmptyOrSpaces(walker.getDefaultObjectValue()) &&
            walker.getParentPropertyAdapter(container) != null) {
            return containerParentObject;
        }
        PsiElement replaced = replaceAtCaretAndGetParentObject(element, walker, context, path);
        JsonValueAdapter replacedAdapter = walker.createValueAdapter(replaced);
        return replacedAdapter == null ? null : replacedAdapter.getAsObject();
    }

    public static @Nullable PsiElement rewindToMeaningfulLeaf(@Nullable PsiElement element) {
        PsiElement meaningfulLeaf = element == null ? null : PsiTreeUtil.getDeepestLast(element);
        while (meaningfulLeaf instanceof PsiWhiteSpace || meaningfulLeaf instanceof PsiErrorElement ||
            meaningfulLeaf instanceof LeafPsiElement && meaningfulLeaf.getText().equals(",")) {
            meaningfulLeaf = PsiTreeUtil.prevLeaf(meaningfulLeaf);
        }
        return meaningfulLeaf;
    }

    private static PsiElement replaceAtCaretAndGetParentObject(PsiElement element,
                                                               JsonLikePsiWalker walker,
                                                               InsertionContext context,
                                                               List<String> path) {
        JsonLikeSyntaxAdapter syntaxAdapter = getSyntaxAdapter(walker, context.getProject());
        PsiElement newProperty = syntaxAdapter.createProperty(path.get(0), dummyString, context.getProject());
        single(getParentPropertyAdapter(walker, newProperty).getValues()).getDelegate().replace(element.copy());

        PsiElement parent = element.getParent();
        JsonPropertyAdapter parentAdapter = parent == null ? null : walker.getParentPropertyAdapter(parent);
        JsonValueAdapter parentNameValueAdapter = parentAdapter == null ? null : parentAdapter.getNameValueAdapter();
        if (parent != null && parentNameValueAdapter != null && parentNameValueAdapter.getDelegate() == element) {
            return Objects.requireNonNull(parent.replace(newProperty).getParent());
        }

        return element.replace(Objects.requireNonNull(newProperty.getParent()));
    }

    private static void cleanupWhitespacesAndDelete(PsiElement it, JsonLikePsiWalker walker) {
        Set<PsiElement> toCleanup = new LinkedHashSet<>();
        // cleanup redundant whitespace
        PsiElement next = it.getNextSibling();
        while (next != null && StringUtil.isEmptyOrSpaces(next.getText())) {
            PsiElement n = next;
            next = next.getNextSibling();
            toCleanup.add(n);
        }
        PsiElement prev = it.getPrevSibling();
        while (prev != null && StringUtil.isEmptyOrSpaces(prev.getText())) {
            PsiElement n = prev;
            prev = prev.getPrevSibling();
            if (prev == null || walker.getParentPropertyAdapter(prev) == null || it.getNextSibling() == null) {
                toCleanup.add(n);
            }
        }
        // we have to collect elements to avoid getting siblings of already deleted items
        for (PsiElement element : toCleanup) {
            element.delete();
        }
        it.delete();
    }

    private static JsonPropertyAdapter addNewPropertyWithObjectValue(JsonObjectValueAdapter parentObject,
                                                                     String propertyName,
                                                                     JsonLikePsiWalker walker,
                                                                     PsiElement element,
                                                                     @Nullable PsiElement fakeProperty) {
        Project project = parentObject.getDelegate().getProject();
        JsonLikeSyntaxAdapter syntaxAdapter = getSyntaxAdapter(walker, project);
        PsiElement property = syntaxAdapter.createProperty(propertyName, dummyString, project);
        single(getParentPropertyAdapter(walker, property).getValues()).getDelegate().replace(element.copy());
        PsiElement added = addBeforeOrAfter(parentObject, property, element, fakeProperty);
        return getParentPropertyAdapter(walker, added);
    }

    private static @Nullable PsiElement doExpand(JsonObjectValueAdapter parentObject,
                                                 List<String> completionPath,
                                                 JsonLikePsiWalker walker,
                                                 PsiElement element,
                                                 int index,
                                                 @Nullable PsiElement fakeProperty) {
        JsonPropertyAdapter parentProperty = walker.getParentPropertyAdapter(element);
        JsonPropertyAdapter property = null;
        for (JsonPropertyAdapter it : parentObject.getPropertyList()) {
            // we match properties both by name and by parent
            // for languages with exotic syntaxes, there can be multiple same-named properties
            //  at different levels within the same object
            if (completionPath.get(index).equals(it.getName()) &&
                (parentProperty == null || walker.haveSameParentWithinObject(parentProperty.getDelegate(), it.getDelegate()))) {
                property = it;
                break;
            }
        }
        if (property == null) {
            property = addNewPropertyWithObjectValue(parentObject, completionPath.get(index), walker, element, fakeProperty);
        }
        if (fakeProperty != null) {
            cleanupWhitespacesAndDelete(fakeProperty, walker);
        }
        Collection<JsonValueAdapter> values = property.getValues();
        JsonValueAdapter value = values.size() == 1 ? values.iterator().next() : null;
        if (value == null) {
            return null;
        }
        if (index + 1 < completionPath.size()) {
            Project project = parentObject.getDelegate().getProject();
            PsiElement fake = getSyntaxAdapter(walker, project).createProperty(dummyString, dummyString, project);
            PsiElement originalObject = value.isObject() ? value.getDelegate() : expandOrNull(walker, value);
            PsiElement newValue = originalObject != null ? originalObject : value.getDelegate().replace(Objects.requireNonNull(fake.getParent()));
            switchToObjectSeparator(walker, property.getDelegate());
            JsonValueAdapter newValueAdapter = walker.createValueAdapter(newValue);
            assert newValueAdapter != null;
            JsonObjectValueAdapter newValueAsObject = newValueAdapter.getAsObject();
            assert newValueAsObject != null;
            return doExpand(newValueAsObject, completionPath, walker, element, index + 1,
                originalObject != null ? null : single(newValueAsObject.getPropertyList()).getDelegate());
        }
        else {
            PsiElement movedElement;
            if (value.isObject()) {
                JsonPropertyAdapter elementParentProperty = walker.getParentPropertyAdapter(element);
                JsonValueAdapter elementNameValueAdapter = elementParentProperty == null ? null : elementParentProperty.getNameValueAdapter();
                PsiElement elementToAdd;
                if (elementParentProperty != null && elementNameValueAdapter != null && elementNameValueAdapter.getDelegate() == element) {
                    elementToAdd = elementParentProperty.getDelegate();
                }
                else {
                    JsonValueAdapter elementAdapter = walker.createValueAdapter(element);
                    if (elementAdapter != null && elementAdapter.isStringLiteral()) {
                        elementToAdd = getSyntaxAdapter(walker, element.getProject()).createProperty(
                            StringUtil.unquoteString(element.getText()), dummyString, element.getProject()
                        );
                        removePropertyValue(walker, elementToAdd);
                    }
                    else {
                        elementToAdd = element.copy();
                    }
                }
                movedElement = addBeforeOrAfter(value, elementToAdd, element, fakeProperty);
            }
            else {
                PsiElement newElement = replaceValueForNesting(walker, value, element);
                switchToObjectSeparator(walker, property.getDelegate());
                movedElement = newElement;
            }
            return movedElement;
        }
    }

    private static void switchToObjectSeparator(JsonLikePsiWalker walker, PsiElement node) {
        String nonObjectSeparator = walker.getPropertyValueSeparator(null).trim();
        String objectSeparator = walker.getPropertyValueSeparator(JsonSchemaType._object).trim();
        if (!nonObjectSeparator.equals(objectSeparator)) {
            for (PsiElement it : childLeafs(node)) {
                if (it.getParent() == node && it.getText().equals(nonObjectSeparator)) {
                    if (StringUtil.isEmptyOrSpaces(objectSeparator)) {
                        deleteWithWsAround(it, false);
                    }
                    else {
                        it.replace(createLeaf(objectSeparator, it));
                    }
                    break;
                }
            }
        }
    }

    private static void removePropertyValue(JsonLikePsiWalker walker, PsiElement it) {
        Collection<JsonValueAdapter> values = getParentPropertyAdapter(walker, it).getValues();
        if (values.size() == 1) {
            values.iterator().next().getDelegate().delete();
        }
        String separator = walker.getPropertyValueSeparator(null).trim();
        for (PsiElement leaf : childLeafs(it)) {
            if (leaf.getText().equals(separator)) {
                deleteWithWsAround(leaf, true);
                break;
            }
        }
    }

    private static void deleteWithWsAround(PsiElement it, boolean deleteBefore) {
        if (deleteBefore) {
            PsiElement prevSibling = it.getPrevSibling();
            if (prevSibling != null && StringUtil.isEmptyOrSpaces(prevSibling.getText())) {
                prevSibling.delete();
            }
        }
        PsiElement nextSibling = it.getNextSibling();
        if (nextSibling != null && StringUtil.isEmptyOrSpaces(nextSibling.getText())) {
            nextSibling.delete();
        }
        it.delete();
    }

    private static PsiElement addBeforeOrAfter(JsonValueAdapter value,
                                               PsiElement elementToAdd,
                                               PsiElement element,
                                               @Nullable PsiElement fakeProperty) {
        JsonObjectValueAdapter asObject = value.getAsObject();
        List<JsonPropertyAdapter> properties = asObject == null ? List.of() : asObject.getPropertyList();
        JsonPropertyAdapter firstProperty = properties.isEmpty() ? null : properties.get(0);
        JsonPropertyAdapter lastPropertyBefore = null;
        for (int i = properties.size() - 1; i >= 0; i--) {
            JsonPropertyAdapter property = properties.get(i);
            if (element.getTextRange().getStartOffset() >= property.getDelegate().getTextRange().getEndOffset()) {
                lastPropertyBefore = property;
                break;
            }
        }
        if (lastPropertyBefore != null) {
            PsiElement newElement = Objects.requireNonNull(lastPropertyBefore.getDelegate().getParent()).addAfter(elementToAdd, lastPropertyBefore.getDelegate());
            if (lastPropertyBefore.getDelegate() != fakeProperty) {
                JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element);
                JsonLikeSyntaxAdapter syntaxAdapter = walker == null ? null : walker.getSyntaxAdapter(element.getProject());
                if (syntaxAdapter != null) {
                    syntaxAdapter.ensureComma(lastPropertyBefore.getDelegate(), newElement);
                }
            }
            return newElement;
        }
        else {
            PsiElement firstPropertyDelegate = firstProperty == null ? null : firstProperty.getDelegate();
            PsiElement container = firstPropertyDelegate != null ? firstPropertyDelegate.getParent() : value.getDelegate();
            assert container != null;
            PsiElement newElement = container.addBefore(elementToAdd, firstPropertyDelegate);
            if (firstPropertyDelegate != null && firstPropertyDelegate != fakeProperty) {
                JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(newElement);
                JsonLikeSyntaxAdapter syntaxAdapter = walker == null ? null : walker.getSyntaxAdapter(newElement.getProject());
                if (syntaxAdapter != null) {
                    syntaxAdapter.ensureComma(newElement, firstPropertyDelegate);
                }
            }
            return newElement;
        }
    }

    private static @Nullable PsiElement expandOrNull(JsonLikePsiWalker walker, JsonValueAdapter shorthandValue) {
        Project project = shorthandValue.getDelegate().getProject();
        JsonLikeSyntaxAdapter syntaxAdapter = getSyntaxAdapter(walker, project);
        JsonPointerPosition position = walker.findPosition(shorthandValue.getDelegate(), false);
        JsonSchemaShorthandValueHandler.KeyValue expandedValue =
            expandShorthandIfApplicable(shorthandValue, position == null ? null : position.getStepNames());
        if (expandedValue == null) {
            return null;
        }
        PsiElement expandedProperty = syntaxAdapter.createProperty(expandedValue.getKey(), expandedValue.getValue(), project);
        return shorthandValue.getDelegate().replace(Objects.requireNonNull(expandedProperty.getParent()));
    }

    private static PsiElement replaceValueForNesting(JsonLikePsiWalker walker, JsonValueAdapter value, PsiElement element) {
        Project project = value.getDelegate().getProject();
        JsonLikeSyntaxAdapter syntaxAdapter = getSyntaxAdapter(walker, project);
        String name = StringUtil.unquoteString(element.getText());
        PsiElement newProperty = syntaxAdapter.createProperty(name, dummyString, project);
        JsonPointerPosition position = walker.findPosition(value.getDelegate(), false);
        JsonSchemaShorthandValueHandler.KeyValue expandedValue =
            expandShorthandIfApplicable(value, position == null ? null : position.getStepNames());
        PsiElement parentObject = Objects.requireNonNull(newProperty.getParent());
        JsonValueAdapter parentObjectValueAdapter = walker.createValueAdapter(parentObject);
        assert parentObjectValueAdapter != null;
        JsonObjectValueAdapter parentObjectValueAsObject = parentObjectValueAdapter.getAsObject();
        assert parentObjectValueAsObject != null;
        removePropertyValue(walker, single(parentObjectValueAsObject.getPropertyList()).getDelegate());
        PsiElement newValue = value.getDelegate().replace(parentObject);

        JsonValueAdapter newValueAdapter = walker.createValueAdapter(newValue);
        assert newValueAdapter != null;
        JsonObjectValueAdapter parentObjectAdapter = newValueAdapter.getAsObject();
        assert parentObjectAdapter != null;
        JsonPropertyAdapter newPropertyAdapter = single(parentObjectAdapter.getPropertyList());
        // if we are expanding the same property as in completion - don't perform the expansion here
        // the completion will expand itself
        if (expandedValue != null &&
            !expandedValue.getKey().equals(newPropertyAdapter.getName()) &&
            !expandedValue.getKey().equals(StringUtil.unquoteString(newPropertyAdapter.getDelegate().getText()))) {
            PsiElement expandedProperty = syntaxAdapter.createProperty(expandedValue.getKey(), expandedValue.getValue(), project);
            PsiElement addedExpandedProperty = parentObjectAdapter.getDelegate().addBefore(expandedProperty, newPropertyAdapter.getDelegate());
            syntaxAdapter.ensureComma(addedExpandedProperty, newPropertyAdapter.getDelegate());
        }

        return newPropertyAdapter.getDelegate();
    }

    private static JsonSchemaShorthandValueHandler.@Nullable KeyValue expandShorthandIfApplicable(JsonValueAdapter value,
                                                                                                 @Nullable List<String> elementPath) {
        if (elementPath == null) {
            return null;
        }
        if (!value.isNumberLiteral() && !value.isStringLiteral() && !value.isBooleanLiteral() && !value.isNull()) {
            return null;
        }
        String shorthandValue = StringUtil.unquoteString(value.getDelegate().getText());
        PsiFile containingFile = Objects.requireNonNull(value.getDelegate().getContainingFile());
        List<JsonSchemaShorthandValueHandler.KeyValue> expandedValues = new ArrayList<>();
        Application.get().getExtensionPoint(JsonSchemaShorthandValueHandler.class).forEach(handler -> {
            if (handler.isApplicable(containingFile)) {
                JsonSchemaShorthandValueHandler.KeyValue expanded = handler.expandShorthandValue(elementPath, shorthandValue);
                if (expanded != null) {
                    expandedValues.add(expanded);
                }
            }
        });
        return expandedValues.size() == 1 ? expandedValues.get(0) : null;
    }

    private static LeafPsiElement createLeaf(String content, PsiElement context) {
        PsiFileFactory psiFileFactory = PsiFileFactory.getInstance(context.getProject());
        PsiFile containingFile = Objects.requireNonNull(context.getContainingFile());
        PsiFile file = psiFileFactory.createFileFromText("dummy." + Objects.requireNonNull(containingFile.getVirtualFile()).getExtension(),
            containingFile.getFileType(), content);
        for (LeafPsiElement leaf : PsiTreeUtil.findChildrenOfType(file, LeafPsiElement.class)) {
            if (leaf.getText().equals(content)) {
                return leaf;
            }
        }
        throw new IllegalStateException("No leaf with text " + content);
    }

    private static List<PsiElement> childLeafs(PsiElement element) {
        return SyntaxTraverser.psiTraverser(element).traverse().filter(e -> e.getFirstChild() == null).toList();
    }

    private static JsonLikeSyntaxAdapter getSyntaxAdapter(JsonLikePsiWalker walker, Project project) {
        JsonLikeSyntaxAdapter syntaxAdapter = walker.getSyntaxAdapter(project);
        assert syntaxAdapter != null;
        return syntaxAdapter;
    }

    private static JsonPropertyAdapter getParentPropertyAdapter(JsonLikePsiWalker walker, PsiElement element) {
        JsonPropertyAdapter adapter = walker.getParentPropertyAdapter(element);
        assert adapter != null;
        return adapter;
    }

    private static <T> T single(Collection<T> collection) {
        if (collection.size() != 1) {
            throw new IllegalArgumentException("Collection must have exactly one element, but has " + collection.size());
        }
        return collection.iterator().next();
    }
}
