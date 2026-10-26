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
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.JsonLanguage;
import com.intellij.json.pointer.JsonPointerPosition;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import com.jetbrains.jsonSchema.internal.JsonSchemaResolver;
import com.jetbrains.jsonSchema.walker.JsonLikePsiWalker;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.json.localize.JsonLocalize;
import consulo.language.Language;
import consulo.language.editor.documentation.DocumentationMarkup;
import consulo.language.editor.documentation.LanguageDocumentationProvider;
import consulo.language.impl.psi.FakePsiElement;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.path.PsiFileReference;
import consulo.navigation.ItemPresentation;
import consulo.ui.ex.tree.PresentationData;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils.guessType;

@ExtensionImpl(id = "JsonSchemaDocumentationProvider")
public class JsonSchemaDocumentationProvider implements LanguageDocumentationProvider {
    @Override
    public Language getLanguage() {
        return JsonLanguage.INSTANCE;
    }

    @Override
    public @Nullable String getQuickNavigateInfo(PsiElement element, PsiElement originalElement) {
        return findSchemaAndGenerateDoc(element, originalElement, true, null);
    }

    @Override
    public @Nullable String generateDoc(PsiElement element, @Nullable PsiElement originalElement) {
        if (element instanceof FakeDocElement fakeDocElement) {
            return findSchemaAndGenerateDoc(fakeDocElement.myContextElement, originalElement, false, fakeDocElement.myAltName);
        }
        return findSchemaAndGenerateDoc(element, originalElement, false, null);
    }

    public static @Nullable String findSchemaAndGenerateDoc(
        PsiElement element,
        @Nullable PsiElement originalElement,
        boolean preferShort,
        @Nullable String forcedPropName
    ) {
        if (element instanceof FakePsiElement) {
            return null;
        }
        PsiElement docElement = isWhitespaceOrComment(originalElement) || originalElement == null ? element : originalElement;
        if (originalElement != null && hasFileOrPointerReferences(originalElement.getReferences())) {
            return null;
        }
        PsiFile containingFile = docElement.getContainingFile();
        if (containingFile == null) {
            return null;
        }
        JsonSchemaService service = JsonSchemaService.get(docElement.getProject());
        VirtualFile virtualFile = containingFile.getViewProvider().getVirtualFile();
        if (!service.isApplicableToFile(virtualFile)) {
            return null;
        }
        JsonSchemaObject rootSchema = service.getSchemaObject(containingFile);
        if (rootSchema == null) {
            return null;
        }

        return generateDoc(docElement, rootSchema, preferShort, forcedPropName);
    }

