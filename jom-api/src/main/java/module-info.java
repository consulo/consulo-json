/**
 * @author VISTALL
 * @since 2026-02-07
 */
module consulo.json.jom.api {
    requires consulo.json.api;
    exports consulo.json.jom;

    exports consulo.json.jom.validation;
    exports consulo.json.jom.validation.completion;
    exports consulo.json.jom.validation.descriptionByAnotherPsiElement;
    exports consulo.json.jom.validation.descriptor;
    exports consulo.json.jom.validation.inspections;
    exports consulo.json.jom.validation.psi.reference;
}