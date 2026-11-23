# Strings That Have Two Letters Swapped in Python

#### Problem Statement
[https://codezym.com/question/277-strings-two-letters-swapped](https://codezym.com/question/277-strings-two-letters-swapped)


We go through `nameList` one string at a time and ask a simple question: can one swap turn this string into `name`?


A swap changes only two positions. So a matching string must differ from `name` at exactly two positions, and the two letters there must be the same as in `name`, only swapped.


We first try a brute force idea that tests every possible swap. It is easy to follow but very slow. Then we improve it to check each string in a single pass.


## When Does One Swap Work?

Let us compare the candidate `"makret"` with `name = "market"`.

| index | 0 | 1 | 2 | 3 | 4 | 5 |
|---|---|---|---|---|---|---|
| name | m | a | r | k | e | t |
| candidate | m | a | **k** | **r** | e | t |


The strings differ only at positions 2 and 3.


The candidate has `k` at position 2, which is what `name` has at position 3. It has `r` at position 3, which is what `name` has at position 2. The letters are crossed, so swapping them gives `"market"`.


So a candidate is a match only when all three of these are true:

1. It has the same length as `name`.
2. It differs from `name` at exactly two positions, say `i` and `j`.
3. The letters are crossed: `candidate[i] == name[j]` and `candidate[j] == name[i]`.


Why exactly two differences?

- **0 differences:** the string is already equal to `name`. The rules say this is not a match.
- **1 difference:** the correct letter would have to come from another position. But every other position is already correct, so taking a letter from there breaks it.
- **3 or more differences:** one swap can fix at most two positions.


## Approach 1: Brute Force (Try Every Swap)

For each candidate, the main method calls a helper `isOneSwapAway(name, candidate)` and keeps the candidate if it returns `True`.


In the brute force version, the helper tries every pair of positions, swaps them, and checks if the result equals `name`.

```python
def isOneSwapAway(self, name: str, candidate: str) -> bool:
    if len(name) != len(candidate):
        return False

    chars = list(candidate)
    n = len(chars)

    # try every pair of positions (i, j)
    for i in range(n):
        for j in range(i + 1, n):
            # swapping two equal letters changes nothing, so it does not count
            if chars[i] == chars[j]:
                continue

            self.swap(chars, i, j)
            if "".join(chars) == name:
                return True
            self.swap(chars, i, j)  # undo before trying the next pair
    return False

def swap(self, chars: List[str], i: int, j: int) -> None:
    chars[i], chars[j] = chars[j], chars[i]
```


### Why is this slow?

A string of length `n` has about `n * n / 2` pairs of positions, and each comparison with `name` takes `n` steps. That is `O(n^3)` work for a single candidate.


With `n = 100,000` this is around `5 * 10^14` steps, which is far too slow.


Most of this work is wasted. We keep trying swaps at positions where the letters already match `name`.


## Approach 2: Single Pass Comparison (Optimal)

We don't need to try any swaps. The rules above already tell us what a match looks like, so we only need to find **where** the candidate and `name` differ.


The main method stays the same. Only `isOneSwapAway` changes:

1. If the lengths are different, return `False`.
2. Walk through both strings together. Whenever the letters at a position differ, add that position to a small list called `diff`.
3. If `diff` gets a third position, stop right away. One swap can't fix three positions.
4. At the end, return `True` only if `diff` has exactly two positions and their letters are crossed.


We use a list for `diff` because it is the simplest way to remember where the strings differ. It never holds more than three numbers, so it costs almost nothing.


The `result` list collects matches while we go through `nameList` from left to right. So the original order is kept, and a repeated match is added every time it appears.


### Walkthrough

For `name = "market"`:

| candidate | positions that differ | result |
|---|---|---|
| `makret` | 2, 3 (letters crossed) | match |
| `market` | none | not a match |
| `masket` | 2 only | not a match |
| `markte` | 4, 5 (letters crossed) | match |
| `planet` | 0, 1, 2 (stop at the third) | not a match |

Output: `["makret", "markte"]`


### Complexity

- **Time:** `O(T)`, where `T` is the total number of characters in `nameList`. Each candidate is scanned at most once, and only when it has the same length as `name`.
- **Space:** `O(1)` extra, not counting the output list. The `diff` list never holds more than three positions.


### Python Code

```python
from typing import List


class StringsThatHaveTwoLettersSwapped:
    def __init__(self):
        pass

    def findNamesWithTwoLettersSwapped(self, name: str, nameList: List[str]) -> List[str]:
        """
        Returns every string from nameList that becomes equal to name
        after swapping exactly two of its characters.
        Matches keep their original order, and duplicates are kept too.
        """
        result = []
        for candidate in nameList:
            if self.isOneSwapAway(name, candidate):
                result.append(candidate)
        return result

    def isOneSwapAway(self, name: str, candidate: str) -> bool:
        """
        Checks if one swap of two characters turns candidate into name.
        Compares both strings position by position and stops early
        as soon as a third difference is found.
        """
        if len(name) != len(candidate):
            return False

        # positions where name and candidate have different characters
        diff = []
        for i in range(len(name)):
            if name[i] != candidate[i]:
                diff.append(i)
                # one swap can fix at most two positions
                if len(diff) > 2:
                    return False

        # 0 differences means already equal, 1 difference can't be fixed by a swap
        if len(diff) != 2:
            return False

        first, second = diff[0], diff[1]
        # the two different characters must be crossed over
        return (candidate[first] == name[second]
                and candidate[second] == name[first])
```