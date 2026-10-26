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

import com.intellij.json.JsonFileType;
import com.intellij.json.pointer.JsonPointerResolver;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonContainer;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonStringLiteralTextFragment;
import com.intellij.json.psi.JsonValue;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectStorage;
import com.jetbrains.jsonSchema.internal.JsonCachedValues;
import com.jetbrains.jsonSchema.internal.JsonSchemaVariantsTreeBuilder;
import com.jetbrains.jsonSchema.internal.RootJsonSchemaObject;
import consulo.document.util.TextRange;
import consulo.json.icon.JsonIconGroup;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.PrioritizedLookupElement;
import consulo.language.impl.psi.path.WebReference;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.psi.path.FileReference;
import consulo.language.psi.path.FileReferenceSet;
import consulo.language.util.ProcessingContext;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.ui.image.Image;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static com.intellij.json.pointer.JsonPointerUtil.escapeForJsonPointer;
import static com.intellij.json.pointer.JsonPointerUtil.normalizeId;
import static com.intellij.json.pointer.JsonPointerUtil.normalizeSlashes;
import static com.intellij.json.pointer.JsonPointerUtil.split;
import static com.jetbrains.jsonSchema.internal.JsonFileResolver.isHttpPath;

public final class JsonPointerReferenceProvider extends PsiReferenceProvider {
    private final boolean myIsSchemaProperty;

    public JsonPointerReferenceProvider(boolean isSchemaProperty) {
        myIsSchemaProperty = isSchemaProperty;
    }

    @Override
    public PsiReference[] getReferencesByElement(PsiElement element, ProcessingContext context) {
        if (!(element instanceof JsonStringLiteral)) {
            return PsiReference.EMPTY_ARRAY;
        }
        List<PsiReference> refs = new ArrayList<>();

        List<JsonStringLiteralTextFragment> fragments = ((JsonStringLiteral) element).getTextFragments();
        if (fragments.size() != 1) {
            return PsiReference.EMPTY_ARRAY;
        }
        JsonStringLiteralTextFragment fragment = fragments.get(0);
        String originalText = element.getText();
        int hash = originalText.indexOf('#');
        JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter splitter = new JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter(fragment.text());
        String id = splitter.getSchemaId();
        if (id != null) {
            if (id.startsWith("#")) {
                refs.add(new JsonSchemaIdReference((JsonValue) element, id));
            }
            else {
                addFileOrWebReferences(element, refs, hash, id);
            }
        }
        if (!myIsSchemaProperty) {
            String relativePath = normalizeSlashes(normalizeId(splitter.getRelativePath()));
            if (!StringUtil.isEmpty(relativePath)) {
                List<String> parts1 = split(relativePath);
                String[] strings = ContainerUtil.toArray(parts1, String[]::new);
                List<String> parts2 = split(normalizeSlashes(originalText.substring(hash + 1)));
                if (strings.length == parts2.size()) {
                    int start = hash + 2;
                    for (int i = 0; i < parts2.size(); i++) {
                        int length = parts2.get(i).length();
                        if (i == parts2.size() - 1) {
                            length--;
                        }
                        if (length <= 0) {
                            break;
                        }
                        refs.add(new JsonPointerReference(
                            (JsonValue) element,
                            new TextRange(start, start + length),
                            (id == null ? "" : id) + "#/" + StringUtil.join(strings, 0, i + 1, "/")
                        ));
                        start += length + 1;
                    }
                }
            }
        }
        return refs.isEmpty() ? PsiReference.EMPTY_ARRAY : ContainerUtil.toArray(refs, PsiReference[]::new);
    }

