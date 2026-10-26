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

import com.jetbrains.jsonSchema.extension.JsonErrorPriority;
import consulo.json.localize.JsonLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public final class JsonValidationError {

    public @Nullable IssueData getIssueData() {
        return myIssueData;
    }

    public JsonErrorPriority getPriority() {
        return myPriority;
    }

    public enum FixableIssueKind {
        MissingProperty,
        MissingOptionalProperty,
        MissingOneOfProperty,
        MissingAnyOfProperty,
        ProhibitedProperty,
        NonEnumValue,
        ProhibitedType,
        TypeMismatch,
        DuplicateArrayItem,
        None
    }

    public interface IssueData {

    }

    public static final class DuplicateArrayItemIssueData implements IssueData {
        public final int[] duplicateIndices;

        public DuplicateArrayItemIssueData(int[] indices) {
            duplicateIndices = indices;
        }
    }

    public static final class MissingOneOfPropsIssueData implements IssueData {
        public final Collection<MissingMultiplePropsIssueData> myExclusiveOptions;

        public MissingOneOfPropsIssueData(Collection<MissingMultiplePropsIssueData> options) {
            myExclusiveOptions = options;
        }
    }

    public static final class MissingMultiplePropsIssueData implements IssueData {
        public final Collection<MissingPropertyIssueData> myMissingPropertyIssues;

        public MissingMultiplePropsIssueData(Collection<MissingPropertyIssueData> missingPropertyIssues) {
            myMissingPropertyIssues = missingPropertyIssues;
        }

        private static String getPropertyNameWithComment(MissingPropertyIssueData prop) {
            String comment = "";
            if (prop.enumItemsCount == 1) {
                Object defaultValue = prop.defaultValue;
                assert defaultValue != null;
                comment = " = " + defaultValue.toString();
            }
            return "'" + prop.propertyName + "'" + comment;
        }

        public String getMessage(boolean trimIfNeeded) {
            if (myMissingPropertyIssues.size() == 1) {
                MissingPropertyIssueData prop = myMissingPropertyIssues.iterator().next();
                return JsonLocalize.schemaValidationProperty(getPropertyNameWithComment(prop)).get();
            }

            Collection<MissingPropertyIssueData> namesToDisplay = myMissingPropertyIssues;
            boolean trimmed = false;
            if (trimIfNeeded && namesToDisplay.size() > 3) {
                namesToDisplay = new ArrayList<>();
                Iterator<MissingPropertyIssueData> iterator = myMissingPropertyIssues.iterator();
                for (int i = 0; i < 3; i++) {
                    namesToDisplay.add(iterator.next());
                }
                trimmed = true;
            }
            String allNames = myMissingPropertyIssues.stream().map(
                MissingMultiplePropsIssueData::getPropertyNameWithComment).sorted((s1, s2) -> {
                boolean firstHasEq = s1.contains("=");
                boolean secondHasEq = s2.contains("=");
                if (firstHasEq == secondHasEq) {
                    return s1.compareTo(s2);
                }
                return firstHasEq ? -1 : 1;
            }).collect(Collectors.joining(", "));
            if (trimmed) {
                allNames += ", ...";
            }
            return JsonLocalize.schemaValidationProperties(allNames).get();
        }
    }

    public static final class MissingPropertyIssueData implements IssueData {
        public final String propertyName;
        public final @Nullable JsonSchemaType propertyType;
        public final @Nullable Object defaultValue;
        public final int enumItemsCount;

        public MissingPropertyIssueData(String propertyName, @Nullable JsonSchemaType propertyType, @Nullable Object defaultValue, int enumItemsCount) {
            this.propertyName = propertyName;
            this.propertyType = propertyType;
            this.defaultValue = defaultValue;
            this.enumItemsCount = enumItemsCount;
        }
    }

    public static final class ProhibitedPropertyIssueData implements IssueData {
        public final String propertyName;
        public final List<String> typoCandidates;

        public ProhibitedPropertyIssueData(String propertyName, List<String> typoCandidates) {
            this.propertyName = propertyName;
            this.typoCandidates = typoCandidates;
        }
    }

    public static final class TypeMismatchIssueData implements IssueData {
        public final JsonSchemaType[] expectedTypes;

        public TypeMismatchIssueData(JsonSchemaType[] expectedTypes) {
            this.expectedTypes = expectedTypes;
        }
    }

    private final String myMessage;
    private final FixableIssueKind myFixableIssueKind;
    private final @Nullable IssueData myIssueData;
    private final JsonErrorPriority myPriority;

    public JsonValidationError(String message, FixableIssueKind fixableIssueKind, @Nullable IssueData issueData,
                               JsonErrorPriority priority) {
        myMessage = message;
        myFixableIssueKind = fixableIssueKind;
        myIssueData = issueData;
        myPriority = priority;
    }

    public String getMessage() {
        return myMessage;
    }

    public FixableIssueKind getFixableIssueKind() {
        return myFixableIssueKind;
    }

}
