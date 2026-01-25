// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.JsonBundle;
import com.intellij.json.impl.pointer.JsonPointerPosition;
import com.intellij.json.psi.*;
import com.jetbrains.jsonSchema.*;
import com.jetbrains.jsonSchema.extension.*;
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonPropertyAdapter;
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter;
import com.jetbrains.jsonSchema.impl.nestedCompletions.SchemaPath;
import com.jetbrains.jsonSchema.impl.tree.JsonSchemaNodeExpansionRequest;
import consulo.application.AllIcons;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.codeEditor.action.EditorActionHandler;
import consulo.codeEditor.action.EditorActionManager;
import consulo.codeEditor.action.EditorActionUtil;
import consulo.codeEditor.util.EditorModificationUtil;
import consulo.dataContext.DataManager;
import consulo.language.Language;
import consulo.language.ast.TokenType;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.AutoPopupController;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.*;
import consulo.language.inject.Injectable;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.util.LanguageUtil;
import consulo.project.Project;
import consulo.ui.ex.action.IdeActions;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ThreeState;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.http.HttpVirtualFile;
import jakarta.annotation.Nonnull;
import one.util.streamex.StreamEx;
import jakarta.annotation.Nullable;
import sun.tools.jconsole.inspector.IconManager;

import javax.swing.*;
import java.util.*;
import java.util.stream.Collectors;

import static com.jetbrains.jsonSchema.impl.JsonSchemaVariantsTreeBuilder.rewindToMeaningfulLeaf;
import static com.jetbrains.jsonSchema.impl.light.X_INTELLIJ_ENUM_ORDER_SENSITIVE;
import static com.jetbrains.jsonSchema.impl.light.X_INTELLIJ_LANGUAGE_INJECTION;

public class JsonSchemaCompletionContributor extends CompletionContributor {
  private static final String BUILTIN_USAGE_KEY = "builtin";
  private static final String SCHEMA_USAGE_KEY = "schema";
  private static final String USER_USAGE_KEY = "user";
  private static final String REMOTE_USAGE_KEY = "remote";

  @Override
  public void fillCompletionVariants(@Nonnull CompletionParameters parameters, @Nonnull CompletionResultSet result) {
    PsiElement position = parameters.getPosition();
    VirtualFile file = PsiUtilCore.getVirtualFile(position);
    if (file == null) return;

    JsonSchemaService service = JsonSchemaService.Impl.get(position.getProject());
    if (!service.isApplicableToFile(file)) return;
    JsonSchemaObject rootSchema = service.getSchemaObject(position.getContainingFile());
    if (rootSchema == null) return;

    if (skipForSchemaAndRef(position, service, file)) return;

    updateStat(service.getSchemaProvider(rootSchema), service.resolveSchemaFile(rootSchema));
    doCompletion(parameters, result, rootSchema, true);
  }

  private boolean skipForSchemaAndRef(@Nonnull PsiElement position,
                                      @Nonnull JsonSchemaService service,
                                      @Nonnull VirtualFile file) {
    PsiElement positionParent = position.getParent();
    if (positionParent == null) return false;
    PsiElement grandParent = positionParent.getParent();
    if (!(grandParent instanceof JsonProperty)) return false;
    JsonProperty parent = (JsonProperty) grandParent;
    String propName = parent.getName();
    return ("$schema".equals(propName) && parent.getParent() instanceof JsonObject && parent.getParent().getParent() instanceof JsonFile
            || "$ref".equals(propName) && service.isSchemaFile(file));
  }

  private static class Worker {
    private final JsonSchemaObject rootSchema;
    private final PsiElement completionPsiElement;
    private final PsiElement originalPosition;
    private final CompletionType completionType;
    private final java.util.function.Consumer<Collection<LookupElement>> resultHandler;
    private final boolean wrapInQuotes;
    private final boolean insideStringLiteral;

    // we need this set to filter same-named suggestions (they can be suggested by several matching schemes)
    final Set<LookupElement> completionVariants = new LinkedHashSet<>();
    @Nullable
    private final JsonLikePsiWalker psiWalker;
    private final Project myProject;

    private Worker(@Nonnull JsonSchemaObject rootSchema,
                   @Nonnull PsiElement completionPsiElement,
                   @Nonnull PsiElement originalPosition,
                   @Nonnull CompletionType completionType,
                   @Nonnull java.util.function.Consumer<Collection<LookupElement>> resultHandler) {
      this.rootSchema = rootSchema;
      this.completionPsiElement = completionPsiElement;
      this.originalPosition = originalPosition;
      this.completionType = completionType;
      this.resultHandler = resultHandler;
      this.myProject = originalPosition.getProject();

      JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(completionPsiElement, rootSchema);
      PsiElement positionParent = completionPsiElement.getParent();
      boolean isInsideQuotedString = (positionParent != null && walker != null && walker.isQuotedString(positionParent));
      this.wrapInQuotes = !isInsideQuotedString;
      this.psiWalker = walker;
      this.insideStringLiteral = isInsideQuotedString;
    }

