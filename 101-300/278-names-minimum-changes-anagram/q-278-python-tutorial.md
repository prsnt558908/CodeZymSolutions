# Names Requiring Minimum Changes to Become an Anagram in Python

#### Problem Statement
[https://codezym.com/question/278-names-minimum-changes-anagram](https://codezym.com/question/278-names-minimum-changes-anagram)


## Core Idea

Two strings are anagrams when they contain the same characters, the same number of times. Positions do not matter, because we can rearrange characters freely.


So for every candidate, we only need to answer one question: **how many characters of `name` are missing from it?** Each missing character costs exactly one change.


We count the characters of `name` once. Then for each candidate we find how many characters are missing, and we keep the candidates with the smallest number, in their original order.


## Why Each Missing Character Costs One Change

Take `name = "aabc"` and the candidate `"aabb"`.


The candidate already has both `a` and one `b`, so those can stay. Its second `b` is extra, and `c` is missing.


Replace the extra `b` with `c` and we get `"aabc"`. That is **1 missing character** and **1 change**.


This always holds:

- One change adds only one new character. So we need at least one change per missing character.
- Both strings have the same length. So there are exactly as many extra characters as missing ones, and each extra one can be replaced by a missing one.


So the number of changes is the total number of missing characters. This is the "sum of deficits" from the problem statement.


## Approach 1: Brute Force (Match Characters One by One)

The most direct idea is to match characters by hand.


For each character of `name`, search the candidate for a copy that is not used yet.

- If we find one, we mark it as used. This character can stay.
- If we do not find one, the character is missing. We need one change.

```python
def countChanges(self, name: str, candidate: str) -> int:
    used = [False] * len(candidate)
    changes = 0

    for c in name:
        found = False
        for j in range(len(candidate)):
            if not used[j] and candidate[j] == c:
                used[j] = True   # this copy is now taken
                found = True
                break
        if not found:
            changes += 1         # c is missing, one change needed
    return changes
```

We run this for every candidate and keep the ones with the smallest result.


### Why is this slow?

For every character of `name`, we may scan the whole candidate. If the length is `L`, that is `O(L²)` work for a single candidate.


`L` can be `100,000`, which means up to `10^10` steps for one candidate. That is far too slow.


The real waste is that we keep searching for **where** a character is. But we only care about **how many times** it appears.


## Approach 2: Count Characters (Optimal)

Since only counts matter, we stop searching and start counting.


### Step 1: Count the characters of `name` once

We build a count table `nameCount`, where `nameCount[ord(c)]` is how many times character `c` appears in `name`.


**Why a list of 128 numbers?** All characters are English letters, and their ASCII codes (from `ord(c)`) are below 128. So the code itself can be the index, like `nameCount[ord('a')]`. It works like a small map from character to count, but we never have to handle missing keys, and copying or summing it is quick.


`name` is the same for every candidate, so we count it only once.


### Step 2: Find what each candidate is missing

For each candidate, we copy `nameCount` into a new list called `need`. Think of `need` as a shopping list: how many of each character we still need.


Then we walk through the candidate:

- If the character is still on the list (`need[ord(c)] > 0`), we keep it and reduce its count by 1.
- Otherwise it is an extra character, so we skip it.


Whatever is left in `need` is missing. The sum of `need` is the number of changes.


We copy the list because every candidate must start with the full list. The copy has only 128 slots, so it is cheap.


### Step 3: Keep the names with the fewest changes

We walk through `nameList` once and track two things:

- `minChanges`: the smallest number of changes seen so far.
- `result`: every name that needs exactly `minChanges` changes.


For each candidate:

- If it needs **fewer** changes than `minChanges`, it is the new best. We clear `result` and update `minChanges`.
- If it needs **exactly** `minChanges` changes, we add it to `result`.


We go from left to right and add every match, so the input order and duplicate names are kept automatically.


### Dry Run

`name = "aabc"`, `nameList = ["aabb", "abcc", "dddd", "aabb"]`


`nameCount`: `a = 2, b = 1, c = 1`

| Candidate | Missing | Changes | minChanges | result |
|-----------|---------|---------|------------|--------|
| `aabb` | `c` | 1 | 1 | `[aabb]` |
| `abcc` | `a` | 1 | 1 | `[aabb, abcc]` |
| `dddd` | `a, a, b, c` | 4 | 1 | `[aabb, abcc]` |
| `aabb` | `c` | 1 | 1 | `[aabb, abcc, aabb]` |


Final answer: `["aabb", "abcc", "aabb"]`


### Code

```python
from typing import List


class NamesRequiringMinimumChanges:
    def __init__(self):
        pass

    def findNamesWithMinimumChanges(self, name: str, nameList: List[str]) -> List[str]:
        # count the characters of name only once
        nameCount = self.countChars(name)

        result = []
        minChanges = float("inf")

        for candidate in nameList:
            changes = self.countChanges(nameCount, candidate)

            if changes < minChanges:
                # new best found, older names are no longer the answer
                minChanges = changes
                result.clear()
            if changes == minChanges:
                result.append(candidate)

        return result

    def countChars(self, s: str) -> List[int]:
        """Return how many times each character appears in s.

        Letters have ASCII codes below 128, so ord(c) is the index.
        """
        count = [0] * 128
        for c in s:
            count[ord(c)] += 1
        return count

    def countChanges(self, nameCount: List[int], candidate: str) -> int:
        """Return the minimum number of replacements needed
        to turn candidate into an anagram of name.
        """
        # need[i] = how many more copies of character i we still need
        need = nameCount.copy()

        for c in candidate:
            i = ord(c)
            if need[i] > 0:
                need[i] -= 1  # this character is useful, keep it

        # every character that is still missing costs one change
        return sum(need)
```


### Complexity

Let `L` be the length of `name` and `N` be the number of names in `nameList`.

- **Time:** `O(N × L)`. Each candidate is scanned once. Copying and summing `need` adds only 128 steps per candidate. `N × L` is the total number of characters in `nameList`, which is at most `1,000,000`.
- **Space:** `O(1)` extra. The count lists always have 128 slots. The answer list is not counted.