// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
        return project.getService(JsonDependencyModificationTracker.class);
    }
}
