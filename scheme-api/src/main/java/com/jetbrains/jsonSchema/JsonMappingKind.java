// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
package com.jetbrains.jsonSchema;

import consulo.json.localize.JsonLocalize;
import consulo.localize.LocalizeValue;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;

public enum JsonMappingKind {
    File,
    Pattern,
    Directory;

    public LocalizeValue getDescription() {
        return switch (this) {
            case File -> JsonLocalize.schemaMappingFile();
            case Pattern -> JsonLocalize.schemaMappingPattern();
            case Directory -> JsonLocalize.schemaMappingDirectory();
        };
    }

    public LocalizeValue getPrefix() {
        return getDescription().capitalize().map(text -> text + ": ");
    }

    public Image getIcon() {
        return switch (this) {
            case File -> PlatformIconGroup.filetypesAny_type();
            case Pattern -> PlatformIconGroup.filetypesUnknown();
            case Directory -> PlatformIconGroup.nodesFolder();
        };
    }
}
