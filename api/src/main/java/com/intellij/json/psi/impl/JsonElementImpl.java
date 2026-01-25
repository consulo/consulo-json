package com.intellij.json.psi.impl;

import com.intellij.json.psi.JsonElement;
import consulo.language.ast.ASTNode;
import consulo.language.impl.psi.ASTWrapperPsiElement;
import consulo.util.lang.StringUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author Mikhail Golubev
 */
public class JsonElementImpl extends ASTWrapperPsiElement implements JsonElement {

  public JsonElementImpl(@NotNull ASTNode node) {
    super(node);
  }

  @Override
  public String toString() {
    final String className = getClass().getSimpleName();
    return StringUtil.trimEnd(className, "Impl");
  }
}
