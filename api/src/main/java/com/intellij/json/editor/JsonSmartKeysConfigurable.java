// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.editor;

import consulo.configurable.Configurable;
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.disposer.Disposable;
import consulo.json.localize.JsonLocalize;
import consulo.localize.LocalizeValue;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

public final class JsonSmartKeysConfigurable extends SimpleConfigurableByProperties implements Disposable, Configurable {

  @RequiredUIAccess
  @Nonnull
  @Override
  protected Component createLayout(@Nonnull PropertyBuilder propertyBuilder, @Nonnull Disposable uiDisposable) {
    JsonEditorOptions settings = JsonEditorOptions.getInstance();

    VerticalLayout layout = VerticalLayout.create();

    CheckBox commaOnEnterCb = CheckBox.create(JsonLocalize.settingsSmartKeysInsertMissingCommaOnEnter());
    propertyBuilder.add(commaOnEnterCb, settings::isCommaOnEnter, settings::setCommaOnEnter);
    layout.add(commaOnEnterCb);

    CheckBox commaOnMatchingBracesCb = CheckBox.create(JsonLocalize.settingsSmartKeysInsertMissingCommaAfterMatchingBracesAndQuotes());
    propertyBuilder.add(commaOnMatchingBracesCb, settings::isCommaOnMatchingBraces, settings::setCommaOnMatchingBraces);
    layout.add(commaOnMatchingBracesCb);

    CheckBox commaOnPasteCb = CheckBox.create(JsonLocalize.settingsSmartKeysAutomaticallyManageCommasWhenPastingJsonFragments());
    propertyBuilder.add(commaOnPasteCb, settings::isCommaOnPaste, settings::setCommaOnPaste);
    layout.add(commaOnPasteCb);

    CheckBox escapePastedTextCb = CheckBox.create(JsonLocalize.settingsSmartKeysEscapeTextOnPasteInStringLiterals());
    propertyBuilder.add(escapePastedTextCb, settings::isEscapePastedText, settings::setEscapePastedText);
    layout.add(escapePastedTextCb);

    CheckBox autoQuotePropNameCb = CheckBox.create(JsonLocalize.settingsSmartKeysAutomaticallyAddQuotesToPropertyNamesWhenTypingComma());
    propertyBuilder.add(autoQuotePropNameCb, settings::isAutoQuotePropName, settings::setAutoQuotePropName);
    layout.add(autoQuotePropNameCb);

    CheckBox autoWhitespaceAfterColonCb = CheckBox.create(JsonLocalize.settingsSmartKeysAutomaticallyAddWhitespaceWhenTypingCommaAfterPropertyNames());
    propertyBuilder.add(autoWhitespaceAfterColonCb, settings::isAutoWhitespaceAfterColon, settings::setAutoWhitespaceAfterColon);
    layout.add(autoWhitespaceAfterColonCb);

    CheckBox colonMoveOutsideQuotesCb = CheckBox.create(JsonLocalize.settingsSmartKeysAutomaticallyMoveCommaAfterThePropertyNameIfTypedInsideQuotes());
    propertyBuilder.add(colonMoveOutsideQuotesCb, settings::isColonMoveOutsideQuotes, settings::setColonMoveOutsideQuotes);
    layout.add(colonMoveOutsideQuotesCb);

    CheckBox commaMoveOutsideQuotesCb = CheckBox.create(JsonLocalize.settingsSmartKeysAutomaticallyMoveCommaAfterThePropertyValueOrArrayElementIfInsideQuotes());
    propertyBuilder.add(commaMoveOutsideQuotesCb, settings::isCommaMoveOutsideQuotes, settings::setCommaMoveOutsideQuotes);
    layout.add(commaMoveOutsideQuotesCb);

    return layout;
  }

  @Nonnull
  @Override
  public LocalizeValue getDisplayName() {
    return JsonLocalize.configurableJsonsmartkeysconfigurableDisplayName();
  }

  @Nullable
  @Override
  public String getHelpTopic() {
    return "reference.settings.editor.smart.keys.json";
  }

  @Override
  public void dispose() {
  }
}
