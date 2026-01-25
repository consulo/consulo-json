// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.widget;

import com.jetbrains.jsonSchema.fus.*;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.util.concurrent.AppExecutorUtil;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.http.HttpVirtualFile;
import consulo.virtualFileSystem.http.RemoteFileInfo;
import consulo.virtualFileSystem.http.RemoteFileState;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public final class FailedSchemaLoadingDiagnostics {
  private FailedSchemaLoadingDiagnostics() {}

  public static void logSchemaDownloadFailureDiagnostics(HttpVirtualFile schemaFile, Project project) {
    String schemaPath = schemaFile.getPath();
    File nioSchemaFile = null;
    boolean nioFileExists = false;
    boolean isFile = false;
    long nioFileLength = -2;
    boolean canRead = false;

    try {
      Path nioPath = schemaFile.toNioPath();
      if (nioPath != null) {
        nioSchemaFile = nioPath.toFile();
        nioFileExists = nioSchemaFile.exists();
        isFile = nioSchemaFile.isFile();
        nioFileLength = nioSchemaFile.length();
        canRead = nioSchemaFile.canRead();
      }
    }
    catch (Exception ignored) {
      // Ignore exceptions when getting NIO file info
    }

    RemoteFileInfo fileInfo = schemaFile.getFileInfo();
    List<String> errorMessages = new ArrayList<>();
    if (fileInfo != null && fileInfo.getErrorMessage() != null) {
      errorMessages.add(fileInfo.getErrorMessage());
    }
    if (errorMessages.isEmpty()) {
      errorMessages.add("none");
    }
    String errorMessage = String.join(", ", errorMessages);

    RemoteFileState stateOrNull = fileInfo != null ? fileInfo.getState() : null;
    String instantData = "Schema loading failure report. SchemaPath: " + schemaPath + ", " +
                         "File exists: " + nioFileExists + ", isFile: " + isFile + ", File length: " + nioFileLength + " bytes, " +
                         "Can read: " + canRead + ", Error message: " + errorMessage + ", State: " + (stateOrNull != null ? stateOrNull : "unknown");

    File finalNioSchemaFile = nioSchemaFile;
    boolean finalNioFileExists = nioFileExists;
    boolean finalCanRead = canRead;
    long finalNioFileLength = nioFileLength;

    DiagnosticsScopeProvider.getInstance(project).submit(() -> {
      VirtualFile anotherFileLookupAttempt = null;
      try {
        Path nioPath = schemaFile.toNioPath();
        if (nioPath != null) {
          anotherFileLookupAttempt = VirtualFileManager.getInstance().findFileByNioPath(nioPath);
        }
      }
      catch (Exception ignored) {
        // Ignore exceptions
      }

      boolean isNull = anotherFileLookupAttempt == null;
      boolean isValid = anotherFileLookupAttempt != null && anotherFileLookupAttempt.isValid();

      JsonHttpFileLoadingUsageCollector.jsonSchemaHighlightingSessionData.log(
        JsonHttpFileNioFile.with(finalNioFileExists),
        JsonHttpFileNioFileCanBeRead.with(finalCanRead),
        JsonHttpFileNioFileLength.with(finalNioFileLength),
        JsonHttpFileVfsFile.with(fileInfo != null && fileInfo.getLocalFile() != null),
        JsonHttpFileSyncRefreshVfsFile.with(anotherFileLookupAttempt != null),
        JsonHttpFileVfsFileValidity.with(isValid),
        JsonHttpFileDownloadState.with(JsonRemoteSchemaDownloadState.fromRemoteFileState(stateOrNull))
      );

      Logger.getInstance(schemaFile.getClass()).error(instantData + ", syncRefreshFileIsNull: " + isNull + ", syncRefreshFileIsValid: " + isValid);
    });
  }

  @Singleton
  @ServiceAPI(ComponentScope.PROJECT)
  @ServiceImpl
  public static class DiagnosticsScopeProvider {
    private final ExecutorService executorService;

    @Inject
    public DiagnosticsScopeProvider() {
      this.executorService = AppExecutorUtil.createBoundedApplicationPoolExecutor("JsonSchemaDiagnostics", 1);
    }

    public static DiagnosticsScopeProvider getInstance(Project project) {
      return project.getInstance(DiagnosticsScopeProvider.class);
    }

    public void submit(Runnable task) {
      CompletableFuture.runAsync(task, executorService);
    }
  }
}
