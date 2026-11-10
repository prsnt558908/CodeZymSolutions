# Longest Shared Address Sequence in Java

#### Problem Statement
[https://codezym.com/question/440-longest-shared-address-sequence](https://codezym.com/question/440-longest-shared-address-sequence)

---

This is the classic **Longest Common Subsequence (LCS)** problem, just with addresses instead of plain words.

The core idea is simple: compare the two addresses one character at a time.

When two characters match, keep them. When they do not match, drop a character from one of the addresses and keep whichever choice gives the longer answer.

Trying every choice with plain recursion is very slow, because the same small questions come up again and again. So we save the answer to each small question in a 2D table and build the final answer from those saved answers. This trick is called **dynamic programming**, and it runs in `O(m * n)` time.

At the end, we will see that only two rows of that table are needed at any time. This cuts the memory down to `O(n)`.

---

## Understanding the Problem

We can skip characters, but we cannot change the order of the ones we keep. The kept characters also do not need to be next to each other.

Take Example 2:

```
firstAddress  : 4 5 n o r t h r o a d
                ^ ^   ^   ^ ^ ^ ^ ^ ^
secondAddress : 4 5 s o u t h r o a d
                ^ ^   ^   ^ ^ ^ ^ ^ ^
```

The marked characters spell `"45othroad"` in both addresses. That is 9 characters, so the answer is `9`.

---

## Brute Force: Plain Recursion

We will work with the first `i` characters of `firstAddress` and the first `j` characters of `secondAddress`. Let's call these the two **parts**.

Let `solve(i, j)` be the answer for these two parts. The last character of each part is at index `i - 1` and `j - 1`.

Now look at the last character of both parts:

1. If one part is empty, nothing is shared. Return `0`.
2. If the last characters match, keep them. Return `1 + solve(i - 1, j - 1)`.
3. If they do not match, at least one of them cannot be in the answer. So try dropping each one and return the bigger result, `max(solve(i - 1, j), solve(i, j - 1))`.

When the last characters match, keeping them never makes the answer worse. That is why step 2 does not try dropping them.

```java
public int findLongestSharedSequence(String firstAddress, String secondAddress) {
    return solve(firstAddress, secondAddress, firstAddress.length(), secondAddress.length());
}

// Answer for the first i chars of a and the first j chars of b
int solve(String a, String b, int i, int j) {
    if (i == 0 || j == 0) {
        return 0; // one part is empty, nothing is shared
    }
    if (a.charAt(i - 1) == b.charAt(j - 1)) {
        return 1 + solve(a, b, i - 1, j - 1); // keep the matching pair
    }
    // drop the last char of one address and keep the better answer
    return Math.max(solve(a, b, i - 1, j), solve(a, b, i, j - 1));
}
```

### Problems with This Approach

Every mismatch makes two more calls. So in the worst case, the number of calls grows like `O(2^(m + n))`. With addresses of 1000 characters, this will never finish.

Most of these calls repeat work. For example, `solve(i - 1, j - 1)` can be reached from both `solve(i - 1, j)` and `solve(i, j - 1)`.

But there are only `(m + 1) * (n + 1)` different `(i, j)` pairs. If we solve each pair only once and save the result, the whole job takes `O(m * n)` time.

---

## Solution 1: Dynamic Programming Table

Instead of recursion, we fill a 2D table `dp`, going from small parts to bigger parts.

`dp[i][j]` = answer for the first `i` characters of `firstAddress` and the first `j` characters of `secondAddress`.

The rules are the same as in the recursion:

- Row `0` and column `0` are all `0`, because an empty part shares nothing.
- If `firstAddress.charAt(i - 1) == secondAddress.charAt(j - 1)`, then `dp[i][j] = dp[i - 1][j - 1] + 1`.
- Otherwise, `dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1])`.

We fill the table row by row, from left to right. Each cell only needs the cell above it, the cell on its left and the cell at its top left. All three are already filled when we reach it.

The final answer is in the bottom right cell, `dp[m][n]`.

**Why a 2D array?** Each answer depends on two numbers, `i` and `j`. A 2D array stores exactly that, and reading any cell takes `O(1)` time. A map with `(i, j)` keys would also work, but it is slower and needs more code for no benefit.

**Why `m + 1` rows and `n + 1` columns?** The extra row and column stand for an empty part. They are always `0`, so we never need special checks for `i - 1` or `j - 1` going below zero.

### Walkthrough

Rows come from `firstAddress = "12ab"` and columns come from `secondAddress = "1a2b"`. The `-` row and column are row `0` and column `0`, the empty parts.

```
        -   1   a   2   b
    -   0   0   0   0   0
    1   0   1   1   1   1
    2   0   1   1   2   2
    a   0   1   2   2   2
    b   0   1   2   2   3
```

- `dp[2][3]` compares `'2'` with `'2'`. They match, so `dp[2][3] = dp[1][2] + 1 = 2`. It means `"12"` and `"1a2"` share `"12"`.
- `dp[2][4]` compares `'2'` with `'b'`. No match, so `dp[2][4] = max(dp[1][4], dp[2][3]) = max(1, 2) = 2`.
- `dp[4][4] = 3` is the answer. For example, `"12b"` appears in both addresses.

### Code

```java
public class AddressSimilarityAnalyzer {

    public AddressSimilarityAnalyzer() {
    }

    /**
     * Returns the length of the longest sequence of characters
     * that appears in the same order in both addresses.
     * Time: O(m * n), Space: O(m * n)
     */
    public int findLongestSharedSequence(String firstAddress, String secondAddress) {
        int m = firstAddress.length();
        int n = secondAddress.length();

        // dp[i][j] = answer for the first i chars of firstAddress
        // and the first j chars of secondAddress.
        // Row 0 and column 0 mean an empty part, so they stay 0.
        int[][] dp = new int[m + 1][n + 1];

        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (firstAddress.charAt(i - 1) == secondAddress.charAt(j - 1)) {
                    // Match: answer without these two chars, plus 1 for this pair
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    // No match: drop one char from either address, keep the better answer
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }

        return dp[m][n];
    }
}
```

**Complexity:** `O(m * n)` time and `O(m * n)` space.

---

## Solution 2: Keep Only Two Rows

Look at the rules again. To fill row `i`, we only need row `i - 1` and the cells we already filled in row `i`. Older rows are never used again.

So instead of the full table, we keep just two 1D arrays:

- `prev` holds row `i - 1`.
- `curr` holds row `i`, the row we are filling.

When a row is done, `curr` becomes the new `prev`. The logic does not change, but memory drops from `O(m * n)` to `O(n)`.

### Code

```java
public class AddressSimilarityAnalyzer {

    public AddressSimilarityAnalyzer() {
    }

    /**
     * Same idea as the table solution, but keeps only two rows of it.
     * Time: O(m * n), Space: O(n)
     */
    public int findLongestSharedSequence(String firstAddress, String secondAddress) {
        int m = firstAddress.length();
        int n = secondAddress.length();

        // prev holds row i - 1 of the table. Row 0 is all zeros.
        int[] prev = new int[n + 1];

        for (int i = 1; i <= m; i++) {
            // curr holds row i. curr[0] stays 0 (empty part).
            int[] curr = new int[n + 1];

            for (int j = 1; j <= n; j++) {
                if (firstAddress.charAt(i - 1) == secondAddress.charAt(j - 1)) {
                    // Match: value at top left + 1
                    curr[j] = prev[j - 1] + 1;
                } else {
                    // No match: bigger of the value above and the value on the left
                    curr[j] = Math.max(prev[j], curr[j - 1]);
                }
            }

            // This row becomes the row above for the next step
            prev = curr;
        }

        return prev[n];
    }
}
```

**Complexity:** `O(m * n)` time and `O(n)` space.