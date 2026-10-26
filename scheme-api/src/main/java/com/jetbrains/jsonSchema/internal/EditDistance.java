// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public final class EditDistance {
    private EditDistance() {
    }

    public static int levenshtein(CharSequence str1, CharSequence str2, boolean caseSensitive) {
        // Wagner-Fischer implementation of Levenshtein distance
        // (http://en.wikipedia.org/wiki/Wagner-Fischer_algorithm)
        int[][] d = prepare(str1.length(), str2.length());
        for (int i = 1; i <= str1.length(); i++) {
            for (int j = 1; j <= str2.length(); j++) {
                int cost = equal(str1.charAt(i - 1), str2.charAt(j - 1), caseSensitive) ? 0 : 1;
                d[i][j] = min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost);
            }
        }
        return d[str1.length()][str2.length()];
    }

    public static int optimalAlignment(CharSequence str1, CharSequence str2, boolean caseSensitive) {
        return optimalAlignment(str1, str2, caseSensitive, Integer.MAX_VALUE);
    }

    /**
     * Extension of the Levenshtein distance with additional case of adjacent transpositions using the
     * (<a href="https://en.wikipedia.org/wiki/Damerau-Levenshtein_distance#Optimal_string_alignment_distance">Wagner-Fischer algorithm</a>)
     * Uses 3(n+1) memory instead of n*m, where n & m are the lengths of the two strings.
     *
     * @param str1          first string to compare
     * @param str2          second string to compare
     * @param caseSensitive specify true to compare case sensitively, false to ignore case
     * @param limit         when the distance becomes greater than the limit, further processing stops.
     *                      To save cpu cycles on strings that are too different.
     * @return the number of edits (number of char insertions+deletions+replacements+swaps) difference between the two strings.
     */
    public static int optimalAlignment(CharSequence str1, CharSequence str2, boolean caseSensitive, int limit) {
        return optimalAlignment(str1, str2, caseSensitive, limit, true);
    }

    public static int optimalAlignment(CharSequence first,
                                       CharSequence second,
                                       boolean caseSensitive,
                                       int limit,
                                       boolean transpositionsAllowed) {
        CharSequence str1 = first;
        CharSequence str2 = second;
        if (str1.length() > str2.length()) {
            CharSequence tmp = str1;
            str1 = str2;
            str2 = tmp;
        }
        int length1 = str1.length();
        int length2 = str2.length();
        if (length1 == 0) {
            return length2;
        }
        int[] v0 = new int[length1 + 1];
        int[] v1 = new int[length1 + 1];
        int[] v2 = new int[length1 + 1];// three rows of length n + 1 instead of n*m two dimensional array
        for (int i = 1; i <= length1; i++) {
            v1[i] = i;
        }
        int minCost = limit + 1; // flip to negative on MAX_INT doesn't matter
        for (int j = 0; j < length2; j++) {
            v2[0] = j + 1;

            for (int i = 0; i < length1; i++) {
                int cost = equal(str1.charAt(i), str2.charAt(j), caseSensitive) ? 0 : 1;
                v2[i + 1] = min(v2[i] + 1, // insertion
                    v1[i + 1] + 1, // deletion
                    v1[i] + cost); // substitution (replacement)

                if (transpositionsAllowed && i > 0 && j > 0 &&
                    equal(str2.charAt(j), str1.charAt(i - 1), caseSensitive) && equal(str1.charAt(i), str2.charAt(j - 1), caseSensitive)) {
                    // transposition (swap)
                    v2[i + 1] = Math.min(v2[i + 1], v0[i - 1] + cost);
                }
                int currentCost = v2[i + 1];
                if (currentCost < minCost) {
                    minCost = currentCost;
                }
            }
            if (minCost > limit) {
                return minCost;
            }
            minCost = limit + 1;
            int[] temp = v0;
            v0 = v1;
            v1 = v2;
            v2 = temp; // will be overwritten/reused
        }

        // our last action in the loop above was to switch arrays,
        // so v1 has the most recent cost counts
        return v1[length1];
    }

    private static int[][] prepare(int length1, int length2) {
        int[][] d = new int[length1 + 1][length2 + 1];
        for (int i = 0; i <= length1; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= length2; j++) {
            d[0][j] = j;
        }
        return d;
    }

    private static boolean equal(char c1, char c2, boolean caseSensitive) {
        return caseSensitive ? c1 == c2 : Character.toLowerCase(c1) == Character.toLowerCase(c2);
    }

    private static int min(int a, int b, int c) {
        return Math.min(Math.min(a, b), c);
    }
}
