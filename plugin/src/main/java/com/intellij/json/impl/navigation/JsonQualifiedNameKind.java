// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json.impl.navigation;

import consulo.json.localize.JsonLocalize;
import consulo.localize.LocalizeValue;

public enum JsonQualifiedNameKind {
    Qualified,
    JsonPointer;

    public LocalizeValue getText() {
        return switch (this) {
            case Qualified -> JsonLocalize.qualifiedNameQualified();
            case JsonPointer -> JsonLocalize.qualifiedNamePointer();
        };
    }
}
