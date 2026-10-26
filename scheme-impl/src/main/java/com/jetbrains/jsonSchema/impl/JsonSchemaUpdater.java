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
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.JsonFileType;
import com.jetbrains.jsonSchema.JsonDependencyModificationTracker;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.remote.RemoteSchemaDownloadListener;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.application.ReadAction;
import consulo.application.progress.ProgressManager;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.application.util.concurrent.SequentialTaskExecutor;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.EditorFactory;
import consulo.component.ProcessCanceledException;
import consulo.component.messagebus.MessageBusConnection;
import consulo.disposer.Disposable;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.event.PsiTreeAnyChangeAbstractAdapter;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.event.BulkFileListenerBackgroundable;
import consulo.virtualFileSystem.event.BulkVirtualFileListenerAdapter;
import consulo.virtualFileSystem.event.VFileEvent;
import consulo.virtualFileSystem.event.VirtualFileCopyEvent;
import consulo.virtualFileSystem.event.VirtualFileEvent;
import consulo.virtualFileSystem.event.VirtualFileListener;
import consulo.virtualFileSystem.event.VirtualFileMoveEvent;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Singleton
@ServiceAPI(ComponentScope.PROJECT)
@ServiceImpl
public final class JsonSchemaUpdater implements Disposable {
    private static final Logger LOG = Logger.getInstance(JsonSchemaUpdater.class);

    private static final long DELAY_MS = 200L;

    private final Project myProject;
    private final Set<VirtualFile> myDirtySchemas = ConcurrentHashMap.newKeySet();
    private final DebouncedChannel myDirtySchemasChanged;
    private final Set<String> myDownloadedSchemas = ConcurrentHashMap.newKeySet();
    private final DebouncedChannel myDownloadedSchemasChanged;

