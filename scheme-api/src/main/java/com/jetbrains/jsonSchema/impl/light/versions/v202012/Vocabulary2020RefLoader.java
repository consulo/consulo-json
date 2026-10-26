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
package com.jetbrains.jsonSchema.impl.light.versions.v202012;

import com.jetbrains.jsonSchema.impl.light.StandardJsonSchemaVocabulary;
import com.jetbrains.jsonSchema.impl.light.VocabularySchemaRefLoader;

import java.util.List;

public final class Vocabulary2020RefLoader extends VocabularySchemaRefLoader {
    public static final Vocabulary2020RefLoader INSTANCE = new Vocabulary2020RefLoader();

    private Vocabulary2020RefLoader() {
        super(List.of(
            new StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2020-12/vocab/core",
                                                     "https://json-schema.org/draft/2020-12/meta/core",
                                                     "jsonSchema/vocabularies/2020-12/core.json"),
            new StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2020-12/vocab/applicator",
                                                     "https://json-schema.org/draft/2020-12/meta/applicator",
                                                     "jsonSchema/vocabularies/2020-12/applicator.json"),
            new StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2020-12/vocab/unevaluated",
                                                     "https://json-schema.org/draft/2020-12/meta/unevaluated",
                                                     "jsonSchema/vocabularies/2020-12/unevaluated.json"),
            new StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2020-12/vocab/validation",
                                                     "https://json-schema.org/draft/2020-12/meta/validation",
                                                     "jsonSchema/vocabularies/2020-12/validation.json"),
            new StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2020-12/vocab/meta-data",
                                                     "https://json-schema.org/draft/2020-12/meta/meta-data",
                                                     "jsonSchema/vocabularies/2020-12/meta-data.json"),
            new StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2020-12/vocab/format-annotation",
                                                     "https://json-schema.org/draft/2020-12/meta/format-annotation",
                                                     "jsonSchema/vocabularies/2020-12/format-annotation.json"),
            new StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2020-12/vocab/content",
                                                     "https://json-schema.org/draft/2020-12/meta/content",
                                                     "jsonSchema/vocabularies/2020-12/content.json")
        ));
    }

    @Override
    public String toString() {
        return "Vocabulary2020RefLoader";
    }
}
