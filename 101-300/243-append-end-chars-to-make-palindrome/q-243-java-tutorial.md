# Append Minimum Characters At End To Make Palindrome in Java

#### Problem Statement
[https://codezym.com/question/243-append-end-chars-to-make-palindrome](https://codezym.com/question/243-append-end-chars-to-make-palindrome)

## Core Idea

We can only add characters at the end, so `s` always stays at the front of the final palindrome. The trick is to find the **longest palindromic suffix** of `s`, which is the longest ending part of `s` that is already a palindrome.

This part can stay in the middle of the final palindrome. Every character before it must be copied to the end in reverse order. So the answer is simply `s.length()` minus the length of the longest palindromic suffix.

We first find this suffix with a simple brute force. Then we make it fast, `O(n)`, by reversing `s` and using the LPS array from the KMP string matching algorithm.

## Understanding The Problem

Take `s = "abcdc"`. Its ending part `"cdc"` is already a palindrome, and only `"ab"` comes before it.

Append `"ab"` in reverse order, which is `"ba"`, and we get `"abcdcba"`. Just `2` new characters.

**Why is this always the minimum?**

Say we append `k` characters. These `k` new characters must mirror the first `k` characters of `s`.

What is left in the middle is the suffix of `s` that starts at index `k`. The middle of a palindrome is also a palindrome, so this suffix must be a palindrome too.

So we need the smallest `k` whose suffix is a palindrome, which is the same as the longest palindromic suffix. A single character is always a palindrome, so `k` is never more than `n - 1`.

## Solution 1: Brute Force

Check every suffix, starting from the longest one. The first suffix that is a palindrome is the longest palindromic suffix.

If it starts at index `start`, then exactly `start` characters come before it. So `start` is our answer.

```java
public int minimumCharactersToAppend(String s) {
    int n = s.length();
    // Try the longest suffix first, then shorter ones
    for (int start = 0; start < n - 1; start++) {
        if (isPalindrome(s, start, n - 1)) {
            return start;
        }
    }
    // Only the last character is left, and one character is always a palindrome
    return n - 1;
}

// Two pointers: compare characters from both ends, moving inwards
boolean isPalindrome(String s, int left, int right) {
    while (left < right) {
        if (s.charAt(left) != s.charAt(right)) {
            return false;
        }
        left++;
        right--;
    }
    return true;
}
```

### Problem With Brute Force

One palindrome check can take up to `n` steps, and we may run it for almost `n` starting points. So the worst case is `O(n^2)`.

For a string with many `a`s, then one `b`, then fewer `a`s, most checks walk deep into the string before they fail. With `100,000` characters this can mean over a billion character comparisons.

The real waste is that every check starts from zero. It forgets everything the earlier checks already learned about the string.

## Solution 2: Optimal Using LPS Array (KMP)

### Step 1: Turn The Palindrome Check Into A Matching Problem

Let `rev` be `s` reversed.

If you read the last `L` characters of `s` backwards, you get exactly the first `L` characters of `rev`.

A palindrome reads the same in both directions. So:

> The last `L` characters of `s` form a palindrome **exactly when** they are equal to the first `L` characters of `rev`.

For `s = "abab"`, we get `rev = "baba"`. The last `3` characters of `s` are `"bab"`, and the first `3` characters of `rev` are also `"bab"`. They match, so `"bab"` is a palindromic suffix.

So the problem becomes: **find the longest `L` where the end of `s` matches the start of `rev`.**

### Step 2: Let The LPS Array Find The Match

The LPS (Longest Prefix Suffix) array from the KMP algorithm solves exactly this kind of problem.

For a string `text`, `lps[i]` is the length of the longest part that is **both a prefix and a suffix** of `text[0..i]`. The whole `text[0..i]` does not count.

For example, in `"abab"` the part `"ab"` is both a prefix and a suffix, so the last LPS value is `2`.

Now we join everything into one string:

```
combined = rev + "#" + s
```

`combined` starts with `rev` and ends with `s`. So the last LPS value of `combined` is the longest `L` where the start of `rev` matches the end of `s`.

That `L` is the length of the longest palindromic suffix, and the answer is `n - L`.

**Why do we need `#`?**

`#` never appears in `s`, so it works like a wall. The matching part can never cross it, which keeps `L` at most `n`.

Without it, `s = "aa"` would give `"aaaa"`, whose last LPS value is `3`. That is longer than `s` itself and would give the wrong answer `-1`. With the wall, `"aa#aa"` gives `2`, and the answer is correctly `0`.

### Step 3: How The LPS Array Is Built

We walk through `text` once and keep one number called `matched`. When we reach index `i`, it holds `lps[i - 1]`: the length of the prefix that matches the end of what we have seen so far.

- If `text[i]` is equal to `text[matched]`, the next character of the prefix also matches, so `matched` grows by one.
- If not, we **do not** restart from zero. We fall back to `matched = lps[matched - 1]`, the next shorter prefix that still matches, and compare again.

Reusing old answers like this is exactly what the brute force was missing.

`matched` grows by at most one per step, and every fall back makes it smaller. So the total number of fall backs is never more than the length of `text`. The whole build takes `O(n)` time.

### Walkthrough: `s = "abab"`

`rev = "baba"`, so `combined = "baba#abab"`.

| Index | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|---|
| Char | b | a | b | a | # | a | b | a | b |
| lps | 0 | 0 | 1 | 2 | 0 | 0 | 1 | 2 | 3 |

At index `4`, `#` matches nothing, so `matched` drops to `0`. After that, the end of `s` slowly matches the start of `rev`: first `"b"`, then `"ba"`, then `"bab"`.

The last value is `3`, so the longest palindromic suffix is `"bab"`. The answer is `4 - 3 = 1`, and appending `"a"` gives `"ababa"`.

### Why These Data Structures

- **`StringBuilder`**: Java strings cannot be changed, so we use `StringBuilder` and its built-in `reverse()` to get `rev` in one line.
- **`int[] lps`**: We need one number per position, and while falling back we jump to older values by index. A plain array does both instantly.

### Complexity

- **Time:** `O(n)`. `combined` has `2n + 1` characters and we scan it once.
- **Space:** `O(n)` for `rev`, `combined` and the `lps` array.

### Java Code

```java
import java.util.*;

public class PalindromeAppender {

    public PalindromeAppender() {
    }

    /**
     * Returns the minimum number of characters to append at the end of s
     * so that s becomes a palindrome.
     *
     * answer = length of s - length of the longest palindromic suffix of s
     */
    public int minimumCharactersToAppend(String s) {
        int n = s.length();
        String rev = new StringBuilder(s).reverse().toString();

        // '#' never appears in s, so a match cannot run past rev into s
        String combined = rev + "#" + s;
        int[] lps = buildLps(combined);

        // Longest start of rev that is equal to the end of s.
        // This is the longest palindromic suffix of s.
        int longestPalindromicSuffix = lps[combined.length() - 1];

        // Every character before that suffix must be mirrored at the end
        return n - longestPalindromicSuffix;
    }

    /**
     * lps[i] = length of the longest prefix of text[0..i] that is also
     * a suffix of text[0..i], not counting text[0..i] itself.
     */
    int[] buildLps(String text) {
        int[] lps = new int[text.length()];
        int matched = 0; // length of the prefix that matches right now

        for (int i = 1; i < text.length(); i++) {
            // Mismatch: fall back to the next shorter prefix that still matches
            while (matched > 0 && text.charAt(i) != text.charAt(matched)) {
                matched = lps[matched - 1];
            }
            // Match: the matching prefix grows by one character
            if (text.charAt(i) == text.charAt(matched)) {
                matched++;
            }
            lps[i] = matched;
        }
        return lps;
    }
}
```