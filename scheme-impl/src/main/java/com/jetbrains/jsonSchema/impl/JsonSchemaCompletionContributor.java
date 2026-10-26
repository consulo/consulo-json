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
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.JsonLanguage;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonReferenceExpression;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.jetbrains.jsonSchema.JsonSchemaMetadataEntry;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.JsonSchemaVersion;
import com.jetbrains.jsonSchema.extension.JsonSchemaCompletionCustomizer;
import com.jetbrains.jsonSchema.extension.JsonSchemaNestedCompletionsTreeProvider;
import com.jetbrains.jsonSchema.extension.adapter.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.light.SchemaKeywords;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import com.jetbrains.jsonSchema.impl.nestedCompletions.CompletionNextStep;
import com.jetbrains.jsonSchema.impl.nestedCompletions.NestedCompletions;
import com.jetbrains.jsonSchema.impl.nestedCompletions.NestedCompletionsNode;
import com.jetbrains.jsonSchema.impl.nestedCompletions.SchemaPath;
import com.jetbrains.jsonSchema.impl.tree.JsonSchemaNodeExpansionRequest;
import com.jetbrains.jsonSchema.internal.JsonSchemaResolver;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.codeEditor.Caret;
import consulo.codeEditor.Editor;
import consulo.codeEditor.SelectionModel;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.codeEditor.action.EditorActionManager;
import consulo.codeEditor.action.EditorActionUtil;
import consulo.codeEditor.util.EditorModificationUtil;
import consulo.codeEditor.util.EditorUtil;
import consulo.json.icon.JsonIconGroup;
import consulo.json.localize.JsonLocalize;
import consulo.language.Language;
import consulo.language.ast.TokenType;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.AutoPopupController;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.PrioritizedLookupElement;
import consulo.language.impl.psi.LeafPsiElement;
import consulo.language.inject.Injectable;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.util.LanguageUtil;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.ex.action.IdeActions;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ThreeState;
import consulo.util.lang.lazy.LazyValue;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@ExtensionImpl(order = "before JsonCompletionContributor")
public class JsonSchemaCompletionContributor extends CompletionContributor {
    @Override
    public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
        PsiElement position = parameters.getPosition();
        VirtualFile file = PsiUtilCore.getVirtualFile(position);
        if (file == null) {
            return;
        }

        JsonSchemaService service = JsonSchemaService.get(position.getProject());
        if (!service.isApplicableToFile(file)) {
            return;
        }
        JsonSchemaObject rootSchema = service.getSchemaObject(Objects.requireNonNull(position.getContainingFile()));
        if (rootSchema == null) {
            return;
        }

        if (skipForSchemaAndRef(position, service, file)) {
            return;
        }

