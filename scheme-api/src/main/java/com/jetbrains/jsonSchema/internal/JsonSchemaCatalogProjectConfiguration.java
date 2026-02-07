// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.project.Project;
import consulo.util.collection.Lists;
import consulo.util.xml.serializer.annotation.Tag;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

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

    public void addChangeHandler(Runnable runnable) {
        myChangeHandlers.add(runnable);
    }

    public static JsonSchemaCatalogProjectConfiguration getInstance(final @Nonnull Project project) {
        return project.getService(JsonSchemaCatalogProjectConfiguration.class);
    }

    public JsonSchemaCatalogProjectConfiguration() {
    }

    public void setState(boolean isEnabled, boolean isRemoteActivityEnabled, boolean isPreferRemoteSchemas) {
        myState = new MyState(isEnabled, isRemoteActivityEnabled, isPreferRemoteSchemas);
        for (Runnable handler : myChangeHandlers) {
            handler.run();
        }
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
    public void loadState(@Nonnull MyState state) {
        myState = state;
        for (Runnable handler : myChangeHandlers) {
            handler.run();
        }
    }

    static final class MyState {
        @Tag("enabled")
        public boolean myIsCatalogEnabled = true;

        @Tag("remoteActivityEnabled")
        public boolean myIsRemoteActivityEnabled = true;

        @Tag("preferRemoteSchemas")
        public boolean myIsPreferRemoteSchemas = false;

        MyState() {
        }

        MyState(boolean isCatalogEnabled, boolean isRemoteActivityEnabled, boolean isPreferRemoteSchemas) {
            myIsCatalogEnabled = isCatalogEnabled;
            myIsRemoteActivityEnabled = isRemoteActivityEnabled;
            myIsPreferRemoteSchemas = isPreferRemoteSchemas;
        }
    }
}
