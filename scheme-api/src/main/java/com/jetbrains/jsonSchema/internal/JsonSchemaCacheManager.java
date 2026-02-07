// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.internal;

import com.intellij.json.internal.JsonRegistry;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.internal.ProgressIndicatorUtils;
import consulo.application.progress.ProgressManager;
import consulo.component.ProcessCanceledException;
import consulo.disposer.Disposable;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.util.collection.Maps;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public final class JsonSchemaCacheManager implements Disposable {

    private final ConcurrentMap<VirtualFile, CachedValue<CompletableFuture<JsonSchemaObject>>> cache =
        Maps.newConcurrentWeakHashMap();

    /**
     * Computes {@link JsonSchemaObject} preventing multiple concurrent computations of the same schema.
     */
    @Nullable
    public JsonSchemaObject computeSchemaObject(@Nonnull VirtualFile schemaVirtualFile, @Nonnull PsiFile schemaPsiFile) {
        if (JsonRegistry.JSON_SCHEME_OBJECT_V2) {
            assert false : "Should not use cache with the new json object impl";
        }
        CompletableFuture<JsonSchemaObject> newFuture = new CompletableFuture<>();
        CachedValue<CompletableFuture<JsonSchemaObject>> cachedValue = getUpToDateFuture(schemaVirtualFile, schemaPsiFile, newFuture);
        CompletableFuture<JsonSchemaObject> cachedFuture = cachedValue.value;
        if (cachedFuture == newFuture) {
            completeSync(schemaVirtualFile, schemaPsiFile, cachedFuture);
        }
        try {
            return ProgressIndicatorUtils.awaitWithCheckCanceled(cachedFuture, ProgressManager.getInstance().getProgressIndicator());
        }
        catch (ProcessCanceledException e) {
            ProgressManager.checkCanceled(); // rethrow PCE if this thread's progress is cancelled

            // `ProgressManager.checkCanceled()` was passed => the thread's progress is not cancelled
            // => PCE was thrown because of `cachedFuture.completeExceptionally(PCE)`
            // => evict cached PCE and re-compute the schema
            cache.remove(schemaVirtualFile, cachedValue);

            // The recursion shouldn't happen more than once:
            // Now, the thread's progress is not cancelled.
            // If on the second `computeSchemaObject(...)` call we happened to catch PCE again, then
            // a write action has started => `ProgressManager.checkCanceled()` should throw PCE
            // preventing the third `computeSchemaObject(...)` call.
            return computeSchemaObject(schemaVirtualFile, schemaPsiFile);
        }
    }

    @Nonnull
    private CachedValue<CompletableFuture<JsonSchemaObject>> getUpToDateFuture(@Nonnull VirtualFile schemaVirtualFile,
                                                                  @Nonnull PsiFile schemaPsiFile,
                                                                  @Nonnull CompletableFuture<JsonSchemaObject> newFuture) {
        return cache.compute(schemaVirtualFile, (key, prevValue) -> {
            long virtualFileModStamp = schemaVirtualFile.getModificationStamp();
            long psiFileModStamp = schemaPsiFile.getModificationStamp();
            if (prevValue != null && prevValue.virtualFileModStamp == virtualFileModStamp && prevValue.psiFileModStamp == psiFileModStamp) {
                return prevValue;
            }
            else {
                return new CachedValue<>(newFuture, virtualFileModStamp, psiFileModStamp);
            }
        });
    }

    private void completeSync(@Nonnull VirtualFile schemaVirtualFile, @Nonnull PsiFile schemaPsiFile, @Nonnull CompletableFuture<JsonSchemaObject> future) {
        try {
            future.complete(new JsonSchemaReader(schemaVirtualFile).read(schemaPsiFile));
        }
        catch (Exception e) {
            future.completeExceptionally(e);
        }
    }

    @Override
    public void dispose() {
        cache.clear();
    }

    private static class CachedValue<T> {
        final T value;
        final long virtualFileModStamp;
        final long psiFileModStamp;

        CachedValue(T value, long virtualFileModStamp, long psiFileModStamp) {
            this.value = value;
            this.virtualFileModStamp = virtualFileModStamp;
            this.psiFileModStamp = psiFileModStamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            CachedValue<?> that = (CachedValue<?>) o;
            return virtualFileModStamp == that.virtualFileModStamp &&
                psiFileModStamp == that.psiFileModStamp &&
                Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, virtualFileModStamp, psiFileModStamp);
        }
    }

    @Nonnull
    public static JsonSchemaCacheManager getInstance(@Nonnull Project project) {
        return project.getService(JsonSchemaCacheManager.class);
    }
}

