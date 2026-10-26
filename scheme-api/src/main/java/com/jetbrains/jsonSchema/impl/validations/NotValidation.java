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
package com.jetbrains.jsonSchema.impl.validations;

import com.jetbrains.jsonSchema.JsonAnnotationsCollectionMode;
import com.jetbrains.jsonSchema.JsonComplianceCheckerOptions;
import com.jetbrains.jsonSchema.JsonSchemaObject;
import com.jetbrains.jsonSchema.JsonSchemaType;
import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import com.jetbrains.jsonSchema.extension.JsonSchemaValidation;
import com.jetbrains.jsonSchema.extension.JsonValidationHost;
import com.jetbrains.jsonSchema.extension.adapter.JsonValueAdapter;
import com.jetbrains.jsonSchema.internal.MatchResult;
import consulo.json.localize.JsonLocalize;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

public final class NotValidation implements JsonSchemaValidation {
    public static final NotValidation INSTANCE = new NotValidation();

    @Override
    public boolean validate(JsonValueAdapter propValue,
                            JsonSchemaObject schema,
                            @Nullable JsonSchemaType schemaType,
                            JsonValidationHost consumer,
                            JsonComplianceCheckerOptions options) {
        JsonSchemaObject not = schema.getNot();
        assert not != null;
        MatchResult result = consumer.resolve(not, propValue);
        if (result.mySchemas.isEmpty() && result.myExcludingSchemas.isEmpty()) {
            return true;
        }

        // if 'not' uses reference to owning schema back -> do not check, seems it does not make any sense
        if (result.mySchemas.stream().anyMatch(s -> schema.equals(s)) ||
            result.myExcludingSchemas.stream().flatMap(Collection::stream)
                .anyMatch(s -> schema.equals(s))) {
            return true;
        }

        JsonValidationHost checker =
            consumer.checkByMatchResult(propValue,
                result,
                new JsonComplianceCheckerOptions(options.isCaseInsensitiveEnumCheck(), true, false,
                    JsonAnnotationsCollectionMode.FIND_FIRST));
        if (checker == null || checker.isValid()) {
            consumer.error(JsonLocalize.schemaValidationAgainstNot().get(), propValue.getDelegate(), JsonErrorPriority.NOT_SCHEMA);
            return false;
        }

        return true;
    }
}