        doCompletion(parameters, result, rootSchema, true);
    }

    @Override
    public Language getLanguage() {
        return JsonLanguage.INSTANCE;
    }

    private boolean skipForSchemaAndRef(PsiElement position, JsonSchemaService service, VirtualFile file) {
        PsiElement positionParent = position.getParent();
        if (positionParent == null) {
            return false;
        }
        if (!(positionParent.getParent() instanceof JsonProperty parent)) {
            return false;
        }
        String propName = parent.getName();
        return "$schema".equals(propName) && parent.getParent() instanceof JsonObject && parent.getParent().getParent() instanceof JsonFile
            || "$ref".equals(propName) && service.isSchemaFile(file);
    }

    private static final class Worker {
        // some schemas provide an empty array or an empty object in enum values...
        private static final Set<String> filteredByDefault = Set.of("[]", "{}", "[ ]", "{ }");
        private static final List<String> commonAbbreviations = List.of("e.g.", "i.e.");

        private final JsonSchemaObject rootSchema;
        private final PsiElement completionPsiElement;
        private final PsiElement originalPosition;
        private final CompletionType completionType;
        private final Consumer<Collection<LookupElement>> resultHandler;
        private final boolean wrapInQuotes;
        private final boolean insideStringLiteral;

        // we need this set to filter same-named suggestions (they can be suggested by several matching schemes)
        private final Set<LookupElement> completionVariants = new LinkedHashSet<>();
        private final @Nullable JsonLikePsiWalker psiWalker;
        private final Project myProject;

        private final LazyValue<@Nullable JsonSchemaCompletionCustomizer> completionCustomizer;

        private Worker(
            JsonSchemaObject rootSchema,
            PsiElement completionPsiElement,
            PsiElement originalPosition,
            CompletionType completionType,
            Consumer<Collection<LookupElement>> resultHandler
        ) {
            this.rootSchema = rootSchema;
            this.completionPsiElement = completionPsiElement;
            this.originalPosition = originalPosition;
            this.completionType = completionType;
            this.resultHandler = resultHandler;
            myProject = originalPosition.getProject();

            JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(completionPsiElement, rootSchema);
            PsiElement positionParent = completionPsiElement.getParent();
            boolean isInsideQuotedString = positionParent != null && walker != null && walker.isQuotedString(positionParent);
            wrapInQuotes = !isInsideQuotedString;
            psiWalker = walker;
            insideStringLiteral = isInsideQuotedString;

            completionCustomizer = LazyValue.nullable(() -> {
                List<JsonSchemaCompletionCustomizer> customizers = Application.get()
                    .getExtensionPoint(JsonSchemaCompletionCustomizer.class)
                    .collectFiltered(it -> it.isApplicable(Objects.requireNonNull(originalPosition.getContainingFile())));
                return customizers.size() == 1 ? customizers.get(0) : null;
            });
        }

        private void work() {
            JsonLikePsiWalker walker = psiWalker;
            if (walker == null) {
                return;
            }
            PsiElement checkable = walker.findElementToCheck(completionPsiElement);
            if (checkable == null) {
                return;
            }
            ThreeState isName = walker.isName(checkable);
            JsonPointerPosition position = walker.findPosition(checkable, isName == ThreeState.NO);
            if (position == null || position.isEmpty() && isName == ThreeState.NO) {
                return;
            }

            Set<String> knownNames = new HashSet<>();

            NestedCompletionsNode nestedCompletionsNode = NestedCompletionsNode.navigate(
                JsonSchemaNestedCompletionsTreeProvider.getNestedCompletionsData(Objects.requireNonNull(originalPosition.getContainingFile())),
                position
            );

            JsonPropertyAdapter parentPropertyAdapter = walker.getParentPropertyAdapter(completionPsiElement);
            JsonSchemaNodeExpansionRequest schemaExpansionRequest = new JsonSchemaNodeExpansionRequest(
                parentPropertyAdapter == null ? null : parentPropertyAdapter.getParentObject(),
                completionType == CompletionType.SMART
            );
            for (JsonSchemaObject schema : new JsonSchemaResolver(myProject, rootSchema, position, schemaExpansionRequest).resolve()) {
                NestedCompletions.collectNestedCompletions(schema, myProject, nestedCompletionsNode, null, (path, subSchema) -> {
                    JsonSchemaCompletionCustomizer customizer = completionCustomizer.get();
                    if (customizer != null && !customizer.acceptsPropertyCompletionItem(subSchema, completionPsiElement)) {
                        return CompletionNextStep.Stop;
                    }
                    else {
                        processSchema(subSchema, isName, knownNames, path);
                        return CompletionNextStep.Continue;
                    }
                });
            }

            resultHandler.accept(completionVariants);
        }

        /**
         * @param completionPath Linked node representation of the names of all the parent
         *                       schema objects that we have navigated for nested completions
         */
        private void processSchema(
            JsonSchemaObject schema,
            ThreeState isName,
            Set<String> knownNames,
            @Nullable SchemaPath completionPath
        ) {
            JsonLikePsiWalker walker = psiWalker;
            if (walker == null) {
                return;
            }
            if (isName != ThreeState.NO) {
                PsiElement completionOriginalPosition = NestedCompletions.findChildBy(walker, completionPath, originalPosition);
                PsiElement completionPosition = NestedCompletions.findChildBy(walker, completionPath, completionPsiElement);

                Set<String> properties = walker.getPropertyNamesOfParentObject(completionOriginalPosition, completionPosition);
                JsonPropertyAdapter adapter = walker.getParentPropertyAdapter(completionOriginalPosition);

                Set<String> forbiddenNames = new HashSet<>(
                    NotRequiredProperties.findPropertiesThatMustNotBePresent(schema, completionPsiElement, myProject, properties)
                );
                forbiddenNames.addAll(properties);
                addAllPropertyVariants(schema, forbiddenNames, adapter, knownNames, completionPath);
                addPropertyNameSchemaVariants(schema);
            }

            if (isName != ThreeState.YES) {
                suggestValues(schema, isName == ThreeState.NO, completionPath);
            }
        }

        private void addPropertyNameSchemaVariants(JsonSchemaObject schema) {
            JsonSchemaObject propertyNamesSchema = schema.getPropertyNamesSchema();
            List<Object> anEnum = propertyNamesSchema == null ? null : propertyNamesSchema.getEnum();
            if (anEnum == null) {
                return;
            }
            for (Object o : anEnum) {
                if (!(o instanceof String value)) {
                    continue;
                }
                String toInsert;
                if (!shouldWrapInQuotes(value, false)) {
                    toInsert = value;
                }
                else {
                    toInsert = psiWalker != null ? psiWalker.escapeInvalidIdentifier(value) : StringUtil.wrapWithDoubleQuote(value);
                }
                completionVariants.add(LookupElementBuilder.create(StringUtil.unquoteString(toInsert)));
            }
        }

        private void addAllPropertyVariants(
            JsonSchemaObject schema,
            Set<String> forbiddenNames,
            @Nullable JsonPropertyAdapter adapter,
            Set<String> knownNames,
            @Nullable SchemaPath completionPath
        ) {
            Iterator<String> propertyNames = schema.getPropertyNames();
            while (propertyNames.hasNext()) {
                String name = propertyNames.next();
                if (!(!forbiddenNames.contains(name) && !knownNames.contains(name) || adapter != null && name.equals(adapter.getName()))) {
                    continue;
                }
                knownNames.add(name);
                JsonSchemaObject propertySchema = schema.getPropertyByName(name);
                if (propertySchema == null) {
                    throw new IllegalStateException("Required value was null.");
                }
                JsonSchemaCompletionCustomizer customizer = completionCustomizer.get();
                if (customizer == null || customizer.acceptsPropertyCompletionItem(propertySchema, completionPsiElement)) {
                    addPropertyVariant(name, propertySchema, completionPath, adapter != null ? adapter.getNameValueAdapter() : null);
                }
            }
        }

        private void suggestValues(JsonSchemaObject schema, boolean isSurelyValue, @Nullable SchemaPath completionPath) {
            suggestValuesForSchemaVariants(schema.getAnyOf(), isSurelyValue, completionPath);
            suggestValuesForSchemaVariants(schema.getOneOf(), isSurelyValue, completionPath);
            suggestValuesForSchemaVariants(schema.getAllOf(), isSurelyValue, completionPath);

            List<Object> anEnum = schema.getEnum();
            if (anEnum != null && completionPath == null) {
                Map<String, Map<String, String>> metadata = schema.getEnumMetadata();
                boolean isEnumOrderSensitive = Boolean.parseBoolean(schema.readChildNodeValue(SchemaKeywords.X_INTELLIJ_ENUM_ORDER_SENSITIVE));
                Set<String> filtered = new HashSet<>(filteredByDefault);
                filtered.addAll(getEnumItemsToSkip());
                for (int i = 0; i < anEnum.size(); i++) {
                    Object o = anEnum.get(i);
                    if (insideStringLiteral && !(o instanceof String)) {
                        continue;
                    }
                    String variant = o.toString();
                    if (!filtered.contains(variant) && !filtered.contains(StringUtil.unquoteString(variant))) {
                        Map<String, String> valueMetadata = metadata == null ? null : metadata.get(StringUtil.unquoteString(variant));
                        String description = valueMetadata == null ? null : valueMetadata.get("description");
                        String deprecated = valueMetadata == null ? null : valueMetadata.get("deprecationMessage");
                        Integer order = isEnumOrderSensitive ? i : null;
                        JsonSchemaCompletionCustomizer customizer = completionCustomizer.get();
                        InsertHandler<LookupElement> handler =
                            customizer == null ? null : customizer.createHandlerForEnumValue(schema, variant, completionPsiElement);
                        addValueVariant(variant, description, null, handler, order, deprecated != null);
                    }
                }
            }
            else if (isSurelyValue) {
                JsonSchemaType type = JsonSchemaObjectReadingUtils.guessType(schema);
                suggestSpecialValues(type);
                if (type != null) {
                    suggestByType(schema, type);
                }
                else {
                    Set<JsonSchemaType> typeVariants = schema.getTypeVariants();
                    if (typeVariants != null) {
                        for (JsonSchemaType schemaType : typeVariants) {
                            suggestByType(schema, schemaType);
                        }
                    }
                }
            }
            else if (psiWalker != null && psiWalker.hasObjectArrayAmbivalence()) {
                JsonSchemaObject itemsSchema = schema.getItemsSchema();
                if (itemsSchema != null) {
                    suggestValues(itemsSchema, false, completionPath);
                }
            }
        }

        private Set<String> getEnumItemsToSkip() {
            // if the parent is an array, and it assumes unique items, we don't suggest the same enum items again
            JsonLikePsiWalker walker = psiWalker;
            if (walker == null) {
                return Collections.emptySet();
            }
            JsonPointerPosition position = walker.findPosition(Objects.requireNonNull(walker.findElementToCheck(completionPsiElement)), false);
            JsonPointerPosition containerPosition = position == null ? null : position.trimTail(1);
            JsonSchemaObject containerSchema = null;
            if (containerPosition != null) {
                Collection<JsonSchemaObject> resolved =
                    new JsonSchemaResolver(myProject, rootSchema, containerPosition, (JsonValueAdapter) null).resolve();
                containerSchema = resolved.size() == 1 ? resolved.iterator().next() : null;
            }
            if (containerSchema != null && containerSchema.isUniqueItems()) {
                JsonArrayValueAdapter parentArray = null;
                for (PsiElement element = completionPsiElement.getParent(); element != null; element = element.getParent()) {
                    JsonValueAdapter adapter = walker.createValueAdapter(element);
                    parentArray = adapter == null ? null : adapter.getAsArray();
                    if (parentArray != null) {
                        break;
                    }
                }
                if (parentArray == null) {
                    return Collections.emptySet();
                }
                return parentArray.getElements().stream()
                    .map(it -> StringUtil.unquoteString(it.getDelegate().getText()))
                    .collect(Collectors.toSet());
            }
            else {
                return Collections.emptySet();
            }
        }

        private void suggestSpecialValues(@Nullable JsonSchemaType type) {
            if (!JsonSchemaVersion.isSchemaSchemaId(rootSchema.getId()) || type != JsonSchemaType._string) {
                return;
            }
            JsonLikePsiWalker walker = psiWalker;
            if (walker == null) {
                return;
            }
            JsonPropertyAdapter propertyAdapter = walker.getParentPropertyAdapter(originalPosition);
            if (propertyAdapter == null) {
                return;
            }
            String name = propertyAdapter.getName();
            if ("required".equals(name)) {
                addRequiredPropVariants();
            }
            else if (SchemaKeywords.X_INTELLIJ_LANGUAGE_INJECTION.equals(name)) {
                addInjectedLanguageVariants();
            }
            else if ("language".equals(name)) {
                JsonObjectValueAdapter parent = propertyAdapter.getParentObject();
                if (parent != null) {
                    JsonPropertyAdapter adapter = walker.getParentPropertyAdapter(parent.getDelegate());
                    if (adapter != null && SchemaKeywords.X_INTELLIJ_LANGUAGE_INJECTION.equals(adapter.getName())) {
                        addInjectedLanguageVariants();
                    }
                }
            }
        }

        private void addInjectedLanguageVariants() {
            JsonLikePsiWalker walker = psiWalker;
            if (walker == null) {
                return;
            }
            PsiElement checkable = walker.findElementToCheck(completionPsiElement);
            if (!(checkable instanceof JsonStringLiteral) && !(checkable instanceof JsonReferenceExpression)) {
                return;
            }
            for (Language language : Language.getRegisteredLanguages()) {
                if (!LanguageUtil.isInjectableLanguage(language)) {
                    continue;
                }
                Injectable injectable = Injectable.fromLanguage(language);
                completionVariants.add(
                    LookupElementBuilder
                        .create(injectable.getId())
                        .withIcon(injectable.getIcon())
                        .withTailText("(" + injectable.getDisplayName().get() + ")", true)
                );
            }
        }

        private void addRequiredPropVariants() {
            JsonLikePsiWalker walker = psiWalker;
            if (walker == null) {
                return;
            }
            PsiElement checkable = walker.findElementToCheck(completionPsiElement);
            if (!(checkable instanceof JsonStringLiteral) && !(checkable instanceof JsonReferenceExpression)) {
                return;
            }
            JsonObject propertiesObject = JsonRequiredPropsReferenceProvider.findPropertiesObject(checkable);
            if (propertiesObject == null) {
                return;
            }
            PsiElement parent = checkable.getParent();
            Set<String> items = new HashSet<>();
            if (parent instanceof JsonArray array) {
                for (JsonValue value : array.getValueList()) {
                    if (value instanceof JsonStringLiteral literal) {
                        items.add(literal.getValue());
                    }
                }
            }
            for (JsonProperty property : propertiesObject.getPropertyList()) {
                String name = property.getName();
                if (!items.contains(name)) {
                    addStringVariant(name);
                }
            }
        }

        private void suggestByType(JsonSchemaObject schema, JsonSchemaType type) {
            if (JsonSchemaType._string == type) {
                addPossibleStringValue(schema);
            }
            if (insideStringLiteral) {
                return;
            }
            JsonLikePsiWalker walker = psiWalker;
            switch (type) {
                case _boolean -> {
                    addValueVariant("true");
                    addValueVariant("false");
                }
                case _null -> addValueVariant("null");
                case _array -> {
                    if (walker == null) {
                        return;
                    }
                    String value = walker.getDefaultArrayValue();
                    addValueVariant(
                        value,
                        null,
                        "[...]",
                        createArrayOrObjectLiteralInsertHandler(walker.hasWhitespaceDelimitedCodeBlocks(), value.length()),
                        null,
                        false
                    );
                }
                case _object -> {
                    if (walker == null) {
                        return;
                    }
                    String value = walker.getDefaultObjectValue();
                    addValueVariant(
                        value,
                        null,
                        "{...}",
                        createArrayOrObjectLiteralInsertHandler(walker.hasWhitespaceDelimitedCodeBlocks(), value.length()),
                        null,
                        false
                    );
                }
                default -> {
                }
            }
        }

        private void addPossibleStringValue(JsonSchemaObject schema) {
            Object defaultValue = schema.getDefault();
            String defaultValueString = defaultValue == null ? null : defaultValue.toString();
            addStringVariant(defaultValueString);
        }

        private void addStringVariant(@Nullable String defaultValueString) {
            if (defaultValueString == null || psiWalker == null) {
                return;
            }
            String normalizedValue = defaultValueString;
            boolean shouldQuote = psiWalker.requiresValueQuotes();
            boolean isQuoted = StringUtil.isQuotedString(normalizedValue);
            if (shouldQuote && !isQuoted) {
                normalizedValue = StringUtil.wrapWithDoubleQuote(normalizedValue);
            }
            else if (!shouldQuote && isQuoted) {
                normalizedValue = StringUtil.unquoteString(normalizedValue);
            }
            addValueVariant(normalizedValue);
        }

        private void suggestValuesForSchemaVariants(
            @Nullable List<? extends JsonSchemaObject> list,
            boolean isSurelyValue,
            @Nullable SchemaPath completionPath
        ) {
            if (list == null || list.isEmpty()) {
                return;
            }
            for (JsonSchemaObject schemaObject : list) {
                suggestValues(schemaObject, isSurelyValue, completionPath);
            }
        }

        private void addValueVariant(String key) {
            addValueVariant(key, null, null, null, null, false);
        }

        private void addValueVariant(
            String key,
            @Nullable String description,
            @Nullable String altText,
            @Nullable InsertHandler<LookupElement> handler,
            @Nullable Integer order,
            boolean deprecated
        ) {
            String unquoted = StringUtil.unquoteString(key);
            String lookupString = !shouldWrapInQuotes(unquoted, true) ? unquoted : key;
            LookupElementBuilder builder = LookupElementBuilder.create(lookupString)
                .withPresentableText(altText != null ? altText : lookupString)
                .withTypeText(description)
                .withInsertHandler(handler);
            builder = withDeprecation(builder, deprecated);
            if (order != null) {
                completionVariants.add(PrioritizedLookupElement.withPriority(builder, -order.doubleValue()));
            }
            else {
                completionVariants.add(builder);
            }
        }

        private boolean shouldWrapInQuotes(@Nullable String key, boolean isValue) {
            return wrapInQuotes && psiWalker != null
                && (isValue && psiWalker.requiresValueQuotes()
                || !isValue && psiWalker.requiresNameQuotes()
                || key != null && !psiWalker.isValidIdentifier(key, myProject));
        }

        private void addPropertyVariant(
            String key,
            JsonSchemaObject jsonSchemaObject,
            @Nullable SchemaPath completionPath,
            @Nullable JsonValueAdapter sourcePsiAdapter
        ) {
            Collection<JsonSchemaObject> variants =
                new JsonSchemaResolver(myProject, jsonSchemaObject, new JsonPointerPosition(), sourcePsiAdapter).resolve();
            JsonSchemaObject firstVariant = ContainerUtil.getFirstItem(variants);
            JsonSchemaObject schemaObject = firstVariant != null ? firstVariant : jsonSchemaObject;
            String propertyKey;
            if (!shouldWrapInQuotes(key, false)) {
                propertyKey = key;
            }
            else {
                propertyKey = psiWalker != null ? psiWalker.escapeInvalidIdentifier(key) : StringUtil.wrapWithDoubleQuote(key);
            }

            List<String> extraLookupStrings = Collections.emptyList();
            List<JsonSchemaMetadataEntry> metadata = jsonSchemaObject.getMetadata();
            if (metadata != null) {
                for (JsonSchemaMetadataEntry entry : metadata) {
                    if ("aliases".equals(entry.getKey())) {
                        extraLookupStrings = entry.getValues();
                        break;
                    }
                }
            }

            String presentableText;
            if (completionPath != null) {
                presentableText = completionPath.prefix() + "." + key;
            }
            else if (psiWalker != null && !psiWalker.requiresNameQuotes()) {
                presentableText = key;
            }
            else {
                presentableText = propertyKey;
            }

            List<String> lookupStrings = new ArrayList<>();
            if (completionPath != null) {
                lookupStrings.add(completionPath.prefix() + "." + key);
            }
            lookupStrings.add(propertyKey);
            if (completionPath != null) {
                lookupStrings.addAll(completionPath.accessor());
            }
            lookupStrings.addAll(extraLookupStrings);

            LookupElementBuilder builder = LookupElementBuilder.create(propertyKey)
                .withPresentableText(presentableText)
                .withLookupStrings(lookupStrings)
                .withTypeText(getDocumentationOrTypeName(schemaObject), true)
                .withIcon(getIcon(JsonSchemaObjectReadingUtils.guessType(schemaObject)))
                .withInsertHandler(choosePropertyInsertHandler(completionPath, variants, schemaObject));
            builder = withDeprecation(builder, schemaObject.getDeprecationMessage() != null);

            if (completionPath != null) {
                completionVariants.add(PrioritizedLookupElement.withPriority(builder, -completionPath.accessor().size()));
            }
            else {
                completionVariants.add(builder);
            }
        }

        private static LookupElementBuilder withDeprecation(LookupElementBuilder builder, boolean deprecated) {
            if (!deprecated) {
                return builder;
            }
            return builder.withTailText(JsonLocalize.schemaDocumentationDeprecatedPostfix().get(), true).withStrikeoutness(true);
        }

        private InsertHandler<LookupElement> choosePropertyInsertHandler(
            @Nullable SchemaPath completionPath,
            Collection<JsonSchemaObject> variants,
            JsonSchemaObject schemaObject
        ) {
            if (hasSameType(variants)) {
                JsonSchemaType type = JsonSchemaObjectReadingUtils.guessType(schemaObject);
                List<Object> values = schemaObject.getEnum();
                if (values != null && !values.isEmpty()) {
                    // if we have an enum with a single kind of values - trigger the handler with value
                    if (values.stream().map(Object::getClass).distinct().count() == 1) {
                        return createPropertyInsertHandler(schemaObject, completionPath);
                    }
                }
                else {
                    // insert a default value if no enum
                    if (type != null || schemaObject.getDefault() != null) {
                        return createPropertyInsertHandler(schemaObject, completionPath);
                    }
                }
            }

            List<Object> anEnum = schemaObject.getEnum();
            return createDefaultPropertyInsertHandler(completionPath, anEnum != null && !anEnum.isEmpty(), schemaObject.getTypeVariants());
        }

        private static @Nullable String getDocumentationOrTypeName(JsonSchemaObject schemaObject) {
            String docText = JsonSchemaDocumentationProvider.getBestDocumentation(true, schemaObject);
            if (docText != null && !docText.isBlank()) {
                return findFirstSentence(StringUtil.stripHtml(docText, false));
            }
            else {
                return JsonSchemaObjectReadingUtils.getTypeDescription(schemaObject, true);
            }
        }

        private InsertHandler<LookupElement> createDefaultPropertyInsertHandler(
            @Nullable SchemaPath completionPath,
            boolean hasEnumValues,
            @Nullable Set<JsonSchemaType> valueTypes
        ) {
            return new InsertHandler<>() {
                @Override
                public void handleInsert(InsertionContext context, LookupElement item) {
                    Application.get().assertWriteAccessAllowed();
                    Editor editor = context.getEditor();
                    Project project = context.getProject();

                    NestedCompletions.expandMissingPropertiesAndMoveCaret(context, completionPath);

                    if (handleInsideQuotesInsertion(context, editor, insideStringLiteral)) {
                        return;
                    }
                    JsonLikePsiWalker walker = psiWalker;
                    if (walker == null) {
                        return;
                    }
                    boolean insertComma = walker.hasMissingCommaAfter(completionPsiElement);
                    String comma = insertComma ? "," : "";
                    boolean hasValue = hasEnumValues || walker.isPropertyWithValue(Objects.requireNonNull(walker.findElementToCheck(completionPsiElement)));
                    int offset = editor.getCaretModel().getOffset();
                    int initialOffset = offset;
                    CharSequence docChars = context.getDocument().getCharsSequence();
                    while (offset < docChars.length() && Character.isWhitespace(docChars.charAt(offset))) {
                        offset++;
                    }
                    String propertyValueSeparator = null;
                    if (valueTypes != null) {
                        for (JsonSchemaType valueType : valueTypes) {
                            String separator = walker.getPropertyValueSeparator(valueType);
                            if (!separator.isBlank()) {
                                propertyValueSeparator = separator;
                                break;
                            }
                        }
                    }
                    if (propertyValueSeparator == null) {
                        JsonSchemaType singleType = valueTypes != null && valueTypes.size() == 1 ? valueTypes.iterator().next() : null;
                        propertyValueSeparator = walker.getPropertyValueSeparator(singleType);
                    }
                    if (hasValue) {
                        // fix colon for YAML and alike
                        if (offset < docChars.length() && !isSeparatorAtOffset(docChars, offset, propertyValueSeparator)) {
                            editor.getDocument().insertString(initialOffset, propertyValueSeparator);
                            handleWhitespaceAfterColon(editor, docChars, initialOffset + propertyValueSeparator.length());
                        }
                        return;
                    }

                    if (offset < docChars.length() && isSeparatorAtOffset(docChars, offset, propertyValueSeparator)) {
                        handleWhitespaceAfterColon(editor, docChars, offset + propertyValueSeparator.length());
                    }
                    else {
                        // inserting longer string for proper formatting
                        String stringToInsert = propertyValueSeparator + " 1" + comma;
                        EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, propertyValueSeparator.length() + 1);
                        formatInsertedString(context, stringToInsert.length());
                        offset = editor.getCaretModel().getOffset();
                        context.getDocument().deleteString(offset, offset + 1);
                    }
                    PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
                    AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
                }

                private boolean isSeparatorAtOffset(CharSequence docChars, int offset, String propertyValueSeparator) {
                    return docChars.subSequence(offset, docChars.length()).toString().startsWith(propertyValueSeparator);
                }

                private void handleWhitespaceAfterColon(Editor editor, CharSequence docChars, int nextOffset) {
                    if (nextOffset < docChars.length() && docChars.charAt(nextOffset) == ' ') {
                        editor.getCaretModel().moveToOffset(nextOffset + 1);
                    }
                    else {
                        editor.getCaretModel().moveToOffset(nextOffset);
                        EditorModificationUtil.insertStringAtCaret(editor, " ", false, true, 1);
                    }
                }
            };
        }

        private InsertHandler<LookupElement> createPropertyInsertHandler(
            JsonSchemaObject jsonSchemaObject,
            @Nullable SchemaPath completionPath
        ) {
            Object defaultValue = jsonSchemaObject.getDefault();
            String defaultValueAsString;
            if (defaultValue == null || defaultValue instanceof JsonSchemaObject) {
                defaultValueAsString = null;
            }
            else if (defaultValue instanceof String) {
                defaultValueAsString = "\"" + defaultValue + "\"";
            }
            else {
                defaultValueAsString = defaultValue.toString();
            }
            List<Object> anEnum = jsonSchemaObject.getEnum();
            JsonSchemaType finalType = JsonSchemaObjectReadingUtils.guessType(jsonSchemaObject);
            if (finalType == null) {
                finalType = detectTypeByEnumValues(anEnum == null ? Collections.emptyList() : anEnum);
            }
            JsonLikePsiWalker walker = psiWalker;
            if (walker == null) {
                throw new IllegalStateException("Walker is required for property insertion");
            }
            return JsonSchemaCompletionContributor.createPropertyInsertHandler(
                finalType,
                defaultValueAsString,
                anEnum,
                walker,
                insideStringLiteral,
                completionPath
            );
        }

        private static String findFirstSentence(String sentence) {
            int i = sentence.indexOf(". ");
            while (i >= 0) {
                int index = i;
                if (commonAbbreviations.stream().noneMatch(abbr -> sentence.regionMatches(index - abbr.length() + 1, abbr, 0, abbr.length()))) {
                    return sentence.substring(0, i + 1);
                }
                i = sentence.indexOf(". ", i + 1);
            }
            return sentence;
        }

        private static Image getIcon(@Nullable JsonSchemaType type) {
            if (type == null) {
                return PlatformIconGroup.nodesProperty();
            }
            return switch (type) {
                case _object -> JsonIconGroup.object();
                case _array -> JsonIconGroup.array();
                default -> PlatformIconGroup.nodesProperty();
            };
        }

        private static boolean hasSameType(Collection<JsonSchemaObject> variants) {
            // enum is not a separate type, so we should treat whether it can be an enum distinctly from the types
            return variants.stream()
                .map(it -> Pair.create(JsonSchemaObjectReadingUtils.guessType(it), isUntypedEnum(it)))
                .distinct()
                .count() <= 1;
        }

        private static boolean isUntypedEnum(JsonSchemaObject it) {
            List<Object> anEnum = it.getEnum();
            return JsonSchemaObjectReadingUtils.guessType(it) == null && anEnum != null && !anEnum.isEmpty();
        }

        private static InsertHandler<LookupElement> createArrayOrObjectLiteralInsertHandler(boolean newline, int insertedTextSize) {
            return (context, item) -> {
                Editor editor = context.getEditor();
                if (!newline) {
                    EditorModificationUtil.moveCaretRelatively(editor, -1);
                }
                else {
                    EditorModificationUtil.moveCaretRelatively(editor, -insertedTextSize);
                    PsiDocumentManager.getInstance(context.getProject()).commitDocument(editor.getDocument());
                    invokeEnterHandler(editor);
                    EditorActionUtil.moveCaretToLineEnd(editor, false, false);
                }
                AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(editor);
            };
        }

        private static @Nullable JsonSchemaType detectTypeByEnumValues(List<Object> values) {
            JsonSchemaType type = null;
            for (Object value : values) {
                JsonSchemaType newType = null;
                if (value instanceof Integer) {
                    newType = JsonSchemaType._integer;
                }
                if (type != null && type != newType) {
                    return null;
                }
                type = newType;
            }
            return type;
        }
    }

    public static void doCompletion(
        CompletionParameters parameters,
        CompletionResultSet result,
        JsonSchemaObject rootSchema,
        boolean stop
    ) {
        PsiElement originalPosition = parameters.getOriginalPosition();
        Worker worker = new Worker(
            rootSchema,
            parameters.getPosition(),
            originalPosition != null ? originalPosition : parameters.getPosition(),
            parameters.getCompletionType(),
            result::addAllElements
        );
        worker.work();
        // stop further completion only if the current contributor has at least one new completion variant
        if (stop && !worker.completionVariants.isEmpty()) {
            result.stopHere();
        }
    }

    public static List<LookupElement> getCompletionVariants(
        JsonSchemaObject schema,
        PsiElement position,
        PsiElement originalPosition,
        CompletionType completionType
    ) {
        List<LookupElement> result = new ArrayList<>();
        new Worker(schema, position, originalPosition, completionType, result::addAll).work();
        return result;
    }

    private static void invokeEnterHandler(Editor editor) {
        EditorActionHandler handler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
        Caret caret = editor.getCaretModel().getCurrentCaret();
        handler.execute(editor, caret, EditorUtil.getEditorDataContext(editor));
    }

    private static boolean handleInsideQuotesInsertion(InsertionContext context, Editor editor, boolean insideStringLiteral) {
        if (!insideStringLiteral) {
            return false;
        }
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = context.getFile().findElementAt(offset);
        int tailOffset = context.getTailOffset();
        int guessEndOffset = tailOffset + 1;
        if (element instanceof LeafPsiElement leaf) {
            if (handleIncompleteString(editor, leaf)) {
                return false;
            }
            int endOffset = element.getTextRange().getEndOffset();
            if (endOffset > tailOffset) {
                context.getDocument().deleteString(tailOffset, endOffset - 1);
            }
        }
        if (element != null) {
            JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element);
            if (walker != null && walker.isPropertyWithValue(Objects.requireNonNull(walker.findElementToCheck(element)))) {
                return true;
            }
        }
        editor.getCaretModel().moveToOffset(guessEndOffset);
        return false;
    }

    private static boolean handleIncompleteString(Editor editor, LeafPsiElement element) {
        if (element.getElementType() == TokenType.WHITE_SPACE) {
            PsiElement prevSibling = element.getPrevSibling();
            if (prevSibling instanceof JsonProperty property) {
                JsonValue nameElement = property.getNameElement();
                if (!nameElement.getText().endsWith("\"")) {
                    editor.getCaretModel().moveToOffset(nameElement.getTextRange().getEndOffset());
                    EditorModificationUtil.insertStringAtCaret(editor, "\"", false, true, 1);
                    return true;
                }
            }
        }
        return false;
    }

    public static InsertHandler<LookupElement> createPropertyInsertHandler(
        @Nullable JsonSchemaType finalType,
        @Nullable String defaultValueAsString,
        @Nullable List<Object> values,
        JsonLikePsiWalker walker,
        boolean insideStringLiteral,
        @Nullable SchemaPath completionPath
    ) {
        return (context, item) -> {
            Application.get().assertWriteAccessAllowed();
            Editor editor = context.getEditor();
            Project project = context.getProject();

            NestedCompletions.expandMissingPropertiesAndMoveCaret(context, completionPath);

            String stringToInsert = null;

            if (handleInsideQuotesInsertion(context, editor, insideStringLiteral)) {
                return;
            }

            String propertyValueSeparator = walker.getPropertyValueSeparator(finalType);

            PsiElement leafAtCaret = findLeafAtCaret(context, editor, walker);
            boolean insertComma = leafAtCaret != null && walker.hasMissingCommaAfter(leafAtCaret);
            String comma = insertComma ? "," : "";
            boolean insertColon = leafAtCaret == null || !propertyValueSeparator.equals(leafAtCaret.getText());
            if (leafAtCaret != null && !insertColon) {
                editor.getCaretModel().moveToOffset(leafAtCaret.getTextRange().getEndOffset());
            }
            if (finalType != null) {
                boolean hadEnter;
                switch (finalType) {
                    case _object -> {
                        if (insertColon) {
                            EditorModificationUtil.insertStringAtCaret(
                                editor,
                                propertyValueSeparator + " ",
                                false,
                                true,
                                propertyValueSeparator.length() + 1
                            );
                        }
                        hadEnter = false;
                        boolean invokeEnter = walker.hasWhitespaceDelimitedCodeBlocks();
                        if (insertColon && invokeEnter) {
                            invokeEnterHandler(editor);
                            hadEnter = true;
                        }
                        if (insertColon) {
                            stringToInsert = walker.getDefaultObjectValue() + comma;
                            EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, hadEnter ? 0 : 1);
                        }

                        if (hadEnter || !insertColon) {
                            EditorActionUtil.moveCaretToLineEnd(editor, false, false);
                        }

                        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());
                        if (!hadEnter && stringToInsert != null) {
                            formatInsertedString(context, stringToInsert.length());
                        }
                        if (stringToInsert != null && !invokeEnter) {
                            invokeEnterHandler(editor);
                        }
                    }
                    case _boolean -> {
                        String value = String.valueOf(Boolean.TRUE.toString().equals(defaultValueAsString));
                        stringToInsert = (insertColon ? propertyValueSeparator + " " : " ") + value + comma;
                        SelectionModel model = editor.getSelectionModel();

                        EditorModificationUtil.insertStringAtCaret(
                            editor,
                            stringToInsert,
                            false,
                            true,
                            stringToInsert.length() - comma.length()
                        );
                        formatInsertedString(context, stringToInsert.length());
                        int start = editor.getSelectionModel().getSelectionStart();
                        model.setSelection(start - value.length(), start);
                        AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
                    }
                    case _array -> {
                        if (insertColon) {
                            EditorModificationUtil.insertStringAtCaret(
                                editor,
                                propertyValueSeparator,
                                false,
                                true,
                                propertyValueSeparator.length()
                            );
                        }
                        hadEnter = false;
                        PsiElement leaf = findLeafAtCaret(context, editor, walker);
                        PsiElement nextSibling = leaf == null ? null : leaf.getNextSibling();
                        if (insertColon && walker.hasWhitespaceDelimitedCodeBlocks()) {
                            invokeEnterHandler(editor);
                            hadEnter = true;
                        }
                        else {
                            if (!(nextSibling instanceof PsiWhiteSpace)) {
                                EditorModificationUtil.insertStringAtCaret(editor, " ", false, true, 1);
                            }
                            else {
                                editor.getCaretModel().moveToOffset(nextSibling.getTextRange().getEndOffset());
                            }
                        }
                        PsiElement currentLeaf = findLeafAtCaret(context, editor, walker);
                        if (insertColon || currentLeaf != null && walker.getPropertyValueSeparator(null).equals(currentLeaf.getText())) {
                            stringToInsert = walker.getDefaultArrayValue() + comma;
                            EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, hadEnter ? 0 : 1);
                        }
                        if (hadEnter) {
                            EditorActionUtil.moveCaretToLineEnd(editor, false, false);
                        }

                        PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

                        if (stringToInsert != null && walker.requiresReformatAfterArrayInsertion()) {
                            formatInsertedString(context, stringToInsert.length());
                        }
                    }
                    case _string, _integer, _number ->
                        insertPropertyWithEnum(context, editor, defaultValueAsString, values, finalType, comma, walker, insertColon);
                    default -> {
                    }
                }
            }
            else {
                insertPropertyWithEnum(context, editor, defaultValueAsString, values, null, comma, walker, insertColon);
            }
        };
    }

    private static @Nullable PsiElement findLeafAtCaret(InsertionContext context, Editor editor, JsonLikePsiWalker walker) {
        PsiElement element = context.getFile().findElementAt(editor.getCaretModel().getOffset());
        return element == null ? null : NestedCompletions.rewindToMeaningfulLeaf(element);
    }

    private static void insertPropertyWithEnum(
        InsertionContext context,
        Editor editor,
        @Nullable String defaultValue,
        @Nullable List<Object> values,
        @Nullable JsonSchemaType type,
        String comma,
        JsonLikePsiWalker walker,
        boolean insertColon
    ) {
        String value = defaultValue;
        String propertyValueSeparator = walker.getPropertyValueSeparator(type);
        if (!walker.requiresValueQuotes() && value != null) {
            value = StringUtil.unquoteString(value);
        }
        boolean isNumber = type != null && (JsonSchemaType._integer == type || JsonSchemaType._number == type)
            || type == null && (value != null && !StringUtil.isQuotedString(value)
            || values != null && values.stream().noneMatch(it -> it instanceof String));
        boolean hasValues = !ContainerUtil.isEmpty(values);
        boolean hasDefaultValue = !StringUtil.isEmpty(value);
        boolean requiresQuotes = !isNumber && walker.requiresValueQuotes();
        int offset = editor.getCaretModel().getOffset();
        CharSequence charSequence = editor.getDocument().getCharsSequence();
        String ws = charSequence.length() > offset && charSequence.charAt(offset) == ' ' ? "" : " ";
        String colonWs = insertColon ? propertyValueSeparator + ws : ws;
        String insertedValue;
        if (hasDefaultValue) {
            insertedValue = value;
        }
        else if (requiresQuotes) {
            insertedValue = "\"\"";
        }
        else {
            insertedValue = "";
        }
        String stringToInsert = colonWs + insertedValue + comma;
        EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true, insertColon ? propertyValueSeparator.length() + 1 : 1);
        if (requiresQuotes || hasDefaultValue) {
            SelectionModel model = editor.getSelectionModel();
            int caretStart = model.getSelectionStart();
            // if we are already within the value quotes, then the shift is zero, if not yet - move inside
            int quoteOffset = 1;
            if (caretStart - 1 >= 0) {
                char ch = editor.getDocument().getCharsSequence().charAt(caretStart - 1);
                if (ch == '"' || ch == '\'') {
                    quoteOffset = 0;
                }
            }
            int newOffset = caretStart + (hasDefaultValue ? Objects.requireNonNull(insertedValue).length() : quoteOffset);
            if (hasDefaultValue && requiresQuotes) {
                newOffset--;
            }
            model.setSelection(requiresQuotes ? caretStart + quoteOffset : caretStart, newOffset);
            editor.getCaretModel().moveToOffset(newOffset);
        }

        if (!walker.hasWhitespaceDelimitedCodeBlocks() && !stringToInsert.equals(colonWs + comma)) {
            formatInsertedString(context, stringToInsert.length());
        }

        if (hasValues) {
            AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
        }
    }

    public static void formatInsertedString(InsertionContext context, int offset) {
        Project project = context.getProject();
        PsiDocumentManager.getInstance(project).commitDocument(context.getDocument());
        CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
        codeStyleManager.reformatText(context.getFile(), context.getStartOffset(), context.getTailOffset() + offset);
    }
}