    @Nullable
    private JsonSchemaCompletionCustomizer getCompletionCustomizer() {
      List<JsonSchemaCompletionCustomizer> customizers = JsonSchemaCompletionCustomizer.EXTENSION_POINT_NAME.getExtensionList().stream()
        .filter(it -> it.isApplicable(originalPosition.getContainingFile()))
        .collect(Collectors.toList());
      return customizers.size() == 1 ? customizers.get(0) : null;
    }

    void work() {
      if (psiWalker == null) return;
      PsiElement checkable = psiWalker.findElementToCheck(completionPsiElement);
      if (checkable == null) return;
      ThreeState isName = psiWalker.isName(checkable);
      JsonPointerPosition position = psiWalker.findPosition(checkable, isName == ThreeState.NO);
      if (position == null || position.isEmpty() && isName == ThreeState.NO) return;

      Set<String> knownNames = new HashSet<>();

      SchemaNode nestedCompletionsNode = JsonSchemaNestedCompletionsTreeProvider.getNestedCompletionsData(originalPosition.getContainingFile())
        .navigate(position);

      JsonSchemaNodeExpansionRequest schemaExpansionRequest = new JsonSchemaNodeExpansionRequest(
        psiWalker.getParentPropertyAdapter(completionPsiElement) != null ? psiWalker.getParentPropertyAdapter(completionPsiElement).getParentObject() : null,
        completionType == CompletionType.SMART
      );

      JsonSchemaCompletionCustomizer completionCustomizer = getCompletionCustomizer();

      new JsonSchemaResolver(myProject, rootSchema, position, schemaExpansionRequest)
        .resolve()
        .forEach(schema -> {
          schema.collectNestedCompletions(myProject, nestedCompletionsNode, (path, subSchema) -> {
            if (completionCustomizer != null && !completionCustomizer.acceptsPropertyCompletionItem(subSchema, completionPsiElement))
              return CompletionNextStep.Stop;
            else {
              processSchema(subSchema, isName, knownNames, path);
              return CompletionNextStep.Continue;
            }
          });
        });

      resultHandler.accept(completionVariants);
    }

    /**
     * @param completionPath Linked node representation of the names of all the parent
     * schema objects that we have navigated for nested completions
     */
    void processSchema(@Nonnull JsonSchemaObject schema,
                       @Nonnull ThreeState isName,
                       @Nonnull Set<String> knownNames,
                       @Nullable SchemaPath completionPath) {
      if (isName != ThreeState.NO) {
        assert psiWalker != null;
        PsiElement completionOriginalPosition = psiWalker.findChildBy(completionPath, originalPosition);
        PsiElement completionPosition = psiWalker.findChildBy(completionPath, completionPsiElement);

        Collection<String> properties = psiWalker.getPropertyNamesOfParentObject(completionOriginalPosition, completionPosition);
        JsonPropertyAdapter adapter = psiWalker.getParentPropertyAdapter(completionOriginalPosition);

        Set<String> forbiddenNames = new HashSet<>(JsonSchemaVariantsTreeBuilder.findPropertiesThatMustNotBePresent(schema, completionPsiElement, myProject, properties));
        forbiddenNames.addAll(properties);
        addAllPropertyVariants(schema, forbiddenNames, adapter, knownNames, completionPath);
        addPropertyNameSchemaVariants(schema);
      }

      if (isName != ThreeState.YES) {
        suggestValues(schema, isName == ThreeState.NO, completionPath);
      }
    }

    void addPropertyNameSchemaVariants(@Nonnull JsonSchemaObject schema) {
      JsonSchemaObject propertyNamesSchema = schema.getPropertyNamesSchema();
      if (propertyNamesSchema == null) return;
      List<Object> anEnum = propertyNamesSchema.getEnum();
      if (anEnum == null) return;
      for (Object o : anEnum) {
        if (!(o instanceof String)) continue;
        String str = (String) o;
        String unquoted = StringUtil.unquoteString(str);
        String toInsert = !shouldWrapInQuotes(unquoted, false) ? unquoted :
          (psiWalker != null ? psiWalker.escapeInvalidIdentifier(unquoted) : StringUtil.wrapWithDoubleQuote(unquoted));
        completionVariants.add(LookupElementBuilder.create(StringUtil.unquoteString(toInsert)));
      }
    }

    void addAllPropertyVariants(@Nonnull JsonSchemaObject schema,
                                @Nonnull Set<String> forbiddenNames,
                                @Nullable JsonPropertyAdapter adapter,
                                @Nonnull Set<String> knownNames,
                                @Nullable SchemaPath completionPath) {
      JsonSchemaCompletionCustomizer completionCustomizer = getCompletionCustomizer();

      StreamEx.of(schema.getPropertyNames())
        .filter(name -> !forbiddenNames.contains(name) && !knownNames.contains(name) || adapter != null && name.equals(adapter.getName()))
        .forEach(name -> {
          knownNames.add(name);
          JsonSchemaObject propertySchema = schema.getPropertyByName(name);
          if (propertySchema == null) throw new IllegalStateException("Property schema cannot be null");
          if (completionCustomizer == null || completionCustomizer.acceptsPropertyCompletionItem(propertySchema, completionPsiElement)) {
            addPropertyVariant(name, propertySchema, completionPath, adapter != null ? adapter.getNameValueAdapter() : null);
          }
        });
    }

