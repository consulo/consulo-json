/*
 * Copyright 2013-2015 must-be.org
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
package consulo.json.jom.validation.inspections;

import com.intellij.json.psi.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.json.jom.validation.JsonFileDescriptorProviders;
import consulo.json.jom.validation.NativeArray;
import consulo.json.jom.validation.descriptor.JsonObjectDescriptor;
import consulo.json.jom.validation.descriptor.JsonPropertyDescriptor;
import consulo.language.editor.inspection.LocalInspectionTool;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author VISTALL
 * @since 2015-11-10
 */
@ExtensionImpl
public class PropertyValidationInspection extends LocalInspectionTool {
    @Nonnull
    @Override
    public PsiElementVisitor buildVisitor(@Nonnull final ProblemsHolder holder, boolean isOnTheFly) {
        return new JsonElementVisitor() {
            @Override
            public void visitLiteral(@Nonnull JsonLiteral o) {
                if (o.getParent() instanceof JsonProperty jsonProperty && jsonProperty.getNameElement() == o) {
                    return;
                }

                validateValue(o, holder);
            }

            @Override
            public void visitObject(@Nonnull JsonObject o) {
                validateValue(o, holder);
            }

            @Override
            public void visitArray(@Nonnull JsonArray node) {
                validateValue(node, holder);
            }

            @Override
            public void visitProperty(@Nonnull JsonProperty node) {
                JsonObjectDescriptor rootDescriptor = JsonFileDescriptorProviders.getRootDescriptor(node.getContainingFile());
                if (rootDescriptor == null) {
                    return;
                }

                Collection<JsonProperty> jsProperties = buildPropertiesAsTree(node, rootDescriptor);
                if (jsProperties.isEmpty()) {
                    return;
                }

                JsonObjectDescriptor currentObject = rootDescriptor;
                for (JsonProperty property : jsProperties) {
                    String name = property.getName();
                    if (name == null) {
                        return;
                    }

                    JsonPropertyDescriptor propertyDescriptor = currentObject.getProperty(name);
                    if (propertyDescriptor == null) {
                        if (node == property) {
                            PsiElement nameIdentifier = node.getNameElement();
                            assert nameIdentifier != null;

                            holder.registerProblem(nameIdentifier, "Undefined property", ProblemHighlightType.ERROR);
                        }
                        return;
                    }
                    else if (propertyDescriptor.getValue() instanceof JsonObjectDescriptor) {
                        currentObject = (JsonObjectDescriptor) propertyDescriptor.getValue();
                    }
                    else {
                        return;
                    }
                }
            }
        };
    }

    @RequiredReadAction
    public static Collection<JsonProperty> buildPropertiesAsTree(PsiElement element, @Nullable JsonObjectDescriptor objectDescriptor) {
        JsonObjectDescriptor rootDescriptor =
            objectDescriptor == null ? JsonFileDescriptorProviders.getRootDescriptor(element.getContainingFile()) : objectDescriptor;
        if (rootDescriptor == null) {
            return Collections.emptyList();
        }

        final Deque<JsonProperty> queue = new ArrayDeque<>();
        PsiTreeUtil.treeWalkUp(element, null, (element1, element2) -> {
            if (element1 instanceof JsonProperty) {
                queue.addFirst((JsonProperty) element1);
            }
            return true;
        });
        return queue;
    }

