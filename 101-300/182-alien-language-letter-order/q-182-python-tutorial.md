# Alien Language Letter Order in Python

#### Problem Statement
[https://codezym.com/question/182-alien-language-letter-order](https://codezym.com/question/182-alien-language-letter-order)


## Core Idea

The word list is sorted by the alien alphabet. So neighboring words quietly give us rules, like "`b` comes before `a`".

Think of each rule as an arrow from one letter to another. A valid alphabet is any order of letters where every arrow points forward. Finding such an order is called a **topological sort**.

We want the smallest valid answer. So we build the order one letter at a time, and at every step we pick the smallest letter that is allowed to go next. If at some point no letter is allowed, the rules go in a circle and the answer is an empty string.


## Step 1: Find the Rules

Compare each word with the word right after it. Only the **first position where they differ** gives a rule.

For `"abca"` and `"cab"`, the first letters differ, so `a` comes before `c`. The letters after that point tell us nothing. In English, `"cat"` before `"dog"` tells us `c < d`, but nothing about `a` and `o`.

Comparing only neighbors is enough. If every word comes before the next word, the whole list is in order. So words that are far apart give no new rules.

**The prefix trap:** if no letter differs, one word is a prefix of the other. Then the shorter word must come first. `"abc"` before `"abcd"` is fine. `"abc"` before `"ab"` can never happen, so we return `""` right away.

```python
rules = []

for i in range(len(words) - 1):
    first, second = words[i], words[i + 1]
    length = min(len(first), len(second))
    j = 0
    # skip the common start of both words
    while j < length and first[j] == second[j]:
        j += 1
    if j < length:
        # first different letters: first[j] comes before second[j]
        rules.append((first[j], second[j]))
    elif len(first) > len(second):
        return ""  # longer word before its own prefix, like "abc" before "ab"
```

Some letters never show up in any rule, like `a` and `b` in `["abc", "abx"]`. They must still be in the answer, so we also collect every unique letter from all the words.


## Simple Solution: Scan From a to z Every Time

Now we turn the rules into an order, one letter at a time.

A letter is **free** when every letter that must come before it is already placed. At each step, we check letters from `a` to `z` and place the first free one.

**Why does this give the smallest answer?** The first letter of any valid order must be a free letter. Picking the smallest free letter makes the first position as small as possible. Placing a free letter never breaks a rule and never blocks the remaining letters. So we can make the same choice again for the next position, and the next.

If no letter is free but some letters are still left, the rules must contain a circle, like `a` before `b` and `b` before `a`. So we return `""`.

```python
# letters: set of all unique letters, rules: list from Step 1
order = []
placed = set()

while len(placed) < len(letters):
    found = False
    for c in sorted(letters):
        if c not in placed and self.isFree(c, rules, placed):
            placed.add(c)
            order.append(c)
            found = True
            break
    if not found:
        return ""  # no letter is free: the rules form a loop

return "".join(order)
```

```python
def isFree(self, c, rules, placed) -> bool:
    # c is free when every letter that must come before c is already placed
    for before, after in rules:
        if after == c and before not in placed:
            return False
    return True
```

**The problem:** `isFree()` scans the whole rule list for every letter, in every round. With `k` letters and `r` rules, that is about `k × k × r` checks, and almost all of them repeat work we already did.


## Optimized Solution: Kahn's Algorithm With a Min-Heap

Two small changes remove the repeated work.

**1. Keep a count instead of rechecking rules.**
For every letter, store `in_degree`: how many letters must still come before it. A letter is free when its count is `0`. When we place a letter, we lower the count only for the letters that come right after it. Now each rule is handled just once.

**2. Keep free letters in a min-heap.**
Python's `heapq` keeps a plain list as a min-heap, so `heappop()` always hands back the smallest letter. So we no longer scan from `a` to `z`.

This is the well known **Kahn's algorithm** for topological sort. The only twist is the min-heap in place of a normal queue, and that is what makes the answer the smallest.

**Loop check:** if the heap runs empty before every letter is placed, some letters never became free. That only happens when the rules contain a circle, so we return `""`.


### Important Data Structures

- **`graph`** (`dict` of letter to `set`): for each letter, the letters that must come after it. When we place a letter, this tells us whose counts to lower.
- **`in_degree`** (`dict` of letter to count): how many letters must still come before each letter. It tells us in one step if a letter is free.
- **`free_letters`** (a list used as a min-heap with `heapq`): every letter that is free right now. `heappop()` always gives back the smallest one.

**Watch out for repeated rules.** In `["za", "zb", "ca", "cb"]`, two pairs give the same rule `a` before `b`. The set inside `graph` stores it once, and we raise `in_degree` only when the rule is new. If we counted it twice, `b` would wait for two arrows when only one exists, and it would never become free.


### Dry Run

Words: `["za", "zb", "ca", "cb"]`

Rules: `a → b` (found twice, kept once) and `z → c`.

Starting counts: `a = 0`, `b = 1`, `c = 1`, `z = 0`.

| Step | Free letters | Pick | Order so far | Counts updated |
|------|--------------|------|--------------|----------------|
| 1 | a, z | a | a | b: 1 → 0, so b is free |
| 2 | b, z | b | ab | none |
| 3 | z | z | abz | c: 1 → 0, so c is free |
| 4 | c | c | abzc | none |

Answer: `"abzc"`.

Look at step 2. `b` just became free and it is smaller than `z`, so it goes first. A normal first in, first out queue would pick `z` there and return `"azbc"`. That order is valid, but it is not the smallest.


### Complexity

Let `L` be the total number of letters in all words, `k` the number of unique letters (at most 26), and `r` the number of unique rules.

- **Time:** `O(L + (k + r) log k)`. Collecting letters and finding rules takes `O(L)`. Each letter enters the heap once and each rule is used once. Since `k` is at most 26, the heap part is tiny, so in practice this is `O(L)`.
- **Space:** `O(k + r)` for the graph, the counts and the heap.


### Python Code

```python
import heapq
from typing import Dict, List, Set


class AlienLanguageOrder:

    def __init__(self):
        pass

    def alienOrder(self, words: List[str]) -> str:
        # graph: letter -> letters that must come after it
        graph: Dict[str, Set[str]] = {}
        # in_degree: letter -> how many letters must still come before it
        in_degree: Dict[str, int] = {}

        # Every unique letter must be in the answer, even if no rule mentions it
        for word in words:
            for c in word:
                graph.setdefault(c, set())
                in_degree.setdefault(c, 0)

        # Step 1: find one rule from each pair of neighboring words
        for i in range(len(words) - 1):
            if not self.addRule(words[i], words[i + 1], graph, in_degree):
                return ""

        # Step 2: Kahn's algorithm, always taking the smallest free letter
        free_letters = [c for c in in_degree if in_degree[c] == 0]
        heapq.heapify(free_letters)

        order = []
        while free_letters:
            letter = heapq.heappop(free_letters)
            order.append(letter)

            # letter is placed, so every letter after it waits for one less letter
            for after in graph[letter]:
                in_degree[after] -= 1
                if in_degree[after] == 0:
                    heapq.heappush(free_letters, after)

        # Some letters never became free: the rules form a loop
        if len(order) < len(in_degree):
            return ""
        return "".join(order)

    def addRule(self, first: str, second: str,
                graph: Dict[str, Set[str]], in_degree: Dict[str, int]) -> bool:
        """Adds the rule given by two neighboring words.
        Returns False when the pair is impossible, like "abc" before "ab".
        """
        for before, after in zip(first, second):
            if before != after:
                # count a rule only the first time we see it
                if after not in graph[before]:
                    graph[before].add(after)
                    in_degree[after] += 1
                return True
        # No different letter found, so the shorter word must come first
        return len(first) <= len(second)
```