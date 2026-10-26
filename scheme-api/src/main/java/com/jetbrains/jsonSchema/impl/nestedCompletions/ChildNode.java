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
package com.jetbrains.jsonSchema.impl.nestedCompletions;

import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

public sealed interface ChildNode permits ChildNode.NamedChildNode, ChildNode.Isolated, ChildNode.OpenNode {
    NestedCompletionsNode getNode();

    sealed interface NamedChildNode extends ChildNode permits Isolated.NamedNode, OpenNode {
        String getName();
    }

    abstract sealed class Isolated implements ChildNode permits Isolated.RegexNode, Isolated.NamedNode {
        public static final class RegexNode extends Isolated {
            private final Pattern myRegex;
            private final NestedCompletionsNode myNode;

            public RegexNode(Pattern regex, NestedCompletionsNode node) {
                myRegex = regex;
                myNode = node;
            }

            public Pattern getRegex() {
                return myRegex;
            }

            @Override
            public NestedCompletionsNode getNode() {
                return myNode;
            }

            @Override
            public boolean equals(@Nullable Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                RegexNode regexNode = (RegexNode) o;
                return myRegex.pattern().equals(regexNode.myRegex.pattern()) && myNode.equals(regexNode.myNode);
            }

            @Override
            public int hashCode() {
                int result = myRegex.pattern().hashCode();
                result = 31 * result + myNode.hashCode();
                return result;
            }
        }

        public static final class NamedNode extends Isolated implements NamedChildNode {
            private final String myName;
            private final NestedCompletionsNode myNode;

            public NamedNode(String name, NestedCompletionsNode node) {
                myName = name;
                myNode = node;
            }

            @Override
            public String getName() {
                return myName;
            }

            @Override
            public NestedCompletionsNode getNode() {
                return myNode;
            }

            @Override
            public boolean equals(@Nullable Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                NamedNode namedNode = (NamedNode) o;
                return myName.equals(namedNode.myName) && myNode.equals(namedNode.myNode);
            }

            @Override
            public int hashCode() {
                int result = myName.hashCode();
                result = 31 * result + myNode.hashCode();
                return result;
            }
        }
    }

    final class OpenNode implements ChildNode, NamedChildNode {
        private final String myName;
        private final NestedCompletionsNode myNode;

        public OpenNode(String name, NestedCompletionsNode node) {
            myName = name;
            myNode = node;
        }

        @Override
        public String getName() {
            return myName;
        }

        @Override
        public NestedCompletionsNode getNode() {
            return myNode;
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            OpenNode openNode = (OpenNode) o;
            return myName.equals(openNode.myName) && myNode.equals(openNode.myNode);
        }

        @Override
        public int hashCode() {
            int result = myName.hashCode();
            result = 31 * result + myNode.hashCode();
            return result;
        }
    }
}