    private void addFileOrWebReferences(PsiElement element, List<PsiReference> refs, int hashIndex, String id) {
        if (isHttpPath(id)) {
            refs.add(new WebReference(element, new TextRange(1, hashIndex >= 0 ? hashIndex : id.length() + 1), id));
            return;
        }

        boolean isCompletion = id.contains(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED);

        FileReferenceSet referenceSet = new FileReferenceSet(id, element, 1, null, true, true, new FileType[]{JsonFileType.INSTANCE}) {
            @Override
            public boolean isEmptyPathAllowed() {
                return true;
            }

            @Override
            protected boolean isSoft() {
                return true;
            }

            @Override
            @SuppressWarnings("NullAway")
            public @Nullable FileReference createFileReference(TextRange range, int index, String text) {
                if (hashIndex != -1 && range.getStartOffset() >= hashIndex) {
                    return null;
                }
                TextRange referenceRange = range;
                String referenceText = text;
                if (hashIndex != -1 && range.getEndOffset() > hashIndex) {
                    referenceRange = new TextRange(range.getStartOffset(), hashIndex);
                    referenceText = text.substring(0, text.indexOf('#'));
                }
                return new FileReference(this, referenceRange, index, referenceText) {
                    @Override
                    public Object[] getVariants() {
                        Object[] fileVariants = super.getVariants();
                        if (!isCompletion || getRangeInElement().getStartOffset() != 1) {
                            return fileVariants;
                        }
                        return ArrayUtil.mergeArrays(fileVariants, collectCatalogVariants());
                    }

                    private Object[] collectCatalogVariants() {
                        List<LookupElement> elements = new ArrayList<>();
                        Project project = getElement().getProject();
                        List<JsonSchemaInfo> schemas = JsonSchemaService.get(project).getAllUserVisibleSchemas();
                        for (JsonSchemaInfo schema : schemas) {
                            LookupElementBuilder element = LookupElementBuilder.create(schema.getUrl(project))
                                .withPresentableText(schema.getDescription())
                                .withLookupString(schema.getDescription())
                                .withIcon(PlatformIconGroup.generalWeb())
                                .withTypeText(schema.getDocumentation(), true);
                            String name = schema.getName();
                            if (name != null) {
                                element = element.withLookupString(name);
                            }
                            String documentation = schema.getDocumentation();
                            if (documentation != null) {
                                element = element.withLookupString(documentation);
                            }
                            elements.add(PrioritizedLookupElement.withPriority(element, -1));
                        }
                        return elements.toArray();
                    }
                };
            }
        };
        for (FileReference reference : referenceSet.getAllReferences()) {
            if (reference != null) {
                refs.add(reference);
            }
        }
    }

    static @Nullable PsiElement resolveForPath(PsiElement element, String text, boolean alwaysRoot) {
        JsonSchemaService service = JsonSchemaService.get(element.getProject());
        JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter splitter = new JsonSchemaVariantsTreeBuilder.SchemaUrlSplitter(text);
        VirtualFile schemaFile = CompletionUtilCore.getOriginalOrSelf(element.getContainingFile()).getVirtualFile();
        if (splitter.isAbsolute()) {
            String schemaId = splitter.getSchemaId();
            assert schemaId != null;
            if (schemaFile == null) {
                return null;
            }
            schemaFile = service.findSchemaFileByReference(schemaId, schemaFile);
            if (schemaFile == null) {
                return null;
            }
        }
        if (schemaFile == null) {
            return null;
        }

        PsiFile psiFile = element.getManager().findFile(schemaFile);

        String normalized = normalizeId(splitter.getRelativePath());
        if (!alwaysRoot && (StringUtil.isEmptyOrSpaces(normalized) || split(normalizeSlashes(normalized)).isEmpty())
            || !(psiFile instanceof JsonFile)) {
            return psiFile;
        }
        List<String> chain = split(normalizeSlashes(normalized));
        JsonSchemaObject schemaObject = service.getSchemaObjectForSchemaFile(schemaFile);
        if (schemaObject == null) {
            return null;
        }

        JsonValue value = ((JsonFile) psiFile).getTopLevelValue();
        return value == null ? psiFile : new JsonPointerResolver(value, StringUtil.join(chain, "/")).resolve();
    }

    public static final class JsonSchemaIdReference extends JsonSchemaBaseReference<JsonValue> {
        private final String myText;

