/**
 * @author VISTALL
 * @since 2026-02-07
 */
module consulo.json {
    requires consulo.ide.api;
    requires consulo.json.api;

    opens com.intellij.json.impl.editor to consulo.util.xml.serializer;
    opens com.intellij.json.impl.formatter to consulo.language.code.style.ui.api;
}