// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.psi.impl;

import com.intellij.json.JsonDialectUtil;
import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.*;
import consulo.document.util.TextRange;
import consulo.json.icon.JsonIconGroup;
import consulo.json.localize.JsonLocalize;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.navigation.ItemPresentation;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.intellij.json.JsonTokenSets.STRING_LITERALS;

public final class JsonPsiImplUtils {
    static final Key<List<JsonStringLiteralTextFragment>> STRING_FRAGMENTS = new Key<>("JSON string fragments");

    @NotNull
    public static String getName(@NotNull JsonProperty property) {
        PsiElement name = property.getNameElement();
        // Below is a highly optimized version of:
        // String text = InjectedLanguageManager.getInstance(property.getProject()).getUnescapedText(property.getNameElement());
        // to avoid calls to PsiElement.getProject() and to avoid walking visitor
        InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(property.getProject());
        String text = languageManager.getUnescapedLeafText(name, false);
        if (text == null) {
            text = Objects.requireNonNull(languageManager.getUnescapedLeafText(name.getFirstChild(), false));
        }
        return JsonTextLiteralService.getInstance().unquoteAndUnescape(text);
    }

    /**
     * Actually only JSON string literal should be accepted as valid name of property according to standard,
     * but for compatibility with JavaScript integration any JSON literals as well as identifiers (unquoted words)
     * are possible and highlighted as error later.
     *
     * @see JsonStandardComplianceInspection
     */
    public static @NotNull JsonValue getNameElement(@NotNull JsonProperty property) {
        final PsiElement firstChild = property.getFirstChild();
        assert firstChild instanceof JsonLiteral || firstChild instanceof JsonReferenceExpression;
        return (JsonValue) firstChild;
    }

    public static @Nullable JsonValue getValue(@NotNull JsonProperty property) {
        return PsiTreeUtil.getNextSiblingOfType(getNameElement(property), JsonValue.class);
    }

    public static boolean isQuotedString(@NotNull JsonLiteral literal) {
        return literal.getNode().findChildByType(STRING_LITERALS) != null;
    }

    public static @Nullable ItemPresentation getPresentation(final @NotNull JsonProperty property) {
        return new ItemPresentation() {
            @Override
            public @Nullable String getPresentableText() {
                return property.getName();
            }

            @Override
            public @Nullable String getLocationString() {
                final JsonValue value = property.getValue();
                return value instanceof JsonLiteral ? value.getText() : null;
            }

            @Override
            public @Nullable Image getIcon() {
                if (property.getValue() instanceof JsonArray) {
                    return JsonIconGroup.array();
                }
                if (property.getValue() instanceof JsonObject) {
                    return JsonIconGroup.object();
                }
                return PlatformIconGroup.nodesProperty();
            }
        };
    }

    public static @Nullable ItemPresentation getPresentation(final @NotNull JsonArray array) {
        return new ItemPresentation() {
            @Override
            public @Nullable String getPresentableText() {
                return JsonLocalize.jsonArray().get();
            }

            @jakarta.annotation.Nullable
            @Override
            public String getLocationString() {
                return null;
            }

            @Override
            public @Nullable Image getIcon() {
                return JsonIconGroup.array();
            }
        };
    }

    public static @Nullable ItemPresentation getPresentation(final @NotNull JsonObject object) {
        return new ItemPresentation() {
            @Override
            public @Nullable String getPresentableText() {
                return JsonLocalize.jsonObject().get();
            }

            @jakarta.annotation.Nullable
            @Override
            public String getLocationString() {
                return null;
            }

            @Override
            public @Nullable Image getIcon(boolean unused) {
                return JsonIconGroup.json();
            }
        };
    }

    private static final String ourEscapesTable = "\"\"\\\\//b\bf\fn\nr\rt\t";

    public static @NotNull List<JsonStringLiteralTextFragment> getTextFragments(@NotNull JsonStringLiteral literal) {
        List<JsonStringLiteralTextFragment> result = literal.getUserData(STRING_FRAGMENTS);
        if (result == null) {
            result = new ArrayList<>();
            final String text = literal.getText();
            final int length = text.length();
            int pos = 1, unescapedSequenceStart = 1;
            while (pos < length) {
                if (text.charAt(pos) == '\\') {
                    if (unescapedSequenceStart != pos) {
                        result.add(new JsonStringLiteralTextFragment(new TextRange(unescapedSequenceStart, pos), text.substring(unescapedSequenceStart, pos)));
                    }
                    if (pos == length - 1) {
                        result.add(new JsonStringLiteralTextFragment(new TextRange(pos, pos + 1), "\\"));
                        break;
                    }
                    final char next = text.charAt(pos + 1);
                    switch (next) {
                        case '"':
                        case '\\':
                        case '/':
                        case 'b':
                        case 'f':
                        case 'n':
                        case 'r':
                        case 't':
                            final int idx = ourEscapesTable.indexOf(next);
                            result.add(new JsonStringLiteralTextFragment(new TextRange(pos, pos + 2), ourEscapesTable.substring(idx + 1, idx + 2)));
                            pos += 2;
                            break;
                        case 'u':
                            int i = pos + 2;
                            for (; i < pos + 6; i++) {
                                if (i == length || !StringUtil.isHexDigit(text.charAt(i))) {
                                    break;
                                }
                            }
                            result.add(new JsonStringLiteralTextFragment(new TextRange(pos, i), text.substring(pos, i)));
                            pos = i;
                            break;
                        case 'x':
                            Language language = JsonDialectUtil.getLanguageOrDefaultJson(literal);
                            if (language instanceof JsonLanguage && ((JsonLanguage) language).hasPermissiveStrings()) {
                                int i2 = pos + 2;
                                for (; i2 < pos + 4; i2++) {
                                    if (i2 == length || !StringUtil.isHexDigit(text.charAt(i2))) {
                                        break;
                                    }
                                }
                                result.add(new JsonStringLiteralTextFragment(new TextRange(pos, i2), text.substring(pos, i2)));
                                pos = i2;
                                break;
                            }
                        default:
                            result.add(new JsonStringLiteralTextFragment(new TextRange(pos, pos + 2), text.substring(pos, pos + 2)));
                            pos += 2;
                    }
                    unescapedSequenceStart = pos;
                }
                else {
                    pos++;
                }
            }
            final int contentEnd = text.charAt(0) == text.charAt(length - 1) ? length - 1 : length;
            if (unescapedSequenceStart < contentEnd) {
                result.add(new JsonStringLiteralTextFragment(new TextRange(unescapedSequenceStart, contentEnd), text.substring(unescapedSequenceStart, contentEnd)));
            }
            result = Collections.unmodifiableList(result);
            literal.putUserData(STRING_FRAGMENTS, result);
        }
        return result;
    }

    public static void delete(@NotNull JsonProperty property) {
        final ASTNode myNode = property.getNode();
        JsonPsiChangeUtils.removeCommaSeparatedFromList(myNode, myNode.getTreeParent());
    }

    public static @NotNull String getValue(@NotNull JsonStringLiteral literal) {
        return JsonTextLiteralService.getInstance().unquoteAndUnescape(literal.getText());
    }

    public static boolean isPropertyName(@NotNull JsonStringLiteral literal) {
        final PsiElement parent = literal.getParent();
        return parent instanceof JsonProperty && ((JsonProperty) parent).getNameElement() == literal;
    }

    public static boolean getValue(@NotNull JsonBooleanLiteral literal) {
        return literal.textMatches("true");
    }

    public static double getValue(@NotNull JsonNumberLiteral literal) {
        return Double.parseDouble(literal.getText());
    }
}
