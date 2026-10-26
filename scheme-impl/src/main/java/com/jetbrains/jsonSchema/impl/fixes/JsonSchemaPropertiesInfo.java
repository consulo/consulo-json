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
package com.jetbrains.jsonSchema.impl.fixes;

import com.jetbrains.jsonSchema.JsonValidationError.MissingMultiplePropsIssueData;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class JsonSchemaPropertiesInfo {
    private final MissingMultiplePropsIssueData myMissingRequiredProperties;
    private final MissingMultiplePropsIssueData myMissingKnownProperties;

    public JsonSchemaPropertiesInfo(MissingMultiplePropsIssueData missingRequiredProperties,
                                    MissingMultiplePropsIssueData missingKnownProperties) {
        myMissingRequiredProperties = missingRequiredProperties;
        myMissingKnownProperties = missingKnownProperties;
    }

    public MissingMultiplePropsIssueData getMissingRequiredProperties() {
        return myMissingRequiredProperties;
    }

    public MissingMultiplePropsIssueData getMissingKnownProperties() {
        return myMissingKnownProperties;
    }

    public boolean hasOnlyRequiredPropertiesMissing() {
        return myMissingRequiredProperties.myMissingPropertyIssues.size() == myMissingKnownProperties.myMissingPropertyIssues.size();
    }

    public boolean hasNoRequiredPropertiesMissing() {
        return myMissingRequiredProperties.myMissingPropertyIssues.isEmpty();
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JsonSchemaPropertiesInfo that = (JsonSchemaPropertiesInfo) o;
        return myMissingRequiredProperties.equals(that.myMissingRequiredProperties)
            && myMissingKnownProperties.equals(that.myMissingKnownProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myMissingRequiredProperties, myMissingKnownProperties);
    }

    @Override
    public String toString() {
        return "JsonSchemaPropertiesInfo(missingRequiredProperties=" + myMissingRequiredProperties +
            ", missingKnownProperties=" + myMissingKnownProperties + ")";
    }
}
