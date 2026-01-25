package com.jetbrains.jsonSchema.impl.light;

import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaService;
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase;
import com.jetbrains.jsonSchema.impl.JsonSchemaObjectReadingUtils;
import com.jetbrains.jsonSchema.remote.JsonFileResolver;
import consulo.application.ApplicationManager;
import consulo.application.util.registry.Registry;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.http.HttpVirtualFile;
import jakarta.annotation.Nullable;

public class RemoteSchemaReferenceResolver implements JsonSchemaRefResolver {
    public static final RemoteSchemaReferenceResolver INSTANCE = new RemoteSchemaReferenceResolver();

    @Override
    @Nullable
    public JsonSchemaObject resolve(String reference,
                                    JsonSchemaObjectBackedByJacksonBase referenceOwner,
                                    JsonSchemaService service) {
        // leave tests with default behaviour to not accidentally miss even more bugs
        if (!ApplicationManager.getApplication().isUnitTestMode() &&
            !Registry.is("json.schema.object.v2.enable.nested.remote.schema.resolve")) {
            return null;
        }

        return resolveRemoteSchemaByUrl(reference, referenceOwner, service);
    }

    @Nullable
    private static JsonSchemaObject resolveRemoteSchemaByUrl(String reference, JsonSchemaObject schemaNode, JsonSchemaService service) {
        JsonSchemaObject value = JsonSchemaObjectReadingUtils.fetchSchemaFromRefDefinition(reference, schemaNode, service, schemaNode.isRefRecursive());
        if (!JsonFileResolver.isHttpPath(reference)) {
            service.registerReference(reference);
        }
        else if (value != null) {
            // our aliases - if http ref actually refers to a local file with specific ID
            VirtualFile virtualFile = service.resolveSchemaFile(value);
            if (virtualFile != null && !(virtualFile instanceof HttpVirtualFile)) {
                service.registerReference(virtualFile.getName());
            }
        }
        return value;
    }
}
