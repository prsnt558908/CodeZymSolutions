# Maximize XOR Score After Adding X in Python

#### Problem Statement
[https://codezym.com/question/213-maximize-xor-score-after-adding-x](https://codezym.com/question/213-maximize-xor-score-after-adding-x)


## Core Idea

Every number in the list has only two choices: keep it as it is, or add `x` to it.

The score only adds up neighbor pairs. So when we move to the next number, the only thing we need from the past is whether the previous number was kept or changed.

This lets us walk the list once from left to right. At every number we remember just two values: the best score so far if this number is kept, and the best score so far if it gets `x`.

This "carry the best answer forward for each choice" idea is called dynamic programming. It runs in `O(n)` time and `O(1)` extra space.


---


## Understanding the Problem

Take `nums = [4, 2, 7]` and `x = 3`. Each index can take one of two values:

| Index | Keep | Add `x` |
|-------|------|---------|
| 0     | 4    | 7       |
| 1     | 2    | 5       |
| 2     | 7    | 10      |

We pick one value for every index and add up the XOR (`^`) of each neighbor pair.

Picking `[7, 5, 10]` gives `(7 ^ 5) + (5 ^ 10) = 2 + 15 = 17`, which is the best possible.

Adding `x` is not always good. The original list `[4, 2, 7]` scores `11`. If we add `x` only to index `1`, we get `[4, 5, 7]`, which scores just `3`.

So we have to compare choices, not just add `x` everywhere.


---


## Solution 1: Brute Force

The simplest idea is to try every possible set of indices.

A bitmask makes this easy. If bit `i` of `mask` is `1`, then index `i` gets `x`.

```python
n = len(nums)
best = 0
for mask in range(1 << n):
    # build the final list for this mask
    b = [nums[i] + (x if mask & (1 << i) else 0) for i in range(n)]

    # score of this final list
    score = sum(b[i] ^ b[i + 1] for i in range(n - 1))
    best = max(best, score)
```

### Why it is slow

There are `2^n` sets, and scoring each one takes `O(n)`. So the total work is `O(n * 2^n)`.

Even `n = 30` means more than a billion sets. With `n` up to `100,000`, this will never finish.


---


## Key Observation

Index `i` is part of at most two pairs: `(i - 1, i)` and `(i, i + 1)`.

Now think of all the ways to choose for indices `0` to `i`. If two of them end the same way at index `i`, the numbers after `i` can't tell them apart. So for each ending, we only need to remember the best score.

There are only two possible endings, which gives us two states:

- `keep_score`: best score of the list up to index `i`, when `nums[i]` is kept.
- `add_score`: best score of the list up to index `i`, when `nums[i]` gets `x`.


---


## Solution 2: Optimal (Two States, One Pass)

### The Transition

At index `i`, let:

- `prev` and `prev_with_x` be the two possible values of `nums[i - 1]`.
- `curr` and `curr_with_x` be the two possible values of `nums[i]`.

If `nums[i]` is kept, the previous number could be in either state. We take the better one:

```
new_keep_score = max(keep_score + (prev ^ curr),
                     add_score  + (prev_with_x ^ curr))
```

If `nums[i]` gets `x`, same idea:

```
new_add_score = max(keep_score + (prev ^ curr_with_x),
                    add_score  + (prev_with_x ^ curr_with_x))
```

**Start:** index `0` has no pair yet, so `keep_score = 0` and `add_score = 0`.

**Answer:** `max(keep_score, add_score)` after the last index.

### Why only two variables?

We could store a full table with two scores for every index. But each step only reads the step right before it.

So two variables are enough, and the extra space drops from `O(n)` to `O(1)`.

### Dry Run

`nums = [4, 2, 7]`, `x = 3`

**i = 0:** no pair yet, so `keep_score = 0` and `add_score = 0`.

**i = 1:** current number is `2` or `5`.

- `keep_score = max(0 + (4 ^ 2), 0 + (7 ^ 2)) = max(6, 5) = 6`
- `add_score = max(0 + (4 ^ 5), 0 + (7 ^ 5)) = max(1, 2) = 2`

**i = 2:** current number is `7` or `10`.

- `keep_score = max(6 + (2 ^ 7), 2 + (5 ^ 7)) = max(11, 4) = 11`
- `add_score = max(6 + (2 ^ 10), 2 + (5 ^ 10)) = max(14, 17) = 17`

**Answer:** `max(11, 17) = 17`

### Complexity

- **Time:** `O(n)`, each index is visited once.
- **Space:** `O(1)`, only a few variables.

### Python Code

```python
from typing import List


class MaximizeXorScore:

    def __init__(self):
        pass

    def maximizeXorScore(self, nums: List[int], x: int) -> int:
        """
        Returns the maximum sum of XOR of adjacent pairs, where x
        can be added to any set of indices (possibly none).
        """
        # Best scores for the list up to the current index.
        # At index 0 there is no pair yet, so both start at 0.
        keep_score = 0  # current number is kept as it is
        add_score = 0   # current number gets x added

        for i in range(1, len(nums)):
            # two possible values of the previous number
            prev = nums[i - 1]
            prev_with_x = prev + x

            # two possible values of the current number
            curr = nums[i]
            curr_with_x = curr + x

            # current number kept: previous one can be kept or changed
            new_keep_score = max(keep_score + (prev ^ curr),
                                 add_score + (prev_with_x ^ curr))

            # current number gets x: previous one can be kept or changed
            new_add_score = max(keep_score + (prev ^ curr_with_x),
                                add_score + (prev_with_x ^ curr_with_x))

            keep_score = new_keep_score
            add_score = new_add_score

        return max(keep_score, add_score)
```