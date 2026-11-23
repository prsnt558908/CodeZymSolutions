# Minimum Times To Append String For Subsequence in Python

#### Problem Statement
[https://codezym.com/question/246-minimum-times-append-string-for-subsequence](https://codezym.com/question/246-minimum-times-append-string-for-subsequence)

## Core Idea

Picture copies of `s` placed one after another: `s s s ...`.

We read `t` from left to right and match each letter with the **earliest** matching letter in the current copy of `s`. When the current copy has no match left, we move on to the next copy.

Taking the earliest match is always safe. It leaves the most of `s` free for the letters that come after, so this greedy walk uses the fewest copies.

Two simple cases: an empty `t` needs `0` copies. And if `t` has a letter that `s` does not have, no number of copies can help, so the answer is `-1`.

We first write this greedy in the most direct way. It is correct, but slow on big inputs. Then we make it fast with a lookup table that instantly answers one question: **"from index `i` in `s`, where is the next letter `c`?"**

---

## Approach 1: Simple Greedy (Brute Force)

Use one copy of `s` at a time and walk through it from start to end. Whenever the current letter of `s` equals the next unmatched letter of `t`, that letter of `t` is matched.

Keep counting copies until all of `t` is matched. If a whole copy matches nothing, the next letter of `t` is not in `s`, so we return `-1`.

```python
def minimumTimesToAppend(self, s: str, t: str) -> int:
    copies = 0
    j = 0  # index of the next character of t to match

    while j < len(t):
        matched_before = j

        # use one full copy of s and match as much of t as we can
        for ch in s:
            if j < len(t) and ch == t[j]:
                j += 1

        # this copy matched nothing, so t[j] is not in s
        if j == matched_before:
            return -1
        copies += 1

    return copies
```

### Why is it slow?

Each copy walks through `s` one letter at a time, even when it matches only one letter of `t`.

Take `s` = `"a"` followed by 99,999 `'b'`s, and `t` = 100,000 `'a'`s. Every copy matches just one `'a'`, but still walks all 100,000 letters of `s`. That is 100,000 copies × 100,000 steps = **10 billion steps**.

So the time is `O(n × m)`, where `n` is the length of `s` and `m` is the length of `t`.

The wasted work is the walking. We step through `s` only to find the next matching letter. If we could **jump** straight to it, each letter of `t` would cost just one step.

---

## Approach 2: Next Index Table (Optimal)

### The table

We build a 2D list `next_index` where:

> `next_index[i][c]` = the first index `>= i` where letter `c` appears in `s`, or `-1` if it does not appear from index `i` onward.

Rows are indexes `0` to `n`. Columns are the 26 letters, and letter `ch` goes to column `ord(ch) - ord('a')`, so `'a'` is column 0 and `'z'` is column 25.

**Why this table?** The brute force keeps asking the same question: "from index `i`, where is the next letter `c`?"

There are only `n + 1` indexes and 26 letters. So we answer every possible question once, up front. After that, each question is a single list lookup.

A plain 2D list is enough here, because both the row (an index) and the column (a letter) are small whole numbers.

### Building the table

Fill it from right to left:

- Row `n`, just past the end of `s`, is all `-1`. Nothing comes after the end.
- Row `i` is a copy of row `i + 1`, with one change: letter `s[i]` now points to `i`.

This works because the next `c` from index `i` is either at `i` itself, or it is the next `c` from index `i + 1`.

Table for `s = "abc"`:

| i | a | b | c |
|:-:|:-:|:-:|:-:|
| 0 | 0 | 1 | 2 |
| 1 | -1 | 1 | 2 |
| 2 | -1 | -1 | 2 |
| 3 | -1 | -1 | -1 |

### Matching `t` with the table

We track two values:

- `copies`: how many copies of `s` we are using. It starts at `1`, since we begin inside the first copy.
- `position`: where we are inside the current copy. It starts at `0`.

For each letter `c` of `t`:

1. If `next_index[0][c]` is `-1`, the letter is not in `s` at all. Return `-1`.
2. Look up `match_index = next_index[position][c]`.
3. If `match_index` is `-1`, the rest of this copy has no `c`. Start a new copy: increase `copies`, and set `match_index = next_index[0][c]`, the first `c` in `s`.
4. Move `position` to `match_index + 1`.

### Dry Run

`s = "abc"`, `t = "cab"`

| Letter | `position` | Lookup | What happens | `copies` |
|:-:|:-:|---|---|:-:|
| `c` | 0 | `next_index[0][c] = 2` | match at index 2, `position` becomes 3 | 1 |
| `a` | 3 | `next_index[3][a] = -1` | copy used up. New copy, `next_index[0][a] = 0`, `position` becomes 1 | 2 |
| `b` | 1 | `next_index[1][b] = 1` | match at index 1, `position` becomes 2 | 2 |

The answer is `2`. Indeed, `"cab"` is a subsequence of `"abcabc"`.

### Complexity

- Building the table: `O(26 × n)` time and space.
- Matching `t`: `O(m)` time, one lookup per letter.
- Total: `O(26 × n + m)`. For the largest input that is about 2.7 million steps, instead of 10 billion.

### Complete Code

```python
from typing import List


class RepeatedStringSubsequenceFinder:
    """
    Finds the minimum number of copies of s, placed back to back,
    so that t becomes a subsequence of them.
    """

    def __init__(self):
        pass

    def minimumTimesToAppend(self, s: str, t: str) -> int:
        # an empty t needs no copies at all
        if len(t) == 0:
            return 0

        next_index = self.buildNextIndexTable(s)

        copies = 1      # we start inside the first copy of s
        position = 0    # current index inside the current copy

        for ch in t:
            letter = ord(ch) - ord('a')

            # this letter does not exist anywhere in s
            if next_index[0][letter] == -1:
                return -1

            match_index = next_index[position][letter]

            # no such letter in the rest of this copy, so start a new copy
            if match_index == -1:
                copies += 1
                match_index = next_index[0][letter]

            position = match_index + 1

        return copies

    def buildNextIndexTable(self, s: str) -> List[List[int]]:
        """
        next_index[i][c] = first index >= i where letter c appears in s,
        or -1 if it does not appear from index i onward.
        Row len(s) is all -1, since nothing comes after the end of s.
        """
        n = len(s)
        next_index = [[-1] * 26 for _ in range(n + 1)]

        # fill from right to left, so row i can start as a copy of row i + 1
        for i in range(n - 1, -1, -1):
            next_index[i] = next_index[i + 1].copy()
            next_index[i][ord(s[i]) - ord('a')] = i

        return next_index
```