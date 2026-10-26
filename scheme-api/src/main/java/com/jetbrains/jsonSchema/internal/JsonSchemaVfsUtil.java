// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.jsonSchema.internal;

import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

public final class JsonSchemaVfsUtil {
    private JsonSchemaVfsUtil() {
    }

    public static @Nullable String findRelativePath(VirtualFile source, VirtualFile dst, char separatorChar) {
        if (!source.getFileSystem().equals(dst.getFileSystem())) {
            return null;
        }

        VirtualFile src = source;
        if (!src.isDirectory()) {
            src = src.getParent();
            if (src == null) {
                return null;
            }
        }

        VirtualFile commonAncestor = VirtualFileUtil.getCommonAncestor(src, dst);
        if (commonAncestor == null) {
            return null;
        }

        StringBuilder buffer = new StringBuilder();

        if (!Comparing.equal(src, commonAncestor)) {
            while (src != null && !Comparing.equal(src, commonAncestor)) {
                buffer.append("..").append(separatorChar);
                src = src.getParent();
            }
        }

        buffer.append(VirtualFileUtil.getRelativePath(dst, commonAncestor, separatorChar));

        if (StringUtil.endsWithChar(buffer, separatorChar)) {
            buffer.setLength(buffer.length() - 1);
        }

        return buffer.toString();
    }
}
