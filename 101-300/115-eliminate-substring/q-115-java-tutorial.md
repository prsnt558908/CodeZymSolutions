# Eliminate Substring in Java

#### Problem Statement
[https://codezym.com/question/115-eliminate-substring](https://codezym.com/question/115-eliminate-substring)

---

## Core Idea

We read `s` from left to right and build the answer like a **stack**. We add one character at a time at the end.


When we cut out `sub`, the left and right parts join. A new copy of `sub` can only form at this join point. If we build the answer from left to right, the join point is always the **end** of what we have built so far.


So after adding each character, we ask only one question: "does the answer now end with `sub`?" If yes, we cut those characters off the end.


We start with a simple brute force, then move to the stack idea, and finally make the "ends with `sub`" check fast using the fallback table from the **KMP** string matching algorithm. The final solution runs in `O(n + m)` time.


Here `n` is the length of `s` and `m` is the length of `sub`.

---

## Solution 1: Brute Force (Simulate Exactly)

Do exactly what the statement says.

1. Find the first occurrence of `sub` in `s`.
2. Cut it out and join the left and right parts.
3. Repeat until `sub` is not found.

```java
public class EliminateSubstring {

    public EliminateSubstring() {
    }

    public String eliminateSubstring(String s, String sub) {
        int index = s.indexOf(sub);

        // Keep removing the first occurrence until none is left
        while (index != -1) {
            s = s.substring(0, index) + s.substring(index + sub.length());
            index = s.indexOf(sub);
        }
        return s;
    }
}
```

### Why is this slow?

Every removal searches from the start of the string again. It also builds a brand new string.


Take `s = "AAA...ABBB...B"` (50,000 `A` followed by 50,000 `B`) and `sub = "AB"`. There is only one `"AB"` at a time, right in the middle. So we remove 50,000 times, and every time we scan and copy a string of up to 100,000 characters.


**Time:** about `O(n²)` in the worst case.

**Space:** `O(n)`.

---

## Solution 2: Build the Answer Like a Stack

### Key observation

Look at Example 1. `"AWAWSSG"` becomes `"AWSG"` after one removal. The new `"AWS"` is made of `"AW"` from the left part and `"S"` from the right part.


So a new copy of `sub` always touches the point where we just cut.


Now read `s` from left to right and keep the characters in a `StringBuilder`. Whenever the builder ends with `sub`, delete those last `m` characters right away.


Because we delete a copy as soon as it appears, the builder **never** has a copy of `sub` inside it. So a new copy can only appear at the very end, ending at the character we just added.

### Why is the answer the same as Solution 1?

All copies of `sub` have the same length. A copy that starts earlier would also end earlier. But the builder had no copy before this character.


So the copy we just found is always the leftmost copy in the current string. That is exactly the one Solution 1 removes.

### Why StringBuilder?

`StringBuilder` lets us add at the end (`append`) and cut from the end (`setLength`) quickly. So it works as our stack.


At the end, it already holds the final answer. We just call `toString()`.

### Dry run (Example 1)

`s = "AWAWSSG"`, `sub = "AWS"`

| Read | Builder after adding | Ends with `"AWS"`? | Builder after check |
|---|---|---|---|
| A | `A` | No | `A` |
| W | `AW` | No | `AW` |
| A | `AWA` | No | `AWA` |
| W | `AWAW` | No | `AWAW` |
| S | `AWAWS` | Yes | `AW` |
| S | `AWS` | Yes | (empty) |
| G | `G` | No | `G` |

Final answer is `"G"`.

### Code

A small shortcut: we only compare when the new character is equal to the last character of `sub`.

```java
public class EliminateSubstring {

    public EliminateSubstring() {
    }

    public String eliminateSubstring(String s, String sub) {
        // Works like a stack: we add at the end and remove from the end
        StringBuilder result = new StringBuilder();
        int m = sub.length();
        char lastChar = sub.charAt(m - 1);

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            result.append(c);

            // A new copy of sub can only end at the character we just added
            if (c == lastChar && result.length() >= m && endsWithSub(result, sub)) {
                result.setLength(result.length() - m);
            }
        }
        return result.toString();
    }

    // Checks if the last sub.length() characters of result are equal to sub
    boolean endsWithSub(StringBuilder result, String sub) {
        int start = result.length() - sub.length();
        for (int j = 0; j < sub.length(); j++) {
            if (result.charAt(start + j) != sub.charAt(j)) {
                return false;
            }
        }
        return true;
    }
}
```

### Is this fast enough?

Each check can compare up to `m` characters. Most of the time a mismatch shows up quickly.


But a tricky input can make every check long. Take `s` as 100,000 `A`s and `sub` as 25,000 `A`s + `B` + 25,000 `A`s. The last character always matches, and each check compares about 25,000 characters before it hits the `B`.


**Time:** `O(n · m)` in the worst case.

**Space:** `O(n)`.

---

## Solution 3: Stack + KMP (Optimal)

### What is wasted in Solution 2?

Every check starts from zero. We forget everything we learned from the earlier characters.


The fix: for every character in the builder, also remember **how many characters of `sub` are matched** when the builder ends at that character. We store this in an array `matched`.


When we add a new character `c`:

- Let `j` be the `matched` value of the current last character.
- If `sub[j] == c`, the match grows to `j + 1`.
- If not, we do not restart from 0. We fall back to a shorter match that is still valid, then try again.


When `j` becomes `m`, the builder ends with `sub`, so we cut the last `m` characters.


Here is the nice part. After cutting, the new last character **already has its `matched` value stored**. So we continue from there without checking anything again.

### The lps array (fallback table)

`lps[i]` is the length of the longest part of `sub[0..i]` that is both a **prefix** and a **suffix** of it (not the whole part itself).


For `sub = "ABAB"`, `lps = [0, 0, 1, 2]`. For `"ABAB"`, the part `"AB"` is both at the start and at the end, so `lps[3] = 2`.


Why does this help? Suppose we matched `j` characters and then got a mismatch. The last `lps[j - 1]` characters we matched are also the first `lps[j - 1]` characters of `sub`. So we can continue from `lps[j - 1]` instead of from 0.


Example: `sub = "AAB"`, so `lps = [0, 1, 0]`. The builder is `"AA"` with 2 characters matched, and the next character is `A`.

- `sub[2]` is `B`, not `A`. Fall back to `j = lps[1] = 1`.
- `sub[1]` is `A`. It matches, so `j = 2`.


So `"AAA"` still ends with `"AA"`, which is the correct match length.

### Why do we need the `matched` array?

Without it, after a removal we would not know how much of `sub` the new end matches. We would need to scan again, and the slowness comes back.


Storing one number for each kept character solves this. Its size is at most `n`, and we use `result.length()` as the "top of stack" index, so both stay in sync.

### Dry run (Example 1)

`s = "AWAWSSG"`, `sub = "AWS"`, `lps = [0, 0, 0]`

| Read | `j` before | `j` after | Builder | `matched` | Note |
|---|---|---|---|---|---|
| A | 0 | 1 | `A` | [1] | |
| W | 1 | 2 | `AW` | [1, 2] | |
| A | 2 | 1 | `AWA` | [1, 2, 1] | `S` ≠ `A`, fall back to 0, then `A` matches |
| W | 1 | 2 | `AWAW` | [1, 2, 1, 2] | |
| S | 2 | 3 | `AW` | [1, 2] | `j = m`, remove `"AWS"` |
| S | 2 | 3 | (empty) | [] | reuses stored 2 of `W`, remove again |
| G | 0 | 0 | `G` | [0] | |


See the second `S`. After the first removal, we did not recheck anything. We picked up `j = 2` from the stored value of `W` and finished the next match in one step.

### Why is it `O(n + m)`?

`j` grows by at most 1 for each character we read, so it can grow at most `n` times in total.


Each fall back makes `j` smaller. A removal also makes it smaller, from `m` down to the stored value of the new last character.


Since `j` never goes below 0, the total number of fall back steps is at most `n`. Building `lps` takes `O(m)`.

### Code

```java
public class EliminateSubstring {

    public EliminateSubstring() {
    }

    public String eliminateSubstring(String s, String sub) {
        int m = sub.length();
        int[] lps = buildLps(sub);

        // Characters we have kept so far. Used like a stack.
        StringBuilder result = new StringBuilder();

        // matched[i] = how many characters of sub are matched
        // when result ends at index i
        int[] matched = new int[s.length()];

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            // Start from the match length of the current last character
            int size = result.length();
            int j = (size == 0) ? 0 : matched[size - 1];

            // On mismatch, fall back to a shorter match instead of 0
            while (j > 0 && sub.charAt(j) != c) {
                j = lps[j - 1];
            }
            if (sub.charAt(j) == c) {
                j++;
            }

            result.append(c);
            matched[size] = j;

            // Full sub matched: remove it from the end.
            // matched[] of the new last character is already stored,
            // so we can continue matching from there.
            if (j == m) {
                result.setLength(result.length() - m);
            }
        }
        return result.toString();
    }

    // lps[i] = length of the longest proper prefix of sub[0..i]
    // which is also a suffix of sub[0..i]
    int[] buildLps(String sub) {
        int m = sub.length();
        int[] lps = new int[m];
        int len = 0;

        for (int i = 1; i < m; i++) {
            while (len > 0 && sub.charAt(i) != sub.charAt(len)) {
                len = lps[len - 1];
            }
            if (sub.charAt(i) == sub.charAt(len)) {
                len++;
            }
            lps[i] = len;
        }
        return lps;
    }
}
```

A small detail: `j` is always less than `m` before we read `sub.charAt(j)`, because any value that reaches `m` is removed right away. So the index is always safe.


If `sub` is longer than `s`, `j` can never reach `m`, and we simply return `s` as it is.

**Time:** `O(n + m)`

**Space:** `O(n + m)`

---

## Summary

| Solution | Time | Space |
|---|---|---|
| Brute force | `O(n²)` worst case | `O(n)` |
| Stack | `O(n · m)` worst case | `O(n)` |
| Stack + KMP | `O(n + m)` | `O(n + m)` |


The key takeaway: a new copy of `sub` can only appear at the end of the answer we are building. A stack handles that naturally, and storing the KMP match length for each kept character lets us continue matching right after every removal.