// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.highlighting;

import com.intellij.json.JsonLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.colorScheme.TextAttributesKey;
import consulo.colorScheme.setting.AttributesDescriptor;
import consulo.colorScheme.setting.ColorDescriptor;
import consulo.json.localize.JsonLocalize;
import consulo.language.Language;
import consulo.language.editor.colorScheme.setting.RainbowColorSettingsPage;
import consulo.language.editor.highlight.SyntaxHighlighter;
import consulo.language.editor.highlight.SyntaxHighlighterFactory;
import consulo.localize.LocalizeValue;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

import static com.intellij.json.highlighting.JsonSyntaxHighlighterFactory.*;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl
public final class JsonColorsPage implements RainbowColorSettingsPage {
  private static final Map<String, TextAttributesKey> ourAdditionalHighlighting = Map.of("propertyKey", JSON_PROPERTY_KEY);

  private static final AttributesDescriptor[] ourAttributeDescriptors = new AttributesDescriptor[]{
    new AttributesDescriptor(JsonLocalize.colorPageAttributePropertyKey(), JSON_PROPERTY_KEY),

    new AttributesDescriptor(JsonLocalize.colorPageAttributeBraces(), JSON_BRACES),
    new AttributesDescriptor(JsonLocalize.colorPageAttributeBrackets(), JSON_BRACKETS),
    new AttributesDescriptor(JsonLocalize.colorPageAttributeComma(), JSON_COMMA),
    new AttributesDescriptor(JsonLocalize.colorPageAttributeColon(), JSON_COLON),
    new AttributesDescriptor(JsonLocalize.colorPageAttributeNumber(), JSON_NUMBER),
    new AttributesDescriptor(JsonLocalize.colorPageAttributeString(), JSON_STRING),
    new AttributesDescriptor(JsonLocalize.colorPageAttributeKeyword(), JSON_KEYWORD),
    new AttributesDescriptor(JsonLocalize.colorPageAttributeLineComment(), JSON_LINE_COMMENT),
    new AttributesDescriptor(JsonLocalize.colorPageAttributeBlockComment(), JSON_BLOCK_COMMENT),
    new AttributesDescriptor(JsonLocalize.colorPageAttributeValidEscapeSequence(), JSON_VALID_ESCAPE),
    new AttributesDescriptor(JsonLocalize.colorPageAttributeInvalidEscapeSequence(), JSON_INVALID_ESCAPE),
    new AttributesDescriptor(JsonLocalize.colorPageAttributeParameter(), JSON_PARAMETER)
  };

  @Override
  public @NotNull SyntaxHighlighter getHighlighter() {
    return SyntaxHighlighterFactory.getSyntaxHighlighter(JsonLanguage.INSTANCE, null, null);
  }

  @Override
  public @NotNull String getDemoText() {
    return """
      {
        // Line comments are not included in standard but nonetheless allowed.
        /* As well as block comments. */
        <propertyKey>"the only keywords are"</propertyKey>: [true, false, null],
        <propertyKey>"strings with"</propertyKey>: {
          <propertyKey>"no escapes"</propertyKey>: "pseudopolinomiality"
          <propertyKey>"valid escapes"</propertyKey>: "C-style\\r\\n and unicode\\u0021",
          <propertyKey>"illegal escapes"</propertyKey>: "\\0377\\x\\"
        },
        <propertyKey>"some numbers"</propertyKey>: [
          42,
          -0.0e-0,
          6.626e-34
        ]\s
      }""";
  }

  @Override
  public @NotNull Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
    return ourAdditionalHighlighting;
  }

  @Override
  public AttributesDescriptor @NotNull [] getAttributeDescriptors() {
    return ourAttributeDescriptors;
  }

  @Override
  public ColorDescriptor @NotNull [] getColorDescriptors() {
    return ColorDescriptor.EMPTY_ARRAY;
  }

  @Override
  public @NotNull LocalizeValue getDisplayName() {
    return JsonLocalize.settingsDisplayNameJson();
  }

  @Override
  public boolean isRainbowType(TextAttributesKey type) {
    return JSON_PROPERTY_KEY.equals(type)
      || JSON_BRACES.equals(type)
      || JSON_BRACKETS.equals(type)
      || JSON_STRING.equals(type)
      || JSON_NUMBER.equals(type)
      || JSON_KEYWORD.equals(type);
  }

  @Override
  public @NotNull Language getLanguage() {
    return JsonLanguage.INSTANCE;
  }
}
