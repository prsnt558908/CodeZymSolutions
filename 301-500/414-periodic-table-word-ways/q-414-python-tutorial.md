# Periodic Table Word Ways in Python

#### Problem Statement
[https://codezym.com/question/414-periodic-table-word-ways](https://codezym.com/question/414-periodic-table-word-ways)


## Core Idea

We need to count how many ways a word can be built by joining chemical element symbols, ignoring letter case.


Every symbol has 1 or 2 letters. So from any position in the word, the next symbol uses either the next 1 letter or the next 2 letters. This is just like the classic **climbing stairs** problem, where you climb 1 or 2 steps at a time. The only twist: a step is allowed only when those letters form a symbol.


We keep all symbols in a set to check matches quickly. Then we use **dynamic programming**, so the answer for each position is found only once and reused.


Also, no two symbols are the same when case is ignored. So a piece of the word can match at most one symbol, and every valid way to cut the word gives exactly one sequence of symbols. Counting the valid cuts is enough.


## Brute Force: Try Every Split

Start at the first letter and try both options:

- If the next 1 letter is a symbol, pick it and count the ways for the rest of the word.
- If the next 2 letters are a symbol, pick them and count the ways for the rest of the word.


When we reach the end of the word, we have found one complete way.

```python
# symbols holds every symbol in lower case
# first call: self.countFrom(word.lower(), 0, symbols)
def countFrom(self, text: str, index: int, symbols: set) -> int:
    if index == len(text):
        return 1  # the whole word is formed
    total = 0
    # next symbol uses one letter
    if text[index] in symbols:
        total += self.countFrom(text, index + 1, symbols)
    # next symbol uses two letters
    if index + 2 <= len(text) and text[index:index + 2] in symbols:
        total += self.countFrom(text, index + 2, symbols)
    return total
```


### Why is this slow?

The same position gets solved again and again.


In `"CoCo"`, we reach index 2 once by picking `C`, `O` and once more by picking `Co`. Both times, the ways for the remaining `"Co"` are counted from scratch.


For long words this repeated work explodes. Take symbols `C`, `S`, `Cs`, `Sc` and a word made of `"CS"` repeated 45 times (90 letters). Almost every position allows both a 1 letter and a 2 letter symbol, so there are about 4.66 × 10<sup>18</sup> ways. Brute force walks through each of them one by one.


**Time:** exponential, up to O(2<sup>n</sup>), where n is the length of the word.


## Dynamic Programming Solution

The number of ways to finish the word from a position does not depend on how we reached that position. So we find it once and store it.


Let `ways[i]` be the number of ways to form the part of the word that starts at index `i`.

- `ways[n] = 1`. Nothing is left to form, so there is exactly one way.
- If the letter at `i` is a symbol, add `ways[i + 1]`.
- If the two letters at `i` and `i + 1` form a symbol, add `ways[i + 2]`.


`ways[i]` only needs values to its right. So we fill the list from the end to the start. The answer is `ways[0]`.


### Why these data structures?

- **`symbols` (a set)**: we keep asking "is this piece of the word a symbol?". A set answers that in O(1). We store the symbols in lower case and lower case the word too, so `"Co"`, `"co"` and `"CO"` all match.
- **`ways` (a list)**: one count per position. Python integers never overflow, so even the biggest answer, about 4.66 × 10<sup>18</sup>, needs no special care.


### Walkthrough

Symbols `C`, `O`, `Co` and word `"CoCo"`, which becomes `"coco"`. We go from `i = 4` down to `i = 0`:

| i | Rest of word | 1 letter | 2 letters | ways[i] |
|---|---|---|---|---|
| 4 | (empty) | | | 1 |
| 3 | `o` | `o` found, add ways[4] = 1 | | 1 |
| 2 | `co` | `c` found, add ways[3] = 1 | `co` found, add ways[4] = 1 | 2 |
| 1 | `oco` | `o` found, add ways[2] = 2 | `oc` not found | 2 |
| 0 | `coco` | `c` found, add ways[1] = 2 | `co` found, add ways[2] = 2 | 4 |


The answer is `ways[0] = 4`.


### Python Code

```python
from typing import List


class PeriodicTableWordCounter:

    def __init__(self):
        pass

    def countWaysToFormWord(self, elementSymbols: List[str], word: str) -> int:
        """
        Counts the different sequences of element symbols that join
        together to form the word. Letter case is ignored.
        """
        # keep symbols in lower case, so "Co", "co" and "CO" all become "co"
        symbols = set()
        for symbol in elementSymbols:
            symbols.add(symbol.lower())

        text = word.lower()
        n = len(text)

        # ways[i] = number of ways to form the part of the word starting at index i
        ways = [0] * (n + 1)
        ways[n] = 1  # nothing left to form, so exactly one way

        # fill from right to left, since ways[i] needs ways[i + 1] and ways[i + 2]
        for i in range(n - 1, -1, -1):
            # next symbol uses one letter
            if text[i] in symbols:
                ways[i] += ways[i + 1]
            # next symbol uses two letters
            if i + 2 <= n and text[i:i + 2] in symbols:
                ways[i] += ways[i + 2]

        return ways[0]
```


### Complexity

- **Time:** O(m + n), where m is the number of symbols and n is the length of the word.
- **Space:** O(m + n) for the set and the `ways` list.


## Space Optimized Solution

`ways[i]` only looks at `ways[i + 1]` and `ways[i + 2]`. So instead of the whole list, we keep just these two values in `one_ahead` and `two_ahead`, and shift them one step left after each position.

```python
from typing import List


class PeriodicTableWordCounter:

    def __init__(self):
        pass

    def countWaysToFormWord(self, elementSymbols: List[str], word: str) -> int:
        """
        Same idea as the list version, but keeps only the two
        values that each step needs.
        """
        # keep symbols in lower case, so matching ignores case
        symbols = set()
        for symbol in elementSymbols:
            symbols.add(symbol.lower())

        text = word.lower()
        n = len(text)

        one_ahead = 1  # ways[i + 1], starts as ways[n] = 1
        two_ahead = 0  # ways[i + 2], only used when i + 2 <= n

        for i in range(n - 1, -1, -1):
            current = 0
            # next symbol uses one letter
            if text[i] in symbols:
                current += one_ahead
            # next symbol uses two letters
            if i + 2 <= n and text[i:i + 2] in symbols:
                current += two_ahead
            # move one step to the left
            two_ahead = one_ahead
            one_ahead = current

        return one_ahead
```


### Complexity

- **Time:** O(m + n)
- **Space:** O(m), only the set remains.