    @Nullable
    @RequiredReadAction
    private static Object getTypeOfExpression(@Nonnull PsiElement node) {
        if (node instanceof JsonLiteral) {
            Class<?> propertyType = null;
            if (node instanceof JsonNumberLiteral) {
                propertyType = Number.class;
            }
            else if (node instanceof JsonStringLiteral) {
                propertyType = String.class;
            }
            else if (node instanceof JsonNullLiteral) {
                propertyType = Void.class;
            }
            else if (node instanceof JsonBooleanLiteral) {
                propertyType = Boolean.class;
            }

            if (propertyType == null) {
                return null;
            }
            return propertyType;
        }
        else if (node instanceof JsonObject) {
            return Object.class;
        }
        else if (node instanceof JsonArray jsonArray) {
            Set<Object> types = new HashSet<>();
            for (JsonValue expression : jsonArray.getValueList()) {
                if (expression == null) {
                    continue;
                }

                Object typeOfExpression = getTypeOfExpression(expression);
                ContainerUtil.addIfNotNull(types, typeOfExpression);
            }

            int size = types.size();
            switch (size) {
                case 0:
                    return null;
                case 1:
                    Object firstItem = ContainerUtil.getFirstItem(types);
                    assert firstItem != null;
                    return new NativeArray(firstItem);
                default:
                    return new NativeArray(Object.class);
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static JsonPropertyDescriptor findPropertyDescriptor(@Nonnull final JsonProperty jsProperty) {
        return CachedValuesManager.getManager(jsProperty.getProject()).createCachedValue(new CachedValueProvider<JsonPropertyDescriptor>() {
            @Nullable
            @Override
            @RequiredReadAction
            public Result<JsonPropertyDescriptor> compute() {
                return Result.create(
                    findPropertyDescriptorImpl(jsProperty),
                    jsProperty,
                    PsiModificationTracker.MODIFICATION_COUNT
                );
            }
        }, false).getValue();
    }

    @Nullable
    @RequiredReadAction
    private static JsonPropertyDescriptor findPropertyDescriptorImpl(@Nonnull JsonProperty jsProperty) {
        JsonObjectDescriptor rootDescriptor = JsonFileDescriptorProviders.getRootDescriptor(jsProperty.getContainingFile());
        if (rootDescriptor == null) {
            return null;
        }

        Collection<JsonProperty> jsProperties = buildPropertiesAsTree(jsProperty, rootDescriptor);
        if (jsProperties.isEmpty()) {
            return null;
        }

        JsonPropertyDescriptor currentProperty = null;
        JsonObjectDescriptor currentObject = rootDescriptor;
        for (JsonProperty property : jsProperties) {
            String name = property.getName();
            if (name == null) {
                return null;
            }

            currentProperty = currentObject.getProperty(name);
            if (currentProperty == null) {
                return null;
            }

            Object value = currentProperty.getValue();
            if (value instanceof JsonObjectDescriptor objectDescriptor) {
                currentObject = objectDescriptor;
            }
            else if (value instanceof NativeArray nativeArray) {
                Object componentType = nativeArray.getComponentType();
                if (componentType instanceof JsonObjectDescriptor objectDescriptor) {
                    currentObject = objectDescriptor;
                }
            }
            else {
                break;
            }
        }

        return currentProperty;
    }

    @RequiredReadAction
    private static void validateValue(@Nonnull PsiElement value, @Nonnull ProblemsHolder holder) {
        Object actualType = getTypeOfExpression(value);
        if (actualType == null) {
            return;
        }
        PsiElement parent = value.getParent();
        if (!(parent instanceof JsonProperty)) {
            return;
        }

        JsonPropertyDescriptor currentProperty = findPropertyDescriptor((JsonProperty) parent);
        if (currentProperty == null) {
            return;
        }

        Object expectedValue = currentProperty.getValue();
        if (!isInheritable(currentProperty, expectedValue, actualType)) {
            holder.registerProblem(
                value,
                "Wrong property value. Expected: " + getSimpleName(expectedValue) +
                    ", actual: " + getSimpleName(actualType),
                ProblemHighlightType.GENERIC_ERROR
            );
        }

        if (currentProperty.isDeprecated()) {
            PsiElement nameIdentifier = ((JsonProperty) parent).getNameElement();
            assert nameIdentifier != null;
            holder.registerProblem(nameIdentifier, "Deprecated property", ProblemHighlightType.LIKE_DEPRECATED);
        }
    }

    public static boolean isInheritable(JsonPropertyDescriptor currentProperty, Object expected, Object actual) {
        // null value
        if (currentProperty.isNullable() && actual == Void.class) {
            return true;
        }

        if (expected instanceof Class && actual instanceof Class) {
            return expected == actual;
        }

        if (expected instanceof JsonObjectDescriptor && actual == Object.class) {
            return true;
        }

        if (expected instanceof NativeArray expectedArray && actual instanceof NativeArray actualArray) {
            return isInheritable(currentProperty, expectedArray.getComponentType(), actualArray.getComponentType());
        }
        return false;
    }

    @Nonnull
    private static String getSimpleName(Object o) {
        if (o instanceof Class aClass) {
            if (o == Void.class) {
                return "null";
            }
            return StringUtil.decapitalize(aClass.getSimpleName());
        }
        else if (o instanceof JsonObjectDescriptor) {
            return getSimpleName(Object.class);
        }
        else if (o instanceof NativeArray nativeArray) {
            return getSimpleName(nativeArray.getComponentType()) + "[]";
        }
        return "null";
    }

    @Nonnull
    @Override
    public LocalizeValue[] getGroupPath() {
        return new LocalizeValue[]{LocalizeValue.localizeTODO("JSON")};
    }

    @Nonnull
    @Override
    public LocalizeValue getGroupDisplayName() {
        throw new UnsupportedOperationException();
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return LocalizeValue.localizeTODO("Property validation");
    }

    @Nonnull
    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.ERROR;
    }
}
