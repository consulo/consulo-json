/*
 * Copyright 2013-2015 must-be.org
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

package consulo.json.jom.validation.psi.reference;

import com.intellij.json.JsonLanguage;
import com.intellij.json.psi.JsonProperty;
import consulo.annotation.component.ExtensionImpl;
import consulo.json.jom.validation.descriptor.JsonPropertyDescriptor;
import consulo.json.jom.validation.inspections.PropertyValidationInspection;
import consulo.language.Language;
import consulo.language.pattern.StandardPatterns;
import consulo.language.psi.*;
import consulo.language.util.ProcessingContext;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 02.12.2015
 */
@ExtensionImpl
public class JsonPropertyNameReferenceProvider extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(@Nonnull PsiReferenceRegistrar psiReferenceRegistrar) {
        psiReferenceRegistrar.registerReferenceProvider(StandardPatterns.psiElement(JsonProperty.class), new PsiReferenceProvider() {
            @Nonnull
            @Override
            public PsiReference[] getReferencesByElement(@Nonnull PsiElement psiElement, @Nonnull ProcessingContext processingContext) {
                JsonPropertyDescriptor propertyDescriptor = PropertyValidationInspection.findPropertyDescriptor((JsonProperty) psiElement);
                if (propertyDescriptor == null) {
                    return PsiReference.EMPTY_ARRAY;
                }

                return new PsiReference[] {new JomJsonPropertyNameReference((JsonProperty) psiElement, propertyDescriptor)};
            }
        });
    }

    @Nonnull
    @Override
    public Language getLanguage() {
        return JsonLanguage.INSTANCE;
    }
}