    private static boolean hasFileOrPointerReferences(PsiReference[] references) {
        for (PsiReference reference : references) {
            if (reference instanceof PsiFileReference
                || reference instanceof JsonPointerReferenceProvider.JsonSchemaIdReference
                || reference instanceof JsonPointerReferenceProvider.JsonPointerReference) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWhitespaceOrComment(@Nullable PsiElement originalElement) {
        return originalElement instanceof PsiWhiteSpace || originalElement instanceof PsiComment;
    }

    public static @Nullable String generateDoc(
        PsiElement element,
        JsonSchemaObject rootSchema,
        boolean preferShort,
        @Nullable String forcedPropName
    ) {
        JsonLikePsiWalker walker = JsonLikePsiWalker.getWalker(element, rootSchema);
        if (walker == null) {
            return null;
        }

        PsiElement checkable = walker.findElementToCheck(element);
        if (checkable == null) {
            return null;
        }
        JsonPointerPosition position = walker.findPosition(checkable, true);
        if (position == null) {
            return null;
        }
        if (forcedPropName != null) {
            if (isWhitespaceOrComment(element)) {
                position.addFollowingStep(forcedPropName);
            }
            else {
                if (position.isEmpty()) {
                    return null;
                }
                if (position.isArray(position.size() - 1)) {
                    return null;
                }
                position.replaceStep(position.size() - 1, forcedPropName);
            }
        }
        Collection<JsonSchemaObject> schemas =
            new JsonSchemaResolver(element.getProject(), rootSchema, position, walker.createValueAdapter(element)).resolve();

        String htmlDescription = null;
        boolean deprecated = false;
        List<JsonSchemaType> possibleTypes = new ArrayList<>();
        for (JsonSchemaObject schema : schemas) {
            if (htmlDescription == null) {
                htmlDescription = getBestDocumentation(preferShort, schema);
                String message = schema.getDeprecationMessage();
                if (message != null) {
                    if (htmlDescription == null) {
                        htmlDescription = message;
                    }
                    else {
                        htmlDescription = message + "<br/>" + htmlDescription;
                    }
                    deprecated = true;
                }
            }
            if (schema.getType() != null && schema.getType() != JsonSchemaType._any) {
                possibleTypes.add(schema.getType());
            }
            else if (schema.getTypeVariants() != null) {
                possibleTypes.addAll(schema.getTypeVariants());
            }
            else {
                JsonSchemaType guessedType = guessType(schema);
                if (guessedType != null) {
                    possibleTypes.add(guessedType);
                }
            }
        }

        return appendNameTypeAndApi(
            position,
            getThirdPartyApiInfo(element, rootSchema),
            possibleTypes,
            htmlDescription,
            deprecated,
            preferShort
        );
    }

    private static @Nullable String appendNameTypeAndApi(
        JsonPointerPosition position,
        String apiInfo,
        List<JsonSchemaType> possibleTypes,
        @Nullable String htmlDescription,
        boolean deprecated,
        boolean preferShort
    ) {
        if (position.isEmpty()) {
            return htmlDescription;
        }

        String name = position.getLastName();
        if (name == null) {
            return htmlDescription;
        }

        String type = "";
        String schemaType = JsonSchemaObjectReadingUtils.getTypesDescription(false, possibleTypes);
        if (schemaType != null) {
            type = ": " + schemaType;
        }

        String deprecationComment = deprecated ? JsonLocalize.schemaDocumentationDeprecatedPostfix().get() : "";
        if (preferShort) {
            return "<b>" + name + "</b>" + type + apiInfo + deprecationComment + (htmlDescription == null ? "" : ("<br/>" + htmlDescription));
        }
        else {
            return DocumentationMarkup.DEFINITION_START + name + type + apiInfo + deprecationComment + DocumentationMarkup.DEFINITION_END +
                (htmlDescription == null ? "" : (DocumentationMarkup.CONTENT_START + htmlDescription + DocumentationMarkup.CONTENT_END));
        }
    }

    private static String getThirdPartyApiInfo(PsiElement element, JsonSchemaObject rootSchema) {
        JsonSchemaService service = JsonSchemaService.get(element.getProject());
        String apiInfo = "";
        JsonSchemaFileProvider provider = service.getSchemaProvider(rootSchema);
        if (provider != null) {
            String information = provider.getThirdPartyApiInformation();
            if (information != null) {
                apiInfo = "&nbsp;&nbsp;<i>(" + information + ")</i>";
            }
        }
        return apiInfo;
    }

    public static @Nullable String getBestDocumentation(boolean preferShort, JsonSchemaObject schema) {
        String htmlDescription = schema.getHtmlDescription();
        String markdownDescriptionAsHtml = getMarkdownDescriptionAsHtml(schema);
        String description = schema.getDescription();
        String title = schema.getTitle();

        String postProcessedDescription;
        if (preferShort && !StringUtil.isEmptyOrSpaces(title)) {
            postProcessedDescription = null;
        }
        else if (!StringUtil.isEmptyOrSpaces(htmlDescription)) {
            postProcessedDescription = htmlDescription;
        }
        else if (!StringUtil.isEmptyOrSpaces(description)) {
            postProcessedDescription = plainTextPostProcess(description);
        }
        else if (!StringUtil.isEmptyOrSpaces(markdownDescriptionAsHtml)) {
            postProcessedDescription = markdownDescriptionAsHtml;
        }
        else {
            return null;
        }
        return buildDocumentation(title, postProcessedDescription);
    }

    private static String plainTextPostProcess(String text) {
        return StringUtil.escapeXmlEntities(text).replace("\\n", "<br/>");
    }

    private static @Nullable String buildDocumentation(@Nullable String title, @Nullable String postProcessedDescription) {
        if (title == null) {
            if (postProcessedDescription == null) {
                return null;
            }
            else {
                return postProcessedDescription;
            }
        }
        else {
            if (postProcessedDescription == null) {
                return plainTextPostProcess(title);
            }
            else {
                return plainTextPostProcess(title) + "<br/>" + postProcessedDescription;
            }
        }
    }

    private static @Nullable String getMarkdownDescriptionAsHtml(JsonSchemaObject schema) {
        String markdownDescription = getMarkdownDescription(schema);
        if (markdownDescription != null) {
            return plainTextPostProcess(markdownDescription);
        }
        return null;
    }

    private static @Nullable String getMarkdownDescription(JsonSchemaObject schema) {
        String rawValue = schema.readChildNodeValue("markdownDescription");
        if (rawValue == null) {
            return null;
        }
        return StringUtil.unquoteString(rawValue, '\"');
    }

    @Override
    public @Nullable PsiElement getDocumentationElementForLookupItem(PsiManager psiManager, Object object, PsiElement element) {
        if ((element instanceof JsonProperty || isWhitespaceOrComment(element) && element.getParent() instanceof JsonObject)
            && object instanceof String name) {
            PsiElement context = element instanceof JsonProperty property ? property.getNameElement() : element;
            return new FakeDocElement(context, StringUtil.unquoteString(name));
        }
        return null;
    }

    private static final class FakeDocElement extends FakePsiElement {
        private final PsiElement myContextElement;
        private final String myAltName;

        private FakeDocElement(PsiElement context, String name) {
            myContextElement = context;
            myAltName = name;
        }

        @Override
        public PsiElement getParent() {
            return myContextElement;
        }

        @Override
        public TextRange getTextRangeInParent() {
            return myContextElement.getTextRange().shiftLeft(myContextElement.getTextOffset());
        }

        @Override
        public ItemPresentation getPresentation() {
            return new PresentationData(myAltName, null, null, null);
        }
    }
}
