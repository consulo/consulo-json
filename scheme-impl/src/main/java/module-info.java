import org.jspecify.annotations.NullMarked;

/**
 * @author VISTALL
 * @since 2026-10-26
 */
@NullMarked
module consulo.json.scheme.impl {
    requires consulo.json.scheme.api;
    requires consulo.ide.api;
    requires consulo.application.api;
    requires consulo.base.icon.library;
    requires consulo.code.editor.api;
    requires consulo.configurable.api;
    requires consulo.execution.api;
    requires consulo.file.chooser.api;
    requires consulo.file.editor.api;
    requires consulo.http.api;
    requires consulo.index.io;
    requires consulo.language.code.style.api;
    requires consulo.language.editor.refactoring.api;
    requires consulo.language.impl;
    requires consulo.module.content.api;
    requires consulo.project.ui.api;
    requires consulo.ui.api;
    requires consulo.ui.ex.api;
    requires consulo.util.xml.serializer;

    requires tools.jackson.dataformat.yaml;
    requires com.google.common;

    exports com.jetbrains.jsonSchema.impl;
    exports com.jetbrains.jsonSchema.impl.fixes;
    exports com.jetbrains.jsonSchema.impl.inspections;
    exports com.jetbrains.jsonSchema.impl.remote;
    exports com.jetbrains.jsonSchema.settings.mappings;
    exports com.jetbrains.jsonSchema.widget;

    opens com.jetbrains.jsonSchema.impl.inspections to consulo.util.xml.serializer;
}
