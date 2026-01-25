// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light;

import com.intellij.json.JsonFileType;
import consulo.language.file.light.LightVirtualFile;
import consulo.logging.Logger;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

public abstract class StandardJsonSchemaVocabulary {
  private static final Logger LOG = Logger.getInstance(StandardJsonSchemaVocabulary.class);

  private final String id;

  protected StandardJsonSchemaVocabulary(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }

  @Nullable
  public abstract VirtualFile load();

  public static class Bundled extends StandardJsonSchemaVocabulary {
    private final String remoteUrl;
    private final String resourcePath;
    private VirtualFile loadedVocabularyFile;
    private boolean loaded = false;

    public Bundled(String id, String remoteUrl, String resourcePath) {
      super(id);
      this.remoteUrl = remoteUrl;
      this.resourcePath = resourcePath;
    }

    public String getRemoteUrl() {
      return remoteUrl;
    }

    public String getResourcePath() {
      return resourcePath;
    }

    @Override
    @Nullable
    public VirtualFile load() {
      if (!loaded) {
        try {
          loadedVocabularyFile = loadBundledSchemaUnsafe();
        } catch (IOException exception) {
          LOG.warn("Unable to load bundled schema for vocabulary", exception);
          loadedVocabularyFile = null;
        }
        loaded = true;
      }
      return loadedVocabularyFile;
    }

    @Nullable
    private LightVirtualFile loadBundledSchemaUnsafe() throws IOException {
      InputStream resourceStream = this.getClass().getClassLoader().getResourceAsStream(resourcePath);
      if (resourceStream == null) {
        return null;
      }

      String schemaText = FileUtil.loadTextAndClose(resourceStream);
      String schemaName = resourcePath.substring(resourcePath.lastIndexOf("/") + 1) + ".json";
      return new LightVirtualFile(schemaName, JsonFileType.INSTANCE, schemaText);
    }
  }

  public static class Remote extends StandardJsonSchemaVocabulary {
    private final String url;

    public Remote(String id, String url) {
      super(id);
      this.url = url;
    }

    public String getUrl() {
      return url;
    }

    @Override
    @Nullable
    public VirtualFile load() {
      URL remoteSchemaUrl;
      try {
        remoteSchemaUrl = new URL(url);
      } catch (MalformedURLException exception) {
        LOG.warn("Unable to parse URL for json schema vocabulary", exception);
        return null;
      }
      return VirtualFileUtil.findFileByURL(remoteSchemaUrl);
    }
  }
}
