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
package com.jetbrains.jsonSchema.settings.mappings;

import com.jetbrains.jsonSchema.UserDefinedJsonSchemaConfiguration;
import com.jetbrains.jsonSchema.internal.JsonFileResolver;
import consulo.project.Project;
import consulo.util.io.FileUtil;
import consulo.util.lang.BeforeAfter;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ThreeState;
import org.jspecify.annotations.Nullable;

import java.io.File;

public final class JsonSchemaPatternComparator {
    private final Project myProject;

    public JsonSchemaPatternComparator(Project project) {
        myProject = project;
    }

    public ThreeState isSimilar(UserDefinedJsonSchemaConfiguration.Item itemLeft,
                                UserDefinedJsonSchemaConfiguration.Item itemRight) {
        if (itemLeft.isPattern() != itemRight.isPattern()) {
            return ThreeState.NO;
        }
        if (itemLeft.isPattern()) {
            return comparePatterns(itemLeft, itemRight);
        }
        return comparePaths(itemLeft, itemRight);
    }

    private ThreeState comparePaths(UserDefinedJsonSchemaConfiguration.Item left, UserDefinedJsonSchemaConfiguration.Item right) {
        String leftPath = left.getPath();
        String rightPath = right.getPath();

        if (JsonFileResolver.isTempOrMockUrl(leftPath) || JsonFileResolver.isTempOrMockUrl(rightPath)) {
            return leftPath.equals(rightPath) ? ThreeState.YES : ThreeState.NO;
        }
        File leftFile = new File(myProject.getBasePath(), leftPath);
        File rightFile = new File(myProject.getBasePath(), rightPath);

        if (left.isDirectory()) {
            if (FileUtil.isAncestor(leftFile, rightFile, true)) {
                return ThreeState.YES;
            }
        }
        if (right.isDirectory()) {
            if (FileUtil.isAncestor(rightFile, leftFile, true)) {
                return ThreeState.YES;
            }
        }
        return FileUtil.filesEqual(leftFile, rightFile) && left.isDirectory() == right.isDirectory() ? ThreeState.YES : ThreeState.NO;
    }

    private static ThreeState comparePatterns(UserDefinedJsonSchemaConfiguration.Item leftItem,
                                              UserDefinedJsonSchemaConfiguration.Item rightItem) {
        if (leftItem.getPath().equals(rightItem.getPath())) {
            return ThreeState.YES;
        }
        if (leftItem.getPath().indexOf(File.separatorChar) >= 0 || rightItem.getPath().indexOf(File.separatorChar) >= 0) {
            // todo: better heuristic
            return ThreeState.NO;
        }
        BeforeAfter<String> left = getBeforeAfterAroundWildCards(leftItem.getPath());
        BeforeAfter<String> right = getBeforeAfterAroundWildCards(rightItem.getPath());
        if (left == null || right == null) {
            if (left == null && right == null) {
                return leftItem.getPath().equals(rightItem.getPath()) ? ThreeState.YES : ThreeState.NO;
            }
            if (left == null) {
                assert right != null;
                return checkOneSideWithoutWildcard(leftItem, right);
            }
            return checkOneSideWithoutWildcard(rightItem, left);
        }
        if (!StringUtil.isEmptyOrSpaces(left.getBefore()) && !StringUtil.isEmptyOrSpaces(right.getBefore())) {
            if (left.getBefore().startsWith(right.getBefore()) || right.getBefore().startsWith(left.getBefore())) {
                return ThreeState.YES;
            }
            // otherwise they are different
            return ThreeState.NO;
        }
        if (!StringUtil.isEmptyOrSpaces(left.getAfter()) && !StringUtil.isEmptyOrSpaces(right.getAfter())) {
            if (left.getAfter().endsWith(right.getAfter()) || right.getAfter().endsWith(left.getAfter())) {
                return ThreeState.YES;
            }
            // otherwise they are different
            return ThreeState.NO;
        }
        return ThreeState.UNSURE;
    }

    private static ThreeState checkOneSideWithoutWildcard(UserDefinedJsonSchemaConfiguration.Item item, BeforeAfter<String> beforeAfter) {
        if (!StringUtil.isEmptyOrSpaces(beforeAfter.getBefore()) && item.getPath().startsWith(beforeAfter.getBefore())) {
            return ThreeState.YES;
        }
        if (!StringUtil.isEmptyOrSpaces(beforeAfter.getAfter()) && item.getPath().endsWith(beforeAfter.getAfter())) {
            return ThreeState.YES;
        }
        return ThreeState.UNSURE;
    }

    private static @Nullable BeforeAfter<String> getBeforeAfterAroundWildCards(String pattern) {
        int firstIdx = pattern.indexOf('*');
        int lastIdx = pattern.lastIndexOf('*');
        if (firstIdx < 0 || lastIdx < 0) {
            return null;
        }
        return new BeforeAfter<>(pattern.substring(0, firstIdx), pattern.substring(lastIdx + 1));
    }
}