    void suggestValues(@Nonnull JsonSchemaObject schema, boolean isSurelyValue, @Nullable SchemaPath completionPath) {
      suggestValuesForSchemaVariants(schema.getAnyOf(), isSurelyValue, completionPath);
      suggestValuesForSchemaVariants(schema.getOneOf(), isSurelyValue, completionPath);
      suggestValuesForSchemaVariants(schema.getAllOf(), isSurelyValue, completionPath);

      if (schema.getEnum() != null && completionPath == null) {
        Map<String, Map<String, String>> metadata = schema.getEnumMetadata();
        boolean isEnumOrderSensitive = Boolean.parseBoolean(schema.readChildNodeValue(X_INTELLIJ_ENUM_ORDER_SENSITIVE));
        List<Object> anEnum = schema.getEnum();
        Set<String> filtered = new HashSet<>(FILTERED_BY_DEFAULT);
        filtered.addAll(getEnumItemsToSkip());
        for (int i = 0; i < anEnum.size(); i++) {
          Object o = anEnum.get(i);
          if (insideStringLiteral && !(o instanceof String)) continue;
          String variant = o.toString();
          if (!filtered.contains(variant) && !filtered.contains(StringUtil.unquoteString(variant))) {
            Map<String, String> valueMetadata = metadata != null ? metadata.get(StringUtil.unquoteString(variant)) : null;
            String description = valueMetadata != null ? valueMetadata.get("description") : null;
            String deprecated = valueMetadata != null ? valueMetadata.get("deprecationMessage") : null;
            Integer order = isEnumOrderSensitive ? i : null;

            JsonSchemaCompletionCustomizer completionCustomizer = getCompletionCustomizer();
            InsertHandler<LookupElement> handler = completionCustomizer != null ?
              completionCustomizer.createHandlerForEnumValue(schema, variant, completionPsiElement) : null;

            addValueVariant(
              variant,
              description,
              null,
              handler,
              order,
              deprecated != null
            );
          }
        }
      }
      else if (isSurelyValue) {
        JsonSchemaType type = JsonSchemaObjectReadingUtils.guessType(schema);
        suggestSpecialValues(type);
        if (type != null) {
          suggestByType(schema, type);
        }
        else if (schema.getTypeVariants() != null) {
          for (JsonSchemaType schemaType : schema.getTypeVariants()) {
            suggestByType(schema, schemaType);
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

    @Nonnull
    private Set<String> getEnumItemsToSkip() {
      // if the parent is an array, and it assumes unique items, we don't suggest the same enum items again
      if (psiWalker == null) return Collections.emptySet();

      PsiElement checkable = psiWalker.findElementToCheck(completionPsiElement);
      if (checkable == null) return Collections.emptySet();

      JsonPointerPosition position = psiWalker.findPosition(checkable, false);
      if (position == null) return Collections.emptySet();

      JsonPointerPosition trimmed = position.trimTail(1);
      if (trimmed == null) return Collections.emptySet();

      Collection<JsonSchemaObject> resolved = new JsonSchemaResolver(myProject, rootSchema, trimmed, null).resolve();
      JsonSchemaObject containerSchema = resolved.size() == 1 ? resolved.iterator().next() : null;

      if (containerSchema != null && containerSchema.isUniqueItems()) {
        // Find parent array
        JsonArrayValueAdapter parentArray = null;
        for (PsiElement element : getParents(completionPsiElement, false)) {
          JsonValueAdapter adapter = psiWalker.createValueAdapter(element);
          if (adapter != null) {
            JsonArrayValueAdapter arrayAdapter = adapter.getAsArray();
            if (arrayAdapter != null) {
              parentArray = arrayAdapter;
              break;
            }
          }
        }

        if (parentArray != null) {
          List<JsonValueAdapter> elements = parentArray.getElements();
          return elements.stream()
            .map(it -> StringUtil.unquoteString(it.getDelegate().getText()))
            .collect(Collectors.toSet());
        }
      }

      return Collections.emptySet();
    }

    void suggestSpecialValues(@Nullable JsonSchemaType type) {
      if (!JsonSchemaVersion.isSchemaSchemaId(rootSchema.getId()) || type != JsonSchemaType._string) return;
      assert psiWalker != null;
      JsonPropertyAdapter propertyAdapter = psiWalker.getParentPropertyAdapter(originalPosition);
      if (propertyAdapter == null) return;
      String name = propertyAdapter.getName();
      if ("required".equals(name)) {
        addRequiredPropVariants();
      }
      else if (X_INTELLIJ_LANGUAGE_INJECTION.equals(name)) {
        addInjectedLanguageVariants();
      }
      else if ("language".equals(name)) {
        JsonObjectValueAdapter parent = propertyAdapter.getParentObject();
        if (parent != null) {
          JsonPropertyAdapter parentAdapter = psiWalker.getParentPropertyAdapter(parent.getDelegate());
          if (parentAdapter != null && X_INTELLIJ_LANGUAGE_INJECTION.equals(parentAdapter.getName())) {
            addInjectedLanguageVariants();
          }
        }
      }
    }

    void addInjectedLanguageVariants() {
      assert psiWalker != null;
      PsiElement checkable = psiWalker.findElementToCheck(completionPsiElement);
      if (!(checkable instanceof JsonStringLiteral) && !(checkable instanceof JsonReferenceExpression)) return;

      Language.getRegisteredLanguages().stream()
        .filter(LanguageUtil::isInjectableLanguage)
        .map(Injectable::fromLanguage)
        .forEach(injectable -> {
          completionVariants.add(
            LookupElementBuilder
              .create(injectable.getId())
              .withIcon(injectable.getIcon())
              .withTailText("(" + injectable.getDisplayName() + ")", true)
          );
        });
    }

    void addRequiredPropVariants() {
      assert psiWalker != null;
      PsiElement checkable = psiWalker.findElementToCheck(completionPsiElement);
      if (!(checkable instanceof JsonStringLiteral) && !(checkable instanceof JsonReferenceExpression)) return;
      JsonObject propertiesObject = JsonRequiredPropsReferenceProvider.findPropertiesObject(checkable);
      if (propertiesObject == null) return;
      PsiElement parent = checkable.getParent();
      Set<String> items;
      if (parent instanceof JsonArray) {
        items = ((JsonArray) parent).getValueList().stream()
          .filter(v -> v instanceof JsonStringLiteral)
          .map(v -> ((JsonStringLiteral) v).getValue())
          .collect(Collectors.toSet());
      }
      else {
        items = Collections.emptySet();
      }

      propertiesObject.getPropertyList().stream()
        .map(JsonProperty::getName)
        .filter(name -> !items.contains(name))
        .forEach(this::addStringVariant);
    }

    void suggestByType(@Nonnull JsonSchemaObject schema, @Nonnull JsonSchemaType type) {
      if (JsonSchemaType._string == type) {
        addPossibleStringValue(schema);
      }
      if (insideStringLiteral) {
        return;
      }
      assert psiWalker != null;
      switch (type) {
        case _boolean:
          addValueVariant("true", null, null, null, null, false);
          addValueVariant("false", null, null, null, null, false);
          break;
        case _null:
          addValueVariant("null", null, null, null, null, false);
          break;
        case _array: {
          String value = psiWalker.getDefaultArrayValue();
          addValueVariant(
            value,
            null,
            "[...]",
            createArrayOrObjectLiteralInsertHandler(
              psiWalker.hasWhitespaceDelimitedCodeBlocks(), value.length()
            ),
            null,
            false
          );
          break;
        }
        case _object: {
          String value = psiWalker.getDefaultObjectValue();
          addValueVariant(
            value,
            null,
            "{...}",
            createArrayOrObjectLiteralInsertHandler(
              psiWalker.hasWhitespaceDelimitedCodeBlocks(), value.length()
            ),
            null,
            false
          );
          break;
        }
        default:
          // no suggestions
          break;
      }
    }

    void addPossibleStringValue(@Nonnull JsonSchemaObject schema) {
      Object defaultValue = schema.getDefault();
      String defaultValueString = defaultValue != null ? defaultValue.toString() : null;
      addStringVariant(defaultValueString);
    }

    void addStringVariant(@Nullable String defaultValueString) {
      if (defaultValueString == null) return;
      assert psiWalker != null;
      String normalizedValue = defaultValueString;
      boolean shouldQuote = psiWalker.requiresValueQuotes();
      boolean isQuoted = StringUtil.isQuotedString(normalizedValue);
      if (shouldQuote && !isQuoted) {
        normalizedValue = StringUtil.wrapWithDoubleQuote(normalizedValue);
      }
      else if (!shouldQuote && isQuoted) {
        normalizedValue = StringUtil.unquoteString(normalizedValue);
      }
      addValueVariant(normalizedValue, null, null, null, null, false);
    }

    void suggestValuesForSchemaVariants(@Nullable List<JsonSchemaObject> list, boolean isSurelyValue, @Nullable SchemaPath completionPath) {
      if (list == null || list.isEmpty()) return;
      for (JsonSchemaObject schemaObject : list) {
        suggestValues(schemaObject, isSurelyValue, completionPath);
      }
    }

    void addValueVariant(@Nonnull String key,
                         @Nullable String description,
                         @Nullable String altText,
                         @Nullable InsertHandler<LookupElement> handler,
                         @Nullable Integer order,
                         boolean deprecated) {
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

    boolean shouldWrapInQuotes(@Nullable String key, boolean isValue) {
      return wrapInQuotes && psiWalker != null &&
             (isValue && psiWalker.requiresValueQuotes() || !isValue && psiWalker.requiresNameQuotes() || key != null && !psiWalker.isValidIdentifier(key, myProject));
    }

    void addPropertyVariant(@Nonnull String key,
                            @Nonnull JsonSchemaObject jsonSchemaObject,
                            @Nullable SchemaPath completionPath,
                            @Nullable JsonValueAdapter sourcePsiAdapter) {
      String propertyKey = key;
      JsonSchemaObject schemaObject = jsonSchemaObject;
      Collection<JsonSchemaObject> variants = new JsonSchemaResolver(myProject, schemaObject, new JsonPointerPosition(), sourcePsiAdapter).resolve();
      schemaObject = ObjectUtil.coalesce(variants.isEmpty() ? null : variants.iterator().next(), schemaObject);
      propertyKey = !shouldWrapInQuotes(propertyKey, false) ? propertyKey :
        (psiWalker != null ? psiWalker.escapeInvalidIdentifier(propertyKey) : StringUtil.wrapWithDoubleQuote(propertyKey));

      List<String> extraLookupStrings = new ArrayList<>();
      List<JsonSchemaMetadataEntry> metadata = jsonSchemaObject.getMetadata();
      if (metadata != null) {
        for (JsonSchemaMetadataEntry entry : metadata) {
          if ("aliases".equals(entry.getKey())) {
            extraLookupStrings.addAll(entry.getValues());
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

    @Nonnull
    private LookupElementBuilder withDeprecation(@Nonnull LookupElementBuilder builder, boolean deprecated) {
      if (!deprecated) return builder;
      return builder.withTailText(JsonBundle.message("schema.documentation.deprecated.postfix"), true).withStrikeoutness(true);
    }

    @Nonnull
    private InsertHandler<LookupElement> choosePropertyInsertHandler(@Nullable SchemaPath completionPath,
                                                                      @Nonnull Collection<JsonSchemaObject> variants,
                                                                      @Nonnull JsonSchemaObject schemaObject) {
      if (hasSameType(variants)) {
        JsonSchemaType type = JsonSchemaObjectReadingUtils.guessType(schemaObject);
        List<Object> values = schemaObject.getEnum();
        if (values != null && !values.isEmpty()) {
          // if we have an enum with a single kind of values - trigger the handler with value
          long distinctCount = values.stream().map(Object::getClass).distinct().count();
          if (distinctCount == 1) {
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

      return createDefaultPropertyInsertHandler(completionPath, schemaObject.getEnum() != null && !schemaObject.getEnum().isEmpty(),
                                                schemaObject.getTypeVariants());
    }

    @Nullable
    private String getDocumentationOrTypeName(@Nonnull JsonSchemaObject schemaObject) {
      String docText = JsonSchemaDocumentationProvider.getBestDocumentation(true, schemaObject);
      if (docText != null && !docText.isBlank()) {
        return findFirstSentence(StringUtil.removeHtmlTags(docText));
      }
      else {
        return JsonSchemaObjectReadingUtils.getTypeDescription(schemaObject, true);
      }
    }

    @Nonnull
    private InsertHandler<LookupElement> createDefaultPropertyInsertHandler(@Nullable SchemaPath completionPath,
                                                                             boolean hasEnumValues,
                                                                             @Nullable Set<JsonSchemaType> valueTypes) {
      return new InsertHandler<LookupElement>() {
        @Override
        public void handleInsert(@Nonnull InsertionContext context, @Nonnull LookupElement item) {
          ApplicationManager.getApplication().assertWriteAccessAllowed();
          Editor editor = context.getEditor();
          Project project = context.getProject();

          expandMissingPropertiesAndMoveCaret(context, completionPath);

          if (handleInsideQuotesInsertion(context, editor, insideStringLiteral)) return;

          assert psiWalker != null;
          boolean insertComma = psiWalker.hasMissingCommaAfter(completionPsiElement);
          String comma = insertComma ? "," : "";
          PsiElement checkable = psiWalker.findElementToCheck(completionPsiElement);
          boolean hasValue = hasEnumValues || (checkable != null && psiWalker.isPropertyWithValue(checkable));

          int offset = editor.getCaretModel().getOffset();
          int initialOffset = offset;
          CharSequence docChars = context.getDocument().getCharsSequence();
          while (offset < docChars.length() && Character.isWhitespace(docChars.charAt(offset))) {
            offset++;
          }

          String propertyValueSeparator;
          if (valueTypes != null) {
            String separator = null;
            for (JsonSchemaType type : valueTypes) {
              separator = psiWalker.getPropertyValueSeparator(type);
              if (!separator.isBlank()) break;
            }
            propertyValueSeparator = separator != null ? separator : psiWalker.getPropertyValueSeparator(valueTypes.size() == 1 ? valueTypes.iterator().next() : null);
          }
          else {
            propertyValueSeparator = psiWalker.getPropertyValueSeparator(null);
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

    @Nonnull
    InsertHandler<LookupElement> createPropertyInsertHandler(@Nonnull JsonSchemaObject jsonSchemaObject,
                                                              @Nullable SchemaPath completionPath) {
      String defaultValueAsString;
      Object defaultValue = jsonSchemaObject.getDefault();
      if (defaultValue == null || defaultValue instanceof JsonSchemaObject) {
        defaultValueAsString = null;
      }
      else if (defaultValue instanceof String) {
        defaultValueAsString = "\"" + defaultValue + "\"";
      }
      else {
        defaultValueAsString = defaultValue.toString();
      }

      List<Object> enumValues = jsonSchemaObject.getEnum();
      JsonSchemaType finalType = JsonSchemaObjectReadingUtils.guessType(jsonSchemaObject);
      if (finalType == null) {
        finalType = detectTypeByEnumValues(enumValues != null ? enumValues : Collections.emptyList());
      }

      assert psiWalker != null;
      return createPropertyInsertHandler(finalType, defaultValueAsString, enumValues, psiWalker, insideStringLiteral, completionPath);
    }
  }

  // some schemas provide an empty array or an empty object in enum values...
  private static final Set<String> FILTERED_BY_DEFAULT = new HashSet<>(Arrays.asList("[]", "{}", "[ ]", "{ }"));
  private static final List<String> COMMON_ABBREVIATIONS = Arrays.asList("e.g.", "i.e.");

  @Nonnull
  private static String findFirstSentence(@Nonnull String sentence) {
    int i = sentence.indexOf(". ");
    while (i >= 0) {
      boolean isAbbreviation = false;
      for (String abbr : COMMON_ABBREVIATIONS) {
        if (sentence.regionMatches(i - abbr.length() + 1, abbr, 0, abbr.length())) {
          isAbbreviation = true;
          break;
        }
      }
      if (!isAbbreviation) {
        return sentence.substring(0, i + 1);
      }
      i = sentence.indexOf(". ", i + 1);
    }
    return sentence;
  }

  @Nonnull
  private static Icon getIcon(@Nullable JsonSchemaType type) {
    if (type == null) {
      return IconManager.getInstance().getPlatformIcon(PlatformIcons.Property);
    }
    switch (type) {
      case _object:
        return AllIcons.Json.Object;
      case _array:
        return AllIcons.Json.Array;
      default:
        return IconManager.getInstance().getPlatformIcon(PlatformIcons.Property);
    }
  }

  private static boolean hasSameType(@Nonnull Collection<JsonSchemaObject> variants) {
    // enum is not a separate type, so we should treat whether it can be an enum distinctly from the types
    long distinctCount = variants.stream()
      .map(it -> new Pair<>(JsonSchemaObjectReadingUtils.guessType(it), isUntypedEnum(it)))
      .distinct()
      .count();
    return distinctCount <= 1;
  }

  private static boolean isUntypedEnum(@Nonnull JsonSchemaObject it) {
    return JsonSchemaObjectReadingUtils.guessType(it) == null && it.getEnum() != null && !it.getEnum().isEmpty();
  }

  @Nonnull
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

  @Nullable
  private static JsonSchemaType detectTypeByEnumValues(@Nonnull List<Object> values) {
    JsonSchemaType type = null;
    for (Object value : values) {
      JsonSchemaType newType = null;
      if (value instanceof Integer) newType = JsonSchemaType._integer;
      if (type != null && type != newType) return null;
      type = newType;
    }
    return type;
  }

  public static void doCompletion(@Nonnull CompletionParameters parameters,
                                  @Nonnull CompletionResultSet result,
                                  @Nonnull JsonSchemaObject rootSchema,
                                  boolean stop) {
    Worker worker = new Worker(rootSchema,
                               parameters.getPosition(),
                               parameters.getOriginalPosition() != null ? parameters.getOriginalPosition() : parameters.getPosition(),
                               parameters.getCompletionType(),
                               result::addAllElements);
    worker.work();
    // stop further completion only if the current contributor has at least one new completion variant
    if (stop && !worker.completionVariants.isEmpty()) {
      result.stopHere();
    }
  }

  @Nonnull
  public static List<LookupElement> getCompletionVariants(@Nonnull JsonSchemaObject schema,
                                                          @Nonnull PsiElement position,
                                                          @Nonnull PsiElement originalPosition,
                                                          @Nonnull CompletionType completionType) {
    List<LookupElement> result = new ArrayList<>();
    new Worker(schema, position, originalPosition, completionType, result::addAll).work();
    return result;
  }

  private static void updateStat(@Nullable JsonSchemaFileProvider provider, @Nullable VirtualFile schemaFile) {
    if (provider == null) {
      if (schemaFile instanceof HttpVirtualFile) {
        // auto-detected and auto-downloaded JSON schemas
        JsonSchemaUsageTriggerCollector.trigger(REMOTE_USAGE_KEY);
      }
      return;
    }
    SchemaType schemaType = provider.getSchemaType();
    String key;
    switch (schemaType) {
      case schema:
        key = SCHEMA_USAGE_KEY;
        break;
      case userSchema:
        key = USER_USAGE_KEY;
        break;
      case embeddedSchema:
        key = BUILTIN_USAGE_KEY;
        break;
      case remoteSchema:
        // this works only for user-specified remote schemas in our settings, but not for auto-detected remote schemas
        key = REMOTE_USAGE_KEY;
        break;
      default:
        return;
    }
    JsonSchemaUsageTriggerCollector.trigger(key);
  }

  private static void invokeEnterHandler(@Nonnull Editor editor) {
    EditorActionHandler handler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
    com.intellij.openapi.editor.Caret caret = editor.getCaretModel().getCurrentCaret();
    handler.execute(editor, caret, EditorActionHandler.caretDataContext(
      DataManager.getInstance().getDataContext(editor.getContentComponent()), caret));
  }

  private static boolean handleInsideQuotesInsertion(@Nonnull InsertionContext context, @Nonnull Editor editor, boolean insideStringLiteral) {
    if (!insideStringLiteral) return false;
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = context.getFile().findElementAt(offset);
    int tailOffset = context.getTailOffset();
    int guessEndOffset = tailOffset + 1;
    if (element instanceof LeafPsiElement) {
      if (handleIncompleteString(editor, element)) return false;
      int endOffset = element.getTextRange().getEndOffset();
      if (endOffset > tailOffset) {
        context.getDocument().deleteString(tailOffset, endOffset - 1);
      }
    }
    if (element != null) {
      JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element);
      if (walker != null) {
        PsiElement checkable = walker.findElementToCheck(element);
        if (walker.isPropertyWithValue(checkable)) return true;
      }
    }
    editor.getCaretModel().moveToOffset(guessEndOffset);
    return false;
  }

  private static boolean handleIncompleteString(@Nonnull Editor editor, @Nonnull PsiElement element) {
    if (!(element instanceof LeafPsiElement)) return false;
    if (((LeafPsiElement) element).getElementType() != TokenType.WHITE_SPACE) return false;

    PsiElement prevSibling = element.getPrevSibling();
    if (prevSibling instanceof JsonProperty) {
      JsonStringLiteral nameElement = ((JsonProperty) prevSibling).getNameElement();
      if (!nameElement.getText().endsWith("\"")) {
        editor.getCaretModel().moveToOffset(nameElement.getTextRange().getEndOffset());
        EditorModificationUtil.insertStringAtCaret(editor, "\"", false, true, 1);
        return true;
      }
    }
    return false;
  }

  @Nonnull
  public static InsertHandler<LookupElement> createPropertyInsertHandler(@Nullable JsonSchemaType finalType,
                                                                          @Nullable String defaultValueAsString,
                                                                          @Nullable List<Object> values,
                                                                          @Nonnull JsonLikePsiWalker walker,
                                                                          boolean insideStringLiteral,
                                                                          @Nullable SchemaPath completionPath) {
    return (context, item) -> {
      ThreadingAssertions.assertWriteAccess();
      Editor editor = context.getEditor();
      Project project = context.getProject();

      expandMissingPropertiesAndMoveCaret(context, completionPath);

      String stringToInsert = null;

      if (handleInsideQuotesInsertion(context, editor, insideStringLiteral)) return;

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
          case _object:
            if (insertColon) {
              EditorModificationUtil.insertStringAtCaret(editor, propertyValueSeparator + " ",
                                                         false, true,
                                                         propertyValueSeparator.length() + 1);
            }
            hadEnter = false;
            boolean invokeEnter = walker.hasWhitespaceDelimitedCodeBlocks();
            if (insertColon && invokeEnter) {
              invokeEnterHandler(editor);
              hadEnter = true;
            }
            if (insertColon) {
              stringToInsert = walker.getDefaultObjectValue() + comma;
              EditorModificationUtil.insertStringAtCaret(editor, stringToInsert,
                                                         false, true,
                                                         hadEnter ? 0 : 1);
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
            break;
          case _boolean: {
            String value = String.valueOf("true".equals(defaultValueAsString));
            stringToInsert = (insertColon ? propertyValueSeparator + " " : " ") + value + comma;
            com.intellij.openapi.editor.SelectionModel model = editor.getSelectionModel();

            EditorModificationUtil.insertStringAtCaret(editor, stringToInsert,
                                                       false, true,
                                                       stringToInsert.length() - comma.length());
            formatInsertedString(context, stringToInsert.length());
            int start = editor.getSelectionModel().getSelectionStart();
            model.setSelection(start - value.length(), start);
            AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
            break;
          }
          case _array:
            if (insertColon) {
              EditorModificationUtilEx.insertStringAtCaret(editor, propertyValueSeparator,
                                                           false, true,
                                                           propertyValueSeparator.length());
            }
            hadEnter = false;
            PsiElement nextSibling = findLeafAtCaret(context, editor, walker);
            if (nextSibling != null) nextSibling = nextSibling.getNextSibling();
            if (insertColon && walker.hasWhitespaceDelimitedCodeBlocks()) {
              invokeEnterHandler(editor);
              hadEnter = true;
            }
            else {
              if (!(nextSibling instanceof PsiWhiteSpace)) {
                EditorModificationUtilEx.insertStringAtCaret(editor, " ", false, true, 1);
              }
              else {
                editor.getCaretModel().moveToOffset(nextSibling.getTextRange().getEndOffset());
              }
            }
            PsiElement currentLeaf = findLeafAtCaret(context, editor, walker);
            if (insertColon || (currentLeaf != null && walker.getPropertyValueSeparator(null).equals(currentLeaf.getText()))) {
              stringToInsert = walker.getDefaultArrayValue() + comma;
              EditorModificationUtil.insertStringAtCaret(editor, stringToInsert,
                                                         false, true,
                                                         hadEnter ? 0 : 1);
            }
            if (hadEnter) {
              EditorActionUtil.moveCaretToLineEnd(editor, false, false);
            }

            PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

            if (stringToInsert != null && walker.requiresReformatAfterArrayInsertion()) {
              formatInsertedString(context, stringToInsert.length());
            }
            break;
          case _string:
          case _integer:
          case _number:
            insertPropertyWithEnum(context, editor,
                                   defaultValueAsString, values,
                                   finalType, comma, walker,
                                   insertColon);
            break;
          default:
            break;
        }
      }
      else {
        insertPropertyWithEnum(context, editor, defaultValueAsString, values, null, comma, walker, insertColon);
      }
    };
  }

  @Nullable
  private static PsiElement findLeafAtCaret(@Nonnull InsertionContext context,
                                            @Nonnull Editor editor,
                                            @Nonnull JsonLikePsiWalker walker) {
    PsiElement element = context.getFile().findElementAt(editor.getCaretModel().getOffset());
    if (element != null) {
      return rewindToMeaningfulLeaf(element);
    }
    return null;
  }

  private static void insertPropertyWithEnum(@Nonnull InsertionContext context,
                                             @Nonnull Editor editor,
                                             @Nullable String defaultValue,
                                             @Nullable List<Object> values,
                                             @Nullable JsonSchemaType type,
                                             @Nonnull String comma,
                                             @Nonnull JsonLikePsiWalker walker,
                                             boolean insertColon) {
    String value = defaultValue;
    String propertyValueSeparator = walker.getPropertyValueSeparator(type);
    if (!walker.requiresValueQuotes() && value != null) {
      value = StringUtil.unquoteString(value);
    }
    boolean isNumber = type != null && (JsonSchemaType._integer == type || JsonSchemaType._number == type) ||
                       type == null && (value != null &&
                                        !StringUtil.isQuotedString(value) ||
                                        values != null && values.stream().allMatch(it -> !(it instanceof String)));
    boolean hasValues = !ContainerUtil.isEmpty(values);
    boolean hasDefaultValue = !StringUtil.isEmpty(value);
    boolean requiresQuotes = !isNumber && walker.requiresValueQuotes();
    int offset = editor.getCaretModel().getOffset();
    CharSequence charSequence = editor.getDocument().getCharsSequence();
    String ws = (charSequence.length() > offset && charSequence.charAt(offset) == ' ') ? "" : " ";
    String colonWs = insertColon ? propertyValueSeparator + ws : ws;
    String stringToInsert = colonWs + (hasDefaultValue ? value : (requiresQuotes ? "\"\"" : "")) + comma;
    EditorModificationUtil.insertStringAtCaret(editor, stringToInsert, false, true,
                                               insertColon ? propertyValueSeparator.length() + 1 : 1);
    if (requiresQuotes || hasDefaultValue) {
      com.intellij.openapi.editor.SelectionModel model = editor.getSelectionModel();
      int caretStart = model.getSelectionStart();
      // if we are already within the value quotes, then the shift is zero, if not yet - move inside
      int quoteOffset = 0;
      if (caretStart - 1 >= 0) {
        char ch = editor.getDocument().getCharsSequence().charAt(caretStart - 1);
        if (ch != '"' && ch != '\'') {
          quoteOffset = 1;
        }
      }
      int newOffset = caretStart + (hasDefaultValue ? value.length() : quoteOffset);
      if (hasDefaultValue && requiresQuotes) newOffset--;
      model.setSelection(requiresQuotes ? (caretStart + quoteOffset) : caretStart, newOffset);
      editor.getCaretModel().moveToOffset(newOffset);
    }

    if (!walker.hasWhitespaceDelimitedCodeBlocks() && !stringToInsert.equals(colonWs + comma)) {
      formatInsertedString(context, stringToInsert.length());
    }

    if (hasValues) {
      AutoPopupController.getInstance(context.getProject()).scheduleAutoPopup(context.getEditor());
    }
  }

  public static void formatInsertedString(@Nonnull InsertionContext context, int offset) {
    Project project = context.getProject();
    PsiDocumentManager.getInstance(project).commitDocument(context.getDocument());
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    codeStyleManager.reformatText(context.getFile(), context.getStartOffset(), context.getTailOffset() + offset);
  }

  private static void expandMissingPropertiesAndMoveCaret(@Nonnull InsertionContext context, @Nullable SchemaPath completionPath) {
    if (completionPath != null) {
      NestedCompletionsNode.expandPropertiesAndMoveCaret(context, completionPath);
    }
  }

  @Nonnull
  private static List<PsiElement> getParents(@Nonnull PsiElement element, boolean includeSelf) {
    List<PsiElement> result = new ArrayList<>();
    PsiElement current = includeSelf ? element : element.getParent();
    while (current != null) {
      result.add(current);
      current = current.getParent();
    }
    return result;
  }
}

