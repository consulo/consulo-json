// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

import com.jetbrains.jsonSchema.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import org.jspecify.annotations.Nullable;

public interface JsonSchemaValidation {
    /**
     * Validates given property adapter against given JSON-schema node considering the validation options. Results are recorded by the provided consumer instance.
     *
     * @return FALSE if the inspected propValue has errors, TRUE if the propValue is valid.
     * The implementations might consider returning the value as soon as the first error is found, or continue processing all the possible errors.
     * This behaviour is controlled by the {@link JsonComplianceCheckerOptions#shouldStopValidationAfterAnyErrorFound()} method.
     */
    boolean validate(JsonValueAdapter propValue,
                                      JsonSchemaObject schema,
                                      @Nullable JsonSchemaType schemaType,
                                      JsonValidationHost consumer,
                                      JsonComplianceCheckerOptions options);
}
