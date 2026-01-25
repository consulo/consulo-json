package com.jetbrains.jsonSchema.impl.light;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase;
import jakarta.annotation.Nullable;

import java.util.List;

abstract class VocabularySchemaReferenceResolver implements JsonSchemaRefResolver {
    private final List<StandardJsonSchemaVocabulary.Bundled> bundledVocabularies;

    protected VocabularySchemaReferenceResolver(List<StandardJsonSchemaVocabulary.Bundled> bundledVocabularies) {
        this.bundledVocabularies = bundledVocabularies;
    }

    @Override
    @Nullable
    public JsonSchemaObject resolve(String reference,
                                    JsonSchemaObjectBackedByJacksonBase referenceOwner,
                                    JsonSchemaService service) {
        if (reference.startsWith("http") || reference.startsWith("#") || reference.startsWith("/")) {
            return null;
        }
        return JsonSchemaVocabularyResolverKt.resolveVocabulary(reference, referenceOwner, service, bundledVocabularies);
    }
}
