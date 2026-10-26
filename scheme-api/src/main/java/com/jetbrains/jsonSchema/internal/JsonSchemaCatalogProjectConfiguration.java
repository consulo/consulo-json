// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema.internal;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.disposer.Disposable;
import consulo.disposer.Disposer;
import consulo.project.Project;
import consulo.util.collection.Lists;
import consulo.util.xml.serializer.annotation.Tag;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
@State(name = "JsonSchemaCatalogProjectConfiguration", storages = @Storage("jsonCatalog.xml"))
public final class JsonSchemaCatalogProjectConfiguration implements PersistentStateComponent<JsonSchemaCatalogProjectConfiguration.MyState> {
    public volatile MyState myState = new MyState();
    private final List<Runnable> myChangeHandlers = Lists.newLockFreeCopyOnWriteList();

    public boolean isCatalogEnabled() {
        MyState state = getState();
        return state != null && state.myIsCatalogEnabled;
    }

    public boolean isPreferRemoteSchemas() {
        MyState state = getState();
        return state != null && state.myIsPreferRemoteSchemas;
    }

    public boolean isImplicitSchemasEnabled() {
        MyState state = getState();
        return state == null || state.myIsImplicitSchemasEnabled;
    }

    public void addChangeHandler(Runnable runnable, Disposable parentDisposable) {
        myChangeHandlers.add(runnable);
        Disposer.register(parentDisposable, () -> myChangeHandlers.remove(runnable));
    }

    public static JsonSchemaCatalogProjectConfiguration getInstance(Project project) {
        return project.getInstance(JsonSchemaCatalogProjectConfiguration.class);
    }

    public JsonSchemaCatalogProjectConfiguration() {
    }

    public void setState(boolean isEnabled, boolean isRemoteActivityEnabled, boolean isPreferRemoteSchemas) {
        MyState state = getState();
        setState(isEnabled,
            isRemoteActivityEnabled,
            isPreferRemoteSchemas,
            state == null || state.myIsImplicitSchemasEnabled);
    }

    public void setState(boolean isEnabled,
                         boolean isRemoteActivityEnabled,
                         boolean isPreferRemoteSchemas,
                         boolean isImplicitSchemasEnabled) {
        myState = new MyState(isEnabled, isRemoteActivityEnabled, isPreferRemoteSchemas, isImplicitSchemasEnabled);
        for (Runnable handler : myChangeHandlers) {
            handler.run();
        }
    }

    public void setCatalogEnabled(boolean isEnabled) {
        MyState state = getState();
        setState(isEnabled,
            state == null || state.myIsRemoteActivityEnabled,
            state != null && state.myIsPreferRemoteSchemas,
            state == null || state.myIsImplicitSchemasEnabled);
    }

    @Override
    public @Nullable MyState getState() {
        return myState;
    }

    public boolean isRemoteActivityEnabled() {
        MyState state = getState();
        return state != null && state.myIsRemoteActivityEnabled;
    }

    @Override
    public void loadState(MyState state) {
        myState = state;
        for (Runnable handler : myChangeHandlers) {
            handler.run();
        }
    }

    public static final class MyState {
        @Tag("enabled")
        public boolean myIsCatalogEnabled = true;

        @Tag("remoteActivityEnabled")
        public boolean myIsRemoteActivityEnabled = true;

        @Tag("preferRemoteSchemas")
        public boolean myIsPreferRemoteSchemas = false;

        @Tag("implicitSchemasEnabled")
        public boolean myIsImplicitSchemasEnabled = true;

        MyState() {
        }

        MyState(boolean isCatalogEnabled,
                boolean isRemoteActivityEnabled,
                boolean isPreferRemoteSchemas,
                boolean isImplicitSchemasEnabled) {
            myIsCatalogEnabled = isCatalogEnabled;
            myIsRemoteActivityEnabled = isRemoteActivityEnabled;
            myIsPreferRemoteSchemas = isPreferRemoteSchemas;
            myIsImplicitSchemasEnabled = isImplicitSchemasEnabled;
        }
    }
}
