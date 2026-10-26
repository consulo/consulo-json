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
package com.jetbrains.jsonSchema.extension;

import com.jetbrains.jsonSchema.impl.nestedCompletions.NestedCompletionsNode;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.Application;
import consulo.language.psi.PsiFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Extension point used for extending completion provided by {@link com.jetbrains.jsonSchema.impl.JsonSchemaCompletionContributor}.
 * Provided instance of {@link NestedCompletionsNode} will be converted into a completion item with a several level json/yaml tree to insert.
 * See the {@link NestedCompletionsNode}'s documentation for a more detailed description of how exactly the completion item's text will be constructed.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface JsonSchemaNestedCompletionsTreeProvider {
    static @Nullable NestedCompletionsNode getNestedCompletionsData(PsiFile editedFile) {
        List<NestedCompletionsNode> nodes = new ArrayList<>();
        Application.get().getExtensionPoint(JsonSchemaNestedCompletionsTreeProvider.class).forEach(extension -> {
            NestedCompletionsNode node = extension.getNestedCompletionsRoot(editedFile);
            if (node != null) {
                nodes.add(node);
            }
        });
        return nodes.stream().reduce(NestedCompletionsNode::merge).orElse(null);
    }

    /**
     * @return null if you do not want to alter the json schema-based completion for this file
     */
    @Nullable
    NestedCompletionsNode getNestedCompletionsRoot(PsiFile editedFile);
}
