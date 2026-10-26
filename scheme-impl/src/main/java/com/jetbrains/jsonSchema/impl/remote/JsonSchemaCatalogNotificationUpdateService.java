// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema.impl.remote;

import com.jetbrains.jsonSchema.JsonSchemaService;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.fileEditor.EditorNotifications;
import consulo.project.Project;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public final class JsonSchemaCatalogNotificationUpdateService implements Disposable {
    private final Project myProject;

    private final Runnable myUpdateCallback;

    private volatile boolean myRegistered = false;

    @Inject
    public JsonSchemaCatalogNotificationUpdateService(Project project) {
        myProject = project;
        myUpdateCallback = () -> {
            if (!myProject.isDisposed()) {
                EditorNotifications.getInstance(myProject).updateAllNotifications();
            }
        };
    }

    public static JsonSchemaCatalogNotificationUpdateService getInstance(Project project) {
        return project.getInstance(JsonSchemaCatalogNotificationUpdateService.class);
    }

    public void ensureCallbacksRegistered() {
        if (myRegistered) {
            return;
        }
        synchronized (myUpdateCallback) {
            if (myRegistered) {
                return;
            }
            JsonSchemaService service = JsonSchemaService.get(myProject);
            service.registerRemoteUpdateCallback(myUpdateCallback);
            service.registerResetAction(myUpdateCallback);
            Disposer.register(this, () -> {
                service.unregisterRemoteUpdateCallback(myUpdateCallback);
                service.unregisterResetAction(myUpdateCallback);
                myRegistered = false;
            });
            myRegistered = true;
        }
    }

    @Override
    public void dispose() {
    }
}