    @Inject
    public JsonSchemaUpdater(Project project) {
        myProject = project;
        myDirtySchemasChanged = new DebouncedChannel("JsonSchemaUpdater consumer", this::consumeDirtySchemas);
        myDownloadedSchemasChanged = new DebouncedChannel("JsonSchemaUpdater remote download consumer", this::consumeDownloadedSchemas);

        // Subscribe once for each project. A test can construct many JsonSchemaServiceImpl instances for one project.
        MessageBusConnection connection = project.getMessageBus().connect(this);
        connection.subscribe(RemoteSchemaDownloadListener.class, this::onSchemaDownloaded);
        VirtualFileListener contentsChangedListener = new VirtualFileListener() {
            @Override
            public void contentsChanged(VirtualFileEvent event) {
                onFileChange(event.getFile());
            }

            @Override
            public void fileCreated(VirtualFileEvent event) {
                onFileChange(event.getFile());
            }

            @Override
            public void beforeFileDeletion(VirtualFileEvent event) {
                onFileChange(event.getFile());
            }

            @Override
            public void beforeFileMovement(VirtualFileMoveEvent event) {
                onFileChange(event.getFile());
            }

            @Override
            public void fileMoved(VirtualFileMoveEvent event) {
                onFileChange(event.getFile());
            }

            @Override
            public void fileCopied(VirtualFileCopyEvent event) {
                onFileChange(event.getFile());
            }

            @Override
            public void fileDeleted(VirtualFileEvent event) {
                onFileChange(event.getFile());
            }

            @Override
            public void beforeContentsChange(VirtualFileEvent event) {
                onFileChange(event.getFile());
            }
        };
        connection.subscribe(BulkFileListenerBackgroundable.class, new BulkFileListenerBackgroundable() {
            @Override
            public void before(List<? extends VFileEvent> events) {
                for (VFileEvent event : events) {
                    BulkVirtualFileListenerAdapter.fireBefore(contentsChangedListener, event);
                }
            }

            @Override
            public void after(List<? extends VFileEvent> events) {
                for (VFileEvent event : events) {
                    BulkVirtualFileListenerAdapter.fireAfter(contentsChangedListener, event);
                }
            }
        });
        PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeAnyChangeAbstractAdapter() {
            @Override
            protected void onChange(@Nullable PsiFile file) {
                if (file != null) {
                    onFileChange(file.getViewProvider().getVirtualFile());
                }
            }
        }, this);
    }

    public static JsonSchemaUpdater getInstance(Project project) {
        return project.getInstance(JsonSchemaUpdater.class);
    }

    private void consumeDirtySchemas() {
        if (myProject.isDisposed()) {
            return;
        }
        JsonSchemaService service = JsonSchemaService.get(myProject);
        Set<VirtualFile> scope = new HashSet<>();
        drainDirtySchemas(scope);

        if (ContainerUtil.exists(scope, f -> service.possiblyHasReference(f.getName()))) {
            myProject.getMessageBus().syncPublisher(JsonSchemaVfsListener.JSON_DEPS_CHANGED).depsChanged();
            JsonDependencyModificationTracker.forProject(myProject).incModificationCount();
        }

        Set<VirtualFile> finalScope = new HashSet<>();
        for (VirtualFile file : scope) {
            if (service.isApplicableToFile(file) && ((JsonSchemaServiceImpl) service).isMappedSchema(file, false)) {
                finalScope.add(file);
            }
        }
        if (finalScope.isEmpty()) {
            return;
        }

        myProject.getMessageBus().syncPublisher(JsonSchemaVfsListener.JSON_SCHEMA_CHANGED).schemaChanged();

        DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(myProject);
        PsiManager psiManager = PsiManager.getInstance(myProject);
        ReadAction.run(() -> {
            List<VirtualFile> editorFiles = new ArrayList<>();
            for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
                if (editor instanceof EditorEx && editor.getProject() == myProject) {
                    VirtualFile file = editor.getVirtualFile();
                    if (file != null && file.isValid()) {
                        editorFiles.add(file);
                    }
                }
            }
            ProgressManager.checkCanceled();
            for (VirtualFile file : editorFiles) {
                Collection<VirtualFile> schemaFiles = service.getSchemasForFile(file, false, true);
                if (ContainerUtil.exists(schemaFiles, finalScope::contains)) {
                    restartAnalyzer(analyzer, psiManager, file);
                }
            }
        });
    }

    private void drainDirtySchemas(Set<VirtualFile> scope) {
        Iterator<VirtualFile> iterator = myDirtySchemas.iterator();
        while (iterator.hasNext()) {
            scope.add(iterator.next());
            iterator.remove();
        }
    }

    private void consumeDownloadedSchemas() {
        if (myProject.isDisposed()) {
            return;
        }
        Set<String> batch = new HashSet<>();
        drainDownloadedSchemas(batch);
        if (batch.isEmpty()) {
            return;
        }
        JsonSchemaService service = JsonSchemaService.get(myProject);
        service.getCatalogManager().updateIfDownloaded(batch);
        service.reset();
        myProject.getMessageBus().syncPublisher(JsonSchemaVfsListener.JSON_SCHEMA_CHANGED).schemaChanged();
    }

    private void drainDownloadedSchemas(Set<String> batch) {
        Iterator<String> iterator = myDownloadedSchemas.iterator();
        while (iterator.hasNext()) {
            batch.add(iterator.next());
            iterator.remove();
        }
    }

    public void onSchemaDownloaded(String url) {
        myDownloadedSchemas.add(url);
        myDownloadedSchemasChanged.send();
    }

    public void onFileChange(VirtualFile schemaFile) {
        if (JsonFileType.DEFAULT_EXTENSION.equals(schemaFile.getExtension())) {
            myDirtySchemas.add(schemaFile);
            myDirtySchemasChanged.send();
        }
    }

    @Override
    public void dispose() {
        myDirtySchemasChanged.close();
        myDownloadedSchemasChanged.close();
    }

    private static void restartAnalyzer(DaemonCodeAnalyzer analyzer, PsiManager psiManager, VirtualFile file) {
        PsiFile psiFile = !psiManager.isDisposed() && file.isValid() ? psiManager.findFile(file) : null;
        if (psiFile != null) {
            analyzer.restart(psiFile);
        }
    }

    private static final class DebouncedChannel {
        private final Runnable myConsumer;
        private final ExecutorService myExecutor;
        private final AtomicLong mySignals = new AtomicLong();
        private final AtomicBoolean myScheduled = new AtomicBoolean();
        private volatile boolean myClosed;

        private DebouncedChannel(String name, Runnable consumer) {
            myConsumer = consumer;
            myExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor(name);
        }

        private void send() {
            mySignals.incrementAndGet();
            if (myScheduled.compareAndSet(false, true)) {
                awaitQuiet(mySignals.get());
            }
        }

        private void awaitQuiet(long seenSignals) {
            if (myClosed) {
                myScheduled.set(false);
                return;
            }
            if (Application.get().isUnitTestMode()) {
                consume();
                return;
            }
            AppExecutorUtil.getAppScheduledExecutorService().schedule(() -> {
                long signals = mySignals.get();
                if (signals != seenSignals) {
                    awaitQuiet(signals);
                }
                else {
                    consume();
                }
            }, DELAY_MS, TimeUnit.MILLISECONDS);
        }

        private void consume() {
            myScheduled.set(false);
            if (myClosed) {
                return;
            }
            myExecutor.execute(() -> {
                if (myClosed) {
                    return;
                }
                try {
                    myConsumer.run();
                }
                catch (ProcessCanceledException ignored) {
                }
                catch (Exception e) {
                    LOG.error(e);
                }
            });
        }

        private void close() {
            myClosed = true;
        }
    }
}