        private JsonSchemaIdReference(JsonValue element, String text) {
            super(element, getRange(element));
            myText = text;
        }

        private static TextRange getRange(JsonValue element) {
            TextRange range = element.getTextRange().shiftLeft(element.getTextOffset());
            return new TextRange(range.getStartOffset() + 1, range.getEndOffset() - 1);
        }

        @Override
        public @Nullable PsiElement resolveInner() {
            String id = null;

            JsonSchemaObject schemaRootOrNull = JsonSchemaObjectStorage.getInstance(myElement.getProject())
                .getComputedSchemaRootOrNull(Objects.requireNonNull(Objects.requireNonNull(myElement.getContainingFile()).getVirtualFile()));
            if (schemaRootOrNull instanceof RootJsonSchemaObject<?, ?> rootJsonSchemaObject) {
                id = rootJsonSchemaObject.resolveId(myText);
            }
            if (id == null) {
                id = JsonCachedValues.resolveId(myElement.getContainingFile(), myText);
            }
            if (id == null) {
                return null;
            }
            return resolveForPath(myElement, "#" + id, false);
        }

        @Override
        public Object[] getVariants() {
            return JsonCachedValues.getAllIdsInFile(Objects.requireNonNull(myElement.getContainingFile())).toArray();
        }
    }

    static final class JsonPointerReference extends JsonSchemaBaseReference<JsonValue> {
        private final String myFullPath;

        JsonPointerReference(JsonValue element, TextRange textRange, String curPath) {
            super(element, textRange);
            myFullPath = curPath;
        }

        @Override
        public String getCanonicalText() {
            return myFullPath;
        }

        @Override
        public @Nullable PsiElement resolveInner() {
            return resolveForPath(myElement, getCanonicalText(), false);
        }

        @Override
        protected boolean isIdenticalTo(JsonSchemaBaseReference that) {
            return super.isIdenticalTo(that) && getRangeInElement().equals(that.getRangeInElement());
        }

        @Override
        public Object[] getVariants() {
            String text = getCanonicalText();
            int index = text.indexOf(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED);
            if (index >= 0) {
                String part = text.substring(0, index);
                text = prepare(part);
                String prefix = null;
                PsiElement element = resolveForPath(myElement, text, true);
                int indexOfSlash = part.lastIndexOf('/');
                if (indexOfSlash != -1 && indexOfSlash < text.length() - 1) {
                    prefix = text.substring(indexOfSlash + 1);
                    element = resolveForPath(myElement, prepare(text.substring(0, indexOfSlash)), true);
                }
                String finalPrefix = prefix;
                if (element instanceof JsonObject) {
                    return ((JsonObject) element).getPropertyList().stream()
                        .filter(p -> p.getValue() instanceof JsonContainer && (finalPrefix == null || p.getName().startsWith(finalPrefix)))
                        .map(p -> LookupElementBuilder.create(p, escapeForJsonPointer(p.getName()))
                            .withIcon(getIcon(p.getValue()))).toArray();
                }
                else if (element instanceof JsonArray) {
                    List<JsonValue> list = ((JsonArray) element).getValueList();
                    List<Object> values = new LinkedList<>();
                    for (int i = 0; i < list.size(); i++) {
                        String stringValue = String.valueOf(i);
                        if (prefix != null && !stringValue.startsWith(prefix)) {
                            continue;
                        }
                        values.add(LookupElementBuilder.create(stringValue).withIcon(getIcon(list.get(i))));
                    }
                    return ContainerUtil.toArray(values, Object[]::new);
                }
            }

            return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }

        private static Image getIcon(@Nullable JsonValue value) {
            if (value instanceof JsonObject) {
                return JsonIconGroup.object();
            }
            else if (value instanceof JsonArray) {
                return JsonIconGroup.array();
            }
            return PlatformIconGroup.nodesProperty();
        }
    }

    private static String prepare(String part) {
        return part.endsWith("#/") ? part : StringUtil.trimEnd(part, '/');
    }
}
