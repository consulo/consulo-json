// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.widget;

import com.intellij.json.JsonBundle;
import com.jetbrains.jsonSchema.JsonSchemaCatalogProjectConfiguration;
import com.jetbrains.jsonSchema.JsonSchemaMappingsProjectConfiguration;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo;
import consulo.json.localize.JsonLocalize;
import consulo.project.Project;
import consulo.ui.ex.popup.JBPopupFactory;
import consulo.ui.ex.popup.ListPopup;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JsonSchemaStatusPopup {
  static final JsonSchemaInfo ADD_MAPPING = new JsonSchemaInfo("") {
    @Override
    public @Nonnull String getDescription() {
      return JsonLocalize.schemaWidgetAddMapping().get();
    }
  };

  static final JsonSchemaInfo IGNORE_FILE = new JsonSchemaInfo("") {

    @Override
    public @Nls @Nonnull String getDescription() {
      return JsonLocalize.schemaWidgetNoMapping().get();
    }
  };

  static final JsonSchemaInfo STOP_IGNORE_FILE = new JsonSchemaInfo("") {

    @Override
    public @Nls @Nonnull String getDescription() {
      return JsonLocalize.schemaWidgetStopIgnoreFile().get();
    }
  };

  static final JsonSchemaInfo EDIT_MAPPINGS = new JsonSchemaInfo("") {
    @Override
    public @Nonnull String getDescription() {
      return JsonLocalize.schemaWidgetEditMappings().get();
    }
  };

  public static final JsonSchemaInfo LOAD_REMOTE = new JsonSchemaInfo("") {
    @Override
    public @Nonnull String getDescription() {
      return JsonLocalize.schemaWidgetLoadMappings().get();
    }
  };

  static ListPopup createPopup(@Nonnull JsonSchemaService service,
                               @Nonnull Project project,
                               @Nonnull VirtualFile virtualFile,
                               boolean showOnlyEdit) {
    JsonSchemaInfoPopupStep step = createPopupStep(service, project, virtualFile, showOnlyEdit);
    return JBPopupFactory.getInstance().createListPopup(step);
  }

  static @Nonnull JsonSchemaInfoPopupStep createPopupStep(@Nonnull JsonSchemaService service,
                                                          @Nonnull Project project,
                                                          @Nonnull VirtualFile virtualFile,
                                                          boolean showOnlyEdit) {
    List<JsonSchemaInfo> allSchemas;
    JsonSchemaMappingsProjectConfiguration configuration = JsonSchemaMappingsProjectConfiguration.getInstance(project);
    UserDefinedJsonSchemaConfiguration mapping = configuration.findMappingForFile(virtualFile);
    if (!showOnlyEdit || mapping == null) {
      List<JsonSchemaInfo> infos = service.getAllUserVisibleSchemas();
      Comparator<JsonSchemaInfo> comparator = Comparator.comparing(JsonSchemaInfo::getDescription, String::compareToIgnoreCase);
      Stream<JsonSchemaInfo> registered = infos.stream().filter(i -> i.getProvider() != null).sorted(comparator);
      List<JsonSchemaInfo> otherList = List.of();

      if (JsonSchemaCatalogProjectConfiguration.getInstance(project).isRemoteActivityEnabled()) {
        otherList = infos.stream().filter(i -> i.getProvider() == null).sorted(comparator).collect(Collectors.toList());
        if (otherList.isEmpty()) {
          otherList = ContainerUtil.createMaybeSingletonList(LOAD_REMOTE);
        }
      }
      allSchemas = Stream.concat(registered, otherList.stream()).collect(Collectors.toList());
      allSchemas.add(0, mapping == null ? ADD_MAPPING : EDIT_MAPPINGS);
    }
    else {
      allSchemas = new SmartList<>(EDIT_MAPPINGS);
    }

    if (configuration.isIgnoredFile(virtualFile)) {
      allSchemas.add(0, STOP_IGNORE_FILE);
    }
    else {
      allSchemas.add(0, IGNORE_FILE);
    }
    return new JsonSchemaInfoPopupStep(allSchemas, project, virtualFile, service, null);
  }
}
