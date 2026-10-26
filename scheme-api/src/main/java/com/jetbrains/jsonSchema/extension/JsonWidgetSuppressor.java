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

/**
 * Implement to suppress showing JSON widget for particular files where assistance is powered by a custom provider.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface JsonWidgetSuppressor {

    /**
     * Allows to check whether widget for the file should be suppressed or not.
     * This method is called on EDT.
     */
    default boolean isCandidateForSuppress(VirtualFile file, Project project) {
        return false;
    }

    /**
     * Allows to suppress JSON widget for particular files.
     * This method is called only if {@link #isCandidateForSuppress(VirtualFile, Project)}
     * return {@code true} for the given file in the given project.
     * <br>This method is called on a background thread under read action with progress indicator.
     * Implementors might want to call {@link consulo.application.progress.ProgressManager#checkCanceled()}
     * time to time to check whether widget suppression is still actual for the given file.
     * For instance progress indicator is canceled if another editor tab is selected.
     */
    boolean suppressSwitcherWidget(VirtualFile file, Project project);
}
