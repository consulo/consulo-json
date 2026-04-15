/**
 * @author VISTALL
 * @since 2026-02-07
 */
module consulo.json {
    requires consulo.ide.api;
    requires consulo.execution.api;
    requires consulo.language.impl;
    requires com.google.common;
    requires consulo.language.editor.refactoring.api;
    requires consulo.language.spellchecker.api;
    requires consulo.language.code.style.ui.api;
    requires consulo.file.editor.api;
    requires consulo.ui.ex.api;
    requires consulo.json.api;

    opens com.intellij.json.impl.editor to consulo.util.xml.serializer;
    opens com.intellij.json.impl.formatter to consulo.language.code.style.ui.api, consulo.util.xml.serializer;
}