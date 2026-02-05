// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light;

import com.fasterxml.jackson.databind.JsonNode;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase;
import com.jetbrains.jsonSchema.impl.JsonSchemaObjectReadingUtils;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.stream.Stream;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.VOCABULARY;

public class JsonSchemaVocabularyResolverKt {

  @Nullable
  public static JsonSchemaObject resolveVocabulary(String searchedVocabularyId,
                                                   JsonSchemaObjectBackedByJacksonBase currentSchemaNode,
                                                   JsonSchemaService jsonSchemaService,
                                                   List<StandardJsonSchemaVocabulary.Bundled> bundledVocabularies) {
    JsonNode rootNode = currentSchemaNode.getRootSchemaObject().getRawSchemaNode();
    Stream<String> instanceVocabularyIds = JacksonSchemaNodeAccessor.readNodeKeys(rootNode, VOCABULARY);
    if (instanceVocabularyIds == null) {
      return null;
    }

    StandardJsonSchemaVocabulary vocabularyToLoad = findBundledVocabulary(searchedVocabularyId, instanceVocabularyIds, bundledVocabularies);
    if (vocabularyToLoad == null) {
      instanceVocabularyIds = JacksonSchemaNodeAccessor.readNodeKeys(rootNode, VOCABULARY);
      vocabularyToLoad = findRemoteVocabulary(searchedVocabularyId, instanceVocabularyIds);
    }

    if (vocabularyToLoad == null) {
      return null;
    }

    VirtualFile vocabularyVirtualFile = vocabularyToLoad.load();
    if (vocabularyVirtualFile == null) {
      return null;
    }

    JsonSchemaObject result = JsonSchemaObjectReadingUtils.downloadAndParseRemoteSchema(jsonSchemaService, vocabularyVirtualFile);
    if (result == JsonSchemaObjectReadingUtils.NULL_OBJ) {
      return null;
    }
    return result;
  }

  @Nullable
  private static StandardJsonSchemaVocabulary findRemoteVocabulary(String maybeVocabularyId, Stream<String> instanceVocabularyIds) {
    return instanceVocabularyIds
      .map(id -> new StandardJsonSchemaVocabulary.Remote(id, id))
      .filter(remoteVocabulary -> remoteVocabulary.getId().endsWith(maybeVocabularyId))
      .findFirst()
      .orElse(null);
  }

  @Nullable
  private static StandardJsonSchemaVocabulary findBundledVocabulary(String maybeVocabularyId,
                                                                      Stream<String> instanceVocabularyIds,
                                                                      List<StandardJsonSchemaVocabulary.Bundled> bundledVocabularies) {
    List<String> instanceIdsList = instanceVocabularyIds.toList();
    return bundledVocabularies.stream()
      .filter(vocabulary -> vocabulary.getRemoteUrl().endsWith(maybeVocabularyId) &&
                             instanceIdsList.stream().anyMatch(instanceId -> vocabulary.getId().equals(instanceId)))
      .findFirst()
      .orElse(null);
  }
}
