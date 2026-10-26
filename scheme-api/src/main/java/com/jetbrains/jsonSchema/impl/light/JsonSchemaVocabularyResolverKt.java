// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema.impl.light;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.light.legacy.JsonSchemaObjectReadingUtils;
import com.jetbrains.jsonSchema.impl.light.nodes.JacksonSchemaNodeAccessor;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

import static com.jetbrains.jsonSchema.impl.light.SchemaKeywords.VOCABULARY;

public final class JsonSchemaVocabularyResolverKt {
    private JsonSchemaVocabularyResolverKt() {
    }

    public static @Nullable JsonSchemaObject resolveVocabulary(String searchedVocabularyId,
                                                               JsonSchemaObjectBackedByJacksonBase currentSchemaNode,
                                                               JsonSchemaService jsonSchemaService,
                                                               List<StandardJsonSchemaVocabulary.Bundled> bundledVocabularies) {
        Stream<String> instanceVocabularyIdsStream =
            JacksonSchemaNodeAccessor.INSTANCE.readNodeKeys(currentSchemaNode.getRootSchemaObject().getRawSchemaNode(), VOCABULARY);
        if (instanceVocabularyIdsStream == null) {
            return null;
        }
        List<String> instanceVocabularyIds = instanceVocabularyIdsStream.toList();

        StandardJsonSchemaVocabulary vocabularyToLoad = findBundledVocabulary(searchedVocabularyId, instanceVocabularyIds, bundledVocabularies);
        if (vocabularyToLoad == null) {
            vocabularyToLoad = findRemoteVocabulary(searchedVocabularyId, instanceVocabularyIds);
        }
        if (vocabularyToLoad == null) {
            return null;
        }

        VirtualFile vocabularyVirtualFile = vocabularyToLoad.load(jsonSchemaService.getProject());
        if (vocabularyVirtualFile == null) {
            return null;
        }
        JsonSchemaObject result = JsonSchemaObjectReadingUtils.downloadAndParseRemoteSchema(jsonSchemaService, vocabularyVirtualFile);
        // todo get rid of this
        return result != JsonSchemaObjectReadingUtils.NULL_OBJ ? result : null;
    }

    private static @Nullable StandardJsonSchemaVocabulary findRemoteVocabulary(String maybeVocabularyId,
                                                                               List<String> instanceVocabularyIds) {
        return instanceVocabularyIds.stream()
            .map(id -> new StandardJsonSchemaVocabulary.Remote(id, id))
            .filter(remoteVocabulary -> remoteVocabulary.getId().endsWith(maybeVocabularyId))
            .findFirst()
            .orElse(null);
    }

    private static @Nullable StandardJsonSchemaVocabulary findBundledVocabulary(String maybeVocabularyId,
                                                                                List<String> instanceVocabularyIds,
                                                                                List<StandardJsonSchemaVocabulary.Bundled> bundledVocabularies) {
        return bundledVocabularies.stream()
            .filter(vocabulary -> vocabulary.getRemoteUrl().endsWith(maybeVocabularyId)
                && instanceVocabularyIds.stream().anyMatch(instanceId -> vocabulary.getId().equals(instanceId)))
            .findFirst()
            .orElse(null);
    }
}
