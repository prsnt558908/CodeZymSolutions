# Eliminate Substring in Python

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

```python
class EliminateSubstring:
    def __init__(self):
        pass

    def eliminateSubstring(self, s: str, sub: str) -> str:
        index = s.find(sub)

        # Keep removing the first occurrence until none is left
        while index != -1:
            s = s[:index] + s[index + len(sub):]
            index = s.find(sub)
        return s
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


Now read `s` from left to right and keep the characters in a list. Whenever the list ends with `sub`, delete those last `m` characters right away.


Because we delete a copy as soon as it appears, the list **never** has a copy of `sub` inside it. So a new copy can only appear at the very end, ending at the character we just added.

### Why is the answer the same as Solution 1?

All copies of `sub` have the same length. A copy that starts earlier would also end earlier. But the list had no copy before this character.


So the copy we just found is always the leftmost copy in the current string. That is exactly the one Solution 1 removes.

### Why a list?

Python strings cannot be changed in place. Adding a character to a string creates a whole new string every time.


A list lets us add at the end (`append`) and cut from the end (`del result[-m:]`) quickly. So it works as our stack. At the end, we join it once with `"".join(result)`.

### Dry run (Example 1)

`s = "AWAWSSG"`, `sub = "AWS"`

| Read | List after adding | Ends with `"AWS"`? | List after check |
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

```python
class EliminateSubstring:
    def __init__(self):
        pass

    def eliminateSubstring(self, s: str, sub: str) -> str:
        # Works like a stack: we add at the end and remove from the end
        result = []
        m = len(sub)
        last_char = sub[-1]

        for c in s:
            result.append(c)

            # A new copy of sub can only end at the character we just added
            if c == last_char and len(result) >= m and self.ends_with_sub(result, sub):
                del result[-m:]

        return "".join(result)

    # Checks if the last len(sub) characters of result are equal to sub
    def ends_with_sub(self, result: list, sub: str) -> bool:
        start = len(result) - len(sub)
        for j in range(len(sub)):
            if result[start + j] != sub[j]:
                return False
        return True
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


The fix: for every character in the list, also remember **how many characters of `sub` are matched** when the list ends at that character. We store this in a second list `matched`.


When we add a new character `c`:

- Let `j` be the `matched` value of the current last character.
- If `sub[j] == c`, the match grows to `j + 1`.
- If not, we do not restart from 0. We fall back to a shorter match that is still valid, then try again.


When `j` becomes `m`, the list ends with `sub`, so we cut the last `m` characters.


Here is the nice part. After cutting, the new last character **already has its `matched` value stored**. So we continue from there without checking anything again.

### The lps array (fallback table)

`lps[i]` is the length of the longest part of `sub[0..i]` that is both a **prefix** and a **suffix** of it (not the whole part itself).


For `sub = "ABAB"`, `lps = [0, 0, 1, 2]`. For `"ABAB"`, the part `"AB"` is both at the start and at the end, so `lps[3] = 2`.


Why does this help? Suppose we matched `j` characters and then got a mismatch. The last `lps[j - 1]` characters we matched are also the first `lps[j - 1]` characters of `sub`. So we can continue from `lps[j - 1]` instead of from 0.


Example: `sub = "AAB"`, so `lps = [0, 1, 0]`. The list is `"AA"` with 2 characters matched, and the next character is `A`.

- `sub[2]` is `B`, not `A`. Fall back to `j = lps[1] = 1`.
- `sub[1]` is `A`. It matches, so `j = 2`.


So `"AAA"` still ends with `"AA"`, which is the correct match length.

### Why do we need the `matched` list?

Without it, after a removal we would not know how much of `sub` the new end matches. We would need to scan again, and the slowness comes back.


Storing one number for each kept character solves this. `result` and `matched` always have the same length, so whenever we delete `m` items from one, we delete `m` items from the other too.

### Dry run (Example 1)

`s = "AWAWSSG"`, `sub = "AWS"`, `lps = [0, 0, 0]`

| Read | `j` before | `j` after | List | `matched` | Note |
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

```python
class EliminateSubstring:
    def __init__(self):
        pass

    def eliminateSubstring(self, s: str, sub: str) -> str:
        m = len(sub)
        lps = self.build_lps(sub)

        # Characters we have kept so far. Used like a stack.
        result = []

        # matched[i] = how many characters of sub are matched
        # when result ends at index i
        matched = []

        for c in s:
            # Start from the match length of the current last character
            j = matched[-1] if matched else 0

            # On mismatch, fall back to a shorter match instead of 0
            while j > 0 and sub[j] != c:
                j = lps[j - 1]
            if sub[j] == c:
                j += 1

            result.append(c)
            matched.append(j)

            # Full sub matched: remove it from the end of both lists.
            # matched of the new last character is already stored,
            # so we can continue matching from there.
            if j == m:
                del result[-m:]
                del matched[-m:]

        return "".join(result)

    # lps[i] = length of the longest proper prefix of sub[0..i]
    # which is also a suffix of sub[0..i]
    def build_lps(self, sub: str) -> list:
        m = len(sub)
        lps = [0] * m
        length = 0

        for i in range(1, m):
            while length > 0 and sub[i] != sub[length]:
                length = lps[length - 1]
            if sub[i] == sub[length]:
                length += 1
            lps[i] = length
        return lps
```

A small detail: `j` is always less than `m` before we read `sub[j]`, because any value that reaches `m` is removed right away. So the index is always safe.


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