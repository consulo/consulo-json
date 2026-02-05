package com.jetbrains.jsonSchema.impl.light;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase;
import jakarta.annotation.Nullable;

public class LocalSchemaReferenceResolver implements JsonSchemaRefResolver {
    public static final LocalSchemaReferenceResolver INSTANCE = new LocalSchemaReferenceResolver();

    @Override
    @Nullable
    public JsonSchemaObject resolve(String reference,
                                    JsonSchemaObjectBackedByJacksonBase referenceOwner,
                                    JsonSchemaService service) {
        return JsonSchemaRefResolverKt.resolveLocalSchemaNode(reference, referenceOwner);
    }
}
