# Maximum Length Subsequence Substring in Java

#### Problem Statement
[https://codezym.com/question/263-max-length-subseq-substring](https://codezym.com/question/263-max-length-subseq-substring)


## Core Idea

The answer must be a **substring of `y`** and also a **subsequence of `x`**. So every possible answer is simply a block of `y`, and we need the longest block of `y` that can be picked out of `x` in order.


The trick is to pick a starting position in `y` and grow the block to the right for as long as it still fits inside `x`. A classic two pointer walk over `x` does all this growing in a single pass, so each start costs just one walk.


We do this for every start, keep the longest block, and break ties by keeping the lexicographically smaller block. We will begin with a brute force solution and then improve it with two small observations.


---

## Understanding the Problem

- **Substring:** characters that sit next to each other.
- **Subsequence:** characters in the same order, but gaps are allowed.


For `x = "axbxcxd"` and `y = "zzabcdyy"`, the string `"abcd"` sits together inside `y`. It can also be picked from `x` by skipping the extra letters: **a**x**b**x**c**x**d**.


No longer block of `y` works, so the answer is `"abcd"`.


---

## Solution 1: Brute Force

The most direct idea is to try **every substring of `y`** and check if it is a subsequence of `x`.


To check if a string `s` is a subsequence of `x`, we use two pointers. Walk through `x` one character at a time. Whenever the current character of `x` is the next character we need from `s`, move forward in `s`. If we reach the end of `s`, then `s` can be picked from `x`.

```java
// two pointer check: can s be picked out of x in the same order?
boolean isSubsequence(String s, String x) {
    int k = 0; // index of the next character of s we need
    for (int i = 0; i < x.length() && k < s.length(); i++) {
        if (x.charAt(i) == s.charAt(k)) {
            k++;
        }
    }
    return k == s.length();
}
```


Now we try every substring and keep the best valid one.

```java
String best = "";

// try every substring y[start .. end - 1]
for (int start = 0; start < y.length(); start++) {
    for (int end = start + 1; end <= y.length(); end++) {
        String candidate = y.substring(start, end);
        if (isSubsequence(candidate, x) && isBetter(candidate, best)) {
            best = candidate;
        }
    }
}
return best;
```


`isBetter()` returns `true` when the candidate is longer, or when it has the same length and is lexicographically smaller. Its code is in the final solution below.


### Why is this slow?

`y` can have around 2 million substrings (`2000 × 2000 / 2`). Each check can walk through all 2000 characters of `x`. That is billions of steps.


There is also a lot of repeated work. After checking `"ab"`, we check `"abc"` by walking through `x` again from the beginning, even though `"ab"` was already matched.


---

## Solution 2: Greedy Two Pointer

### Observation 1: Shorter blocks from the same start always work

If `"abcd"` can be picked from `x`, then `"abc"`, `"ab"` and `"a"` can be picked too. We simply drop the last characters.


So for a fixed start in `y`, once a block fails, every longer block from that start fails too. This means for each start we only need **one** thing: the longest valid block.


### Observation 2: One walk over `x` finds that longest block

Look at the two pointer check again. It walks through `x` and matches `y[start]`, then `y[start + 1]`, then `y[start + 2]`, and so on.


If we do not stop at a fixed end and simply keep asking for the next character of `y`, then when `x` runs out (or `y` ends), the pointer in `y` stops right after the longest block that can be matched.


Why is it safe to match each character at its **earliest** place in `x`? Because an earlier match leaves more of `x` free for the characters that come next. So this greedy choice never misses a longer block.


So each start needs just one walk over `x`. We do not need any table or map here, only two index pointers and the best string found so far.


### Picking the final answer

Each start gives us one candidate block. We keep the best one:

- A longer block always wins.
- For the same length, the lexicographically smaller block wins.


If no character of `y` appears in `x`, every candidate is `""`, so `best` stays `""`. That is exactly the required output.


### Dry Run

`x = "cabd"`, `y = "abca"`

| start | greedy matching inside `x` | longest block |
|---|---|---|
| 0 | `a` → `x[1]`, `b` → `x[2]`, no `c` after `x[2]` | `"ab"` |
| 1 | `b` → `x[2]`, no `c` after `x[2]` | `"b"` |
| 2 | `c` → `x[0]`, `a` → `x[1]`, `y` ends | `"ca"` |
| 3 | `a` → `x[1]`, `y` ends | `"a"` |


The longest length is 2. Both `"ab"` and `"ca"` have it, and `"ab"` is lexicographically smaller. So the answer is `"ab"`.


### Java Code

```java
public class MaximumLengthSubsequenceSubstring {

    public MaximumLengthSubsequenceSubstring() {

    }

    /**
     * Returns the longest string that is a subsequence of x
     * and a contiguous substring of y.
     * If many strings have the maximum length, returns the
     * lexicographically smallest one. Returns "" if none exists.
     */
    public String longestSubsequenceSubstring(String x, String y) {
        String best = "";

        // try every starting position in y
        for (int start = 0; start < y.length(); start++) {

            // y[start .. end - 1] is already matched inside x
            int end = start;

            // one walk over x: match y[end] at the earliest possible place
            for (int i = 0; i < x.length() && end < y.length(); i++) {
                if (x.charAt(i) == y.charAt(end)) {
                    end++;
                }
            }

            // longest valid block of y that begins at 'start'
            String candidate = y.substring(start, end);
            if (isBetter(candidate, best)) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * A longer string is better.
     * For equal length, the lexicographically smaller string is better.
     */
    boolean isBetter(String candidate, String best) {
        if (candidate.length() != best.length()) {
            return candidate.length() > best.length();
        }
        return candidate.compareTo(best) < 0;
    }
}
```


### Complexity

- **Time:** `O(n × m)`, where `n` is the length of `x` and `m` is the length of `y`. Each of the `m` starts walks through `x` once. Creating and comparing a candidate never costs more than that walk, because every character in the candidate was matched during the walk. For `2000 × 2000` this is about 4 million simple steps.
- **Space:** `O(m)`. We only store the current candidate and the best answer.