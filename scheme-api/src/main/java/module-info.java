import org.jspecify.annotations.NullMarked;

/**
 * @author VISTALL
 * @since 2026-10-26
 */
@NullMarked
module consulo.json.scheme.api {
    requires transitive consulo.json.api;
    requires transitive consulo.ide.api;
    requires transitive consulo.virtual.file.system.http.api;
    requires consulo.index.io;
    requires consulo.language.impl;
    requires consulo.util.xml.serializer;
    requires consulo.module.content.api;

    requires transitive tools.jackson.core;
    requires transitive tools.jackson.databind;
    requires tools.jackson.dataformat.yaml;
    requires com.google.common;

    exports com.jetbrains.jsonSchema;
    exports com.jetbrains.jsonSchema.extension;
    exports com.jetbrains.jsonSchema.extension.adapter;
    exports com.jetbrains.jsonSchema.impl.light;
    exports com.jetbrains.jsonSchema.impl.nestedCompletions;
    exports com.jetbrains.jsonSchema.impl.light.legacy;
    exports com.jetbrains.jsonSchema.impl.light.nodes;
    exports com.jetbrains.jsonSchema.impl.light.versions;
    exports com.jetbrains.jsonSchema.impl.light.versions.v201909;
    exports com.jetbrains.jsonSchema.impl.light.versions.v202012;
    exports com.jetbrains.jsonSchema.impl.tree;
    exports com.jetbrains.jsonSchema.impl.validations;
    exports com.jetbrains.jsonSchema.remote;
    exports com.jetbrains.jsonSchema.walker;

    exports com.jetbrains.jsonSchema.internal;
    exports com.jetbrains.jsonSchema.internal.adapter;

    opens com.jetbrains.jsonSchema to consulo.util.xml.serializer;
    opens com.jetbrains.jsonSchema.internal to consulo.util.xml.serializer;
}
