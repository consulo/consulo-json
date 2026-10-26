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
package com.jetbrains.jsonSchema.extension;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * This API provides a mechanism to enable JSON schemas in particular files
 * This interface should be implemented if you want a particular kind of virtual files to have access to JsonSchemaService APIs
 *
 * This API is new in IntelliJ IDEA Platform 2018.2
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface JsonSchemaEnabler {

    /**
     * This method should return true if JSON schema mechanism should become applicable to corresponding file.
     * This method SHOULD NOT ADDRESS INDEXES.
     * @param file Virtual file to check for
     * @param project Current project
     * @return true if available, false otherwise
     */
    boolean isEnabledForFile(VirtualFile file, @Nullable Project project);

    /**
     * This method enables/disables JSON schema selection widget
     * This method SHOULD NOT ADDRESS INDEXES
     */
    default boolean shouldShowSwitcherWidget(VirtualFile file) {
        return true;
    }

    default boolean canBeSchemaFile(VirtualFile file) {
        return false;
    }
}
