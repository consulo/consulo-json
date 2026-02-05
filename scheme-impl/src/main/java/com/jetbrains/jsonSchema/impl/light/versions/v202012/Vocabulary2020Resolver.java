// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.versions.v202012;

import com.jetbrains.jsonSchema.impl.light.StandardJsonSchemaVocabulary;
import com.jetbrains.jsonSchema.impl.light.VocabularySchemaReferenceResolver;

import java.util.Arrays;

public class Vocabulary2020Resolver extends VocabularySchemaReferenceResolver {
  public static final Vocabulary2020Resolver INSTANCE = new Vocabulary2020Resolver();

  private Vocabulary2020Resolver() {
    super(Arrays.asList(
      new StandardJsonSchemaVocabulary.Bundled(
        "https://json-schema.org/draft/2020-12/vocab/core",
        "https://json-schema.org/draft/2020-12/meta/core",
        "jsonSchema/vocabularies/2020-12/core.json"
      ),
      new StandardJsonSchemaVocabulary.Bundled(
        "https://json-schema.org/draft/2020-12/vocab/applicator",
        "https://json-schema.org/draft/2020-12/meta/applicator",
        "jsonSchema/vocabularies/2020-12/applicator.json"
      ),
      new StandardJsonSchemaVocabulary.Bundled(
        "https://json-schema.org/draft/2020-12/vocab/unevaluated",
        "https://json-schema.org/draft/2020-12/meta/unevaluated",
        "jsonSchema/vocabularies/2020-12/unevaluated.json"
      ),
      new StandardJsonSchemaVocabulary.Bundled(
        "https://json-schema.org/draft/2020-12/vocab/validation",
        "https://json-schema.org/draft/2020-12/meta/validation",
        "jsonSchema/vocabularies/2020-12/validation.json"
      ),
      new StandardJsonSchemaVocabulary.Bundled(
        "https://json-schema.org/draft/2020-12/vocab/meta-data",
        "https://json-schema.org/draft/2020-12/meta/meta-data",
        "jsonSchema/vocabularies/2020-12/meta-data.json"
      ),
      new StandardJsonSchemaVocabulary.Bundled(
        "https://json-schema.org/draft/2020-12/vocab/format-annotation",
        "https://json-schema.org/draft/2020-12/meta/format-annotation",
        "jsonSchema/vocabularies/2020-12/format-annotation.json"
      ),
      new StandardJsonSchemaVocabulary.Bundled(
        "https://json-schema.org/draft/2020-12/vocab/content",
        "https://json-schema.org/draft/2020-12/meta/content",
        "jsonSchema/vocabularies/2020-12/content.json"
      )
    ));
  }
}
