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
package com.jetbrains.jsonSchema;

public final class JsonComplianceCheckerOptions {
    public static final JsonComplianceCheckerOptions RELAX_ENUM_CHECK = new JsonComplianceCheckerOptions(true, false);

    private final boolean isCaseInsensitiveEnumCheck;
    private final boolean isForceStrict;

    private final boolean isReportMissingOptionalProperties;
    private final JsonAnnotationsCollectionMode errorsCollectionMode;

    public JsonComplianceCheckerOptions(boolean caseInsensitiveEnumCheck) {
        this(caseInsensitiveEnumCheck, false);
    }

    private JsonComplianceCheckerOptions(boolean caseInsensitiveEnumCheck, boolean forceStrict) {
        this(caseInsensitiveEnumCheck, forceStrict, false);
    }

    public JsonComplianceCheckerOptions(boolean isCaseInsensitiveEnumCheck,
                                        boolean isForceStrict,
                                        boolean isReportMissingOptionalProperties) {
        this(isCaseInsensitiveEnumCheck, isForceStrict, isReportMissingOptionalProperties, JsonAnnotationsCollectionMode.FIND_ALL);
    }

    public JsonComplianceCheckerOptions(boolean isCaseInsensitiveEnumCheck,
                                        boolean isForceStrict,
                                        boolean isReportMissingOptionalProperties,
                                        JsonAnnotationsCollectionMode errorsCollectionMode) {
        this.errorsCollectionMode = errorsCollectionMode;
        this.isCaseInsensitiveEnumCheck = isCaseInsensitiveEnumCheck;
        this.isForceStrict = isForceStrict;
        this.isReportMissingOptionalProperties = isReportMissingOptionalProperties;
    }

    public JsonComplianceCheckerOptions withForcedStrict() {
        return new JsonComplianceCheckerOptions(isCaseInsensitiveEnumCheck, true);
    }

    public boolean isCaseInsensitiveEnumCheck() {
        return isCaseInsensitiveEnumCheck;
    }

    public boolean isForceStrict() {
        return isForceStrict;
    }

    public boolean isReportMissingOptionalProperties() {
        return isReportMissingOptionalProperties;
    }

    public boolean shouldStopValidationAfterAnyErrorFound() {
        return JsonAnnotationsCollectionMode.FIND_FIRST.equals(errorsCollectionMode);
    }
}
