// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.jsonSchema.impl;

import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonStringLiteralTextFragment;
import consulo.language.Language;
import consulo.language.inject.MultiHostInjector;
import consulo.language.inject.MultiHostRegistrar;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

public abstract class JsonSchemaInjectorBase implements MultiHostInjector {
    public static final class InjectedLanguageData {
        InjectedLanguageData(Language language, @Nullable String prefix, @Nullable String postfix) {
            this.language = language;
            this.prefix = prefix;
            this.postfix = postfix;
        }

        public Language language;
        public @Nullable String prefix;
        public @Nullable String postfix;

        public String getInjectedLanguageId() {
            return language.getID();
        }

        public Language getInjectedLanguage() {
            return language;
        }

        public String getPrefix() {
            return StringUtil.notNullize(prefix);
        }

        public String getSuffix() {
            return StringUtil.notNullize(postfix);
        }

        public @Nullable String getSupportId() {
            return null;
        }
    }

    protected static void injectForHost(
        MultiHostRegistrar registrar,
        JsonStringLiteral host,
        @SuppressWarnings("SameParameterValue") Language language
    ) {
        injectForHost(registrar, host, new InjectedLanguageData(language, null, null));
    }

    protected static void injectForHost(MultiHostRegistrar registrar, JsonStringLiteral host, InjectedLanguageData language) {
        List<JsonStringLiteralTextFragment> fragments = host.getTextFragments();
        if (fragments.isEmpty()) {
            return;
        }
        registrar.startInjecting(language.language);
        for (JsonStringLiteralTextFragment fragment : fragments) {
            registrar.addPlace(language.prefix, language.postfix, (PsiLanguageInjectionHost) host, fragment.textRange());
        }
        registrar.doneInjecting();
    }

    @Override
    public Class<? extends PsiElement> getElementClass() {
        return JsonStringLiteral.class;
    }
}
