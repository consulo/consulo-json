package com.intellij.json.psi;

import consulo.document.util.TextRange;

/**
 * @author VISTALL
 * @since 2026-01-23
 */
public record JsonStringLiteralTextFragment(TextRange textRange, String text) {
}
