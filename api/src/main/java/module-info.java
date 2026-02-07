/**
 * @author VISTALL
 * @since 2026-02-07
 */
module consulo.json.api {
    requires transitive consulo.ide.api;
    
    exports com.intellij.json;
    exports com.intellij.json.codeinsight;
    exports com.intellij.json.editor;
    exports com.intellij.json.highlighting;
    exports com.intellij.json.json5;
    exports com.intellij.json.json5.codeinsight;
    exports com.intellij.json.json5.highlighting;
    exports com.intellij.json.jsonLines;
    exports com.intellij.json.jsonScheme;
    exports com.intellij.json.pointer;
    exports com.intellij.json.psi;
    exports com.intellij.json.psi.impl;
    exports com.intellij.json.syntax;
    exports com.intellij.json.syntax.json5;
    
    exports consulo.json.icon;
    exports consulo.json.localize;

    exports com.intellij.json.internal to consulo.json;
    exports com.intellij.json.internal.navigation to consulo.json;
}