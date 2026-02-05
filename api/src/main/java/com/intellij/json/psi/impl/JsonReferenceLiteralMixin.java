// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.language.psi.ContributedReferenceHost;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ReferenceProvidersRegistry;
import jakarta.annotation.Nonnull;

public class JsonReferenceLiteralMixin extends JsonValueImpl implements ContributedReferenceHost {

    public JsonReferenceLiteralMixin(@Nonnull ASTNode node) {
        super(node);
    }

    @Override
    @Nonnull
    public PsiReference[] getReferences() {
        return ReferenceProvidersRegistry.getReferencesFromProviders(this);
    }
}
