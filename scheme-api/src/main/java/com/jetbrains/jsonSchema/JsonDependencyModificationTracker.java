// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.util.SimpleModificationTracker;
import consulo.project.Project;
import jakarta.inject.Singleton;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public final class JsonDependencyModificationTracker extends SimpleModificationTracker {
    public static JsonDependencyModificationTracker forProject(Project project) {
        return project.getInstance(JsonDependencyModificationTracker.class);
    }
}
