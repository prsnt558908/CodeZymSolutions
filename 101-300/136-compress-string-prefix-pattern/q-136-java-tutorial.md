# Compress String Using a Substring Pattern in Java

#### Problem Statement

[https://codezym.com/question/136-compress-string-prefix-pattern](https://codezym.com/question/136-compress-string-prefix-pattern)


Try every possible substring as the fixed pattern. Write one occurrence as raw letters first, then replace the earliest available matches with `*`. A table of common-prefix lengths makes each match check fast.


Every star must represent the same pattern. The pattern can start anywhere in the string, but it must appear completely as raw letters before the first star.


## Start with a Simple Approach

A direct solution chooses a pattern and tries both choices at each matching position: write the letters or use a star. This can create exponentially many combinations.


We can avoid that branching by always taking the earliest available match after introducing the pattern.


## Build Each Candidate Greedily

Choose a pattern starting at `start` with `length` characters.


First, copy all characters before `start + length` as raw letters. This writes the complete pattern before any star. For example, choosing `ABCD` in `XABCDABCDY` starts the encoding with `XABCD`.


Then scan the remaining characters from left to right.

- If the pattern matches at the current position, append `*` and skip `length` characters.
- Otherwise, append the current letter and move forward by one character.


Only try choices where `start + 2 * length <= n`. A useful candidate needs room for the raw occurrence and at least one later, non-overlapping occurrence.


Keep the shortest candidate. When lengths are equal, keep the lexicographically smallest one. Start with the original string as the answer so that leaving it unchanged remains an option.


Do not skip patterns of length one. They save no space, but they can improve a tie because `*` comes before every uppercase letter. For example, `AA` becomes `A*`.


## Make Each Match Check Fast

Comparing the pattern character by character at every position can take `O(n^4)` time overall.


Instead, let `common[i][j]` store how many consecutive characters match when reading from positions `i` and `j`.

- If the characters differ, the value is zero.
- If they match, the value is `1 + common[i + 1][j + 1]`.


Fill this table from the end of the string. Its extra row and column contain zeros, which handle comparisons that reach the end.


The pattern matches at `index` exactly when `common[start][index] >= length`. Each check now takes `O(1)` time.


The two-dimensional integer array stores these reusable match lengths. `StringBuilder` collects letters and stars without repeatedly rebuilding the candidate string. `compareTo` handles lexicographic ties.


## Walk Through an Example

For `s = "ABCABCEABCABCE"`, choose `p = "ABC"`.

1. Write the first `ABC` as raw letters.
2. Replace the next `ABC` with `*`, then copy `E`.
3. Replace the next two occurrences of `ABC` with two stars, then copy the last `E`.


The result is `ABC*E**E`. Choosing `ABCABCE` gives `ABCABCE*`, which has the same length. We keep `ABC*E**E` because its first differing character is `*` instead of `A`.


## Why This Works

For one chosen raw prefix and pattern, every replacement covers the same number of characters. Taking the earliest match ends no later than taking a later match, so it leaves at least as much room for the remaining replacements. Repeating this choice maximizes the number of stars.


Each star saves `length - 1` characters. Therefore, maximizing stars gives the shortest candidate. If several choices have the same length, using the earliest star makes the result lexicographically smaller because `*` comes before uppercase letters.


In any valid encoding that uses a star, everything before the first star is raw text. It contains a complete occurrence of the chosen pattern. Our loops try that occurrence and its length. The greedy scan produces a candidate at least as good, so comparing all candidates finds the required answer.


## Complexity

Let `n` be the string length.


There are `O(n^2)` choices of pattern position and length. Building and comparing each candidate takes `O(n)` time. The total time is `O(n^3)`, including the `O(n^2)` table construction.


The table uses `O(n^2)` extra space. The candidate and answer strings use `O(n)` space.


## Java Code

```java
public class CompressString {

    public CompressString() {
    }

    public String compressString(String s) {
        int n = s.length();
        int[][] common = buildCommonPrefix(s);
        String best = s;

        for (int start = 0; start < n; start++) {
            // Leave room for one raw occurrence and at least one replacement.
            for (int length = 1; start + 2 * length <= n; length++) {
                StringBuilder encoded = new StringBuilder();

                // Introduce the complete pattern using raw letters.
                encoded.append(s, 0, start + length);
                int index = start + length;

                while (index < n) {
                    if (common[start][index] >= length) {
                        encoded.append('*');
                        index += length;
                    } else {
                        encoded.append(s.charAt(index));
                        index++;
                    }
                }

                String candidate = encoded.toString();
                if (candidate.length() < best.length()
                        || (candidate.length() == best.length()
                            && candidate.compareTo(best) < 0)) {
                    best = candidate;
                }
            }
        }

        return best;
    }

    int[][] buildCommonPrefix(String s) {
        int n = s.length();
        int[][] common = new int[n + 1][n + 1];

        // common[i][j] counts matching characters from positions i and j.
        for (int i = n - 1; i >= 0; i--) {
            for (int j = n - 1; j >= 0; j--) {
                if (s.charAt(i) == s.charAt(j)) {
                    common[i][j] = 1 + common[i + 1][j + 1];
                }
            }
        }

        return common;
    }
}
```
