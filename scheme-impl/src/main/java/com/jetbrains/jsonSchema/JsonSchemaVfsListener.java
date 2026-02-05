// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema;

import com.intellij.json.JsonFileType;
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceImpl;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.ReadAction;
import consulo.application.util.concurrent.SequentialTaskExecutor;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.EditorFactory;
import consulo.component.messagebus.MessageBusConnection;
import consulo.disposer.Disposable;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.event.PsiTreeAnyChangeAbstractAdapter;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.event.BulkVirtualFileListenerAdapter;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public final class JsonSchemaVfsListener extends BulkVirtualFileListenerAdapter {
  public static final Topic<Runnable> JSON_SCHEMA_CHANGED = Topic.create("JsonSchemaVfsListener.Json.Schema.Changed", Runnable.class);
  public static final Topic<Runnable> JSON_DEPS_CHANGED = Topic.create("JsonSchemaVfsListener.Json.Deps.Changed", Runnable.class);

  public static @Nonnull JsonSchemaUpdater startListening(@Nonnull Project project, @Nonnull JsonSchemaService service, @Nonnull MessageBusConnection connection) {
    final JsonSchemaUpdater updater = new JsonSchemaUpdater(project, service);
    connection.subscribe(VirtualFileManager.VFS_CHANGES, new JsonSchemaVfsListener(updater));
    PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiTreeAnyChangeAbstractAdapter() {
      @Override
      protected void onChange(@Nullable PsiFile file) {
        if (file != null) updater.onFileChange(file.getViewProvider().getVirtualFile());
      }
    }, (Disposable)service);
    return updater;
  }

  private JsonSchemaVfsListener(@Nonnull JsonSchemaUpdater updater) {
    super(new VirtualFileContentsChangedAdapter() {
      private final @Nonnull JsonSchemaUpdater myUpdater = updater;
      @Override
      protected void onFileChange(final @Nonnull VirtualFile schemaFile) {
        myUpdater.onFileChange(schemaFile);
      }

      @Override
      protected void onBeforeFileChange(@Nonnull VirtualFile schemaFile) {
        myUpdater.onFileChange(schemaFile);
      }
    });
  }

  public static final class JsonSchemaUpdater {
    private static final int DELAY_MS = 200;

    private final @Nonnull Project myProject;
    private final ZipperUpdater myUpdater;
    private final @Nonnull JsonSchemaService myService;
    private final Set<VirtualFile> myDirtySchemas = ConcurrentCollectionFactory.createConcurrentSet();
    private final Runnable myRunnable;
    private final ExecutorService myTaskExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Json Vfs Updater Executor");

    private JsonSchemaUpdater(@Nonnull Project project, @Nonnull JsonSchemaService service) {
      Disposable disposable = (Disposable)service;

      myProject = project;
      myUpdater = new ZipperUpdater(DELAY_MS, ThreadToUse.POOLED_THREAD, disposable);
      myService = service;
      myRunnable = () -> {
        if (myProject.isDisposed()) return;
        Collection<VirtualFile> scope = new HashSet<>(myDirtySchemas);
        if (ContainerUtil.exists(scope, f -> service.possiblyHasReference(f.getName()))) {
          myProject.getMessageBus().syncPublisher(JSON_DEPS_CHANGED).run();
          JsonDependencyModificationTracker.forProject(myProject).incModificationCount();
        }
        myDirtySchemas.removeAll(scope);
        if (scope.isEmpty()) return;

        Collection<VirtualFile> finalScope = ContainerUtil.filter(scope, file -> myService.isApplicableToFile(file)
                                                                                 && ((JsonSchemaServiceImpl)myService).isMappedSchema(file, false));
        if (finalScope.isEmpty()) return;
        if (myProject.isDisposed()) return;
        myProject.getMessageBus().syncPublisher(JSON_SCHEMA_CHANGED).run();

        final DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(project);
        final PsiManager psiManager = PsiManager.getInstance(project);
        final Editor[] editors = EditorFactory.getInstance().getAllEditors();
        Arrays.stream(editors)
              .filter(editor -> editor instanceof EditorEx && editor.getProject() == myProject)
              .map(editor -> editor.getVirtualFile())
              .filter(file -> file != null && file.isValid())
              .forEach(file -> {
                final Collection<VirtualFile> schemaFiles = ((JsonSchemaServiceImpl)myService).getSchemasForFile(file, false, true);
                if (ContainerUtil.exists(schemaFiles, finalScope::contains)) {
                  if (ApplicationManager.getApplication().isUnitTestMode()) {
                    ReadAction.run(() -> restartAnalyzer(analyzer, psiManager, file));
                  }
                  else {
                    ReadAction.nonBlocking(() -> restartAnalyzer(analyzer, psiManager, file))
                      .expireWith(disposable)
                      .submit(myTaskExecutor);
                  }
                }
              });
      };
    }

    private static void restartAnalyzer(@Nonnull DaemonCodeAnalyzer analyzer, @Nonnull PsiManager psiManager, @Nonnull VirtualFile file) {
      PsiFile psiFile = !psiManager.isDisposed() && file.isValid() ? psiManager.findFile(file) : null;
      if (psiFile != null) analyzer.restart(psiFile, "JsonSchemaUpdater");
    }

    private void onFileChange(final @Nonnull VirtualFile schemaFile) {
      if (JsonFileType.DEFAULT_EXTENSION.equals(schemaFile.getExtension())) {
        myDirtySchemas.add(schemaFile);
        Application app = ApplicationManager.getApplication();
        if (app.isUnitTestMode()) {
          app.invokeLater(myRunnable, myProject.getDisposed());
        }
        else {
          myUpdater.queue(myRunnable);
        }
      }
    }
  }
}
