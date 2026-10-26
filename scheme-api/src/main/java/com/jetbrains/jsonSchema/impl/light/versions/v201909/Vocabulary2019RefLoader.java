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
package com.jetbrains.jsonSchema.impl.light.versions.v201909;

import com.jetbrains.jsonSchema.impl.light.StandardJsonSchemaVocabulary;
import com.jetbrains.jsonSchema.impl.light.VocabularySchemaRefLoader;

import java.util.List;

public final class Vocabulary2019RefLoader extends VocabularySchemaRefLoader {
    public static final Vocabulary2019RefLoader INSTANCE = new Vocabulary2019RefLoader();

    private Vocabulary2019RefLoader() {
        super(List.of(
            new StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2019-09/vocab/core",
                                                     "https://json-schema.org/draft/2019-09/meta/core",
                                                     "jsonSchema/vocabularies/2019-09/core.json"),
            new StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2019-09/vocab/applicator",
                                                     "https://json-schema.org/draft/2019-09/meta/applicator",
                                                     "jsonSchema/vocabularies/2019-09/applicator.json"),
            new StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2019-09/vocab/validation",
                                                     "https://json-schema.org/draft/2019-09/meta/validation",
                                                     "jsonSchema/vocabularies/2019-09/validation.json"),
            new StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2019-09/vocab/meta-data",
                                                     "https://json-schema.org/draft/2019-09/meta/meta-data",
                                                     "jsonSchema/vocabularies/2019-09/meta-data.json"),
            new StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2019-09/vocab/format",
                                                     "https://json-schema.org/draft/2019-09/meta/format",
                                                     "jsonSchema/vocabularies/2019-09/format.json"),
            new StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2019-09/vocab/content",
                                                     "https://json-schema.org/draft/2019-09/meta/content",
                                                     "jsonSchema/vocabularies/2019-09/content.json")
        ));
    }

    @Override
    public String toString() {
        return "Vocabulary2019RefLoader";
    }
}
