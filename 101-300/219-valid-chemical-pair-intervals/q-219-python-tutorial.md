# Biological Hazards: Valid Chemical Pair Intervals in Python

#### Problem Statement
[https://codezym.com/question/219-valid-chemical-pair-intervals](https://codezym.com/question/219-valid-chemical-pair-intervals)


## Core Idea

We need to count intervals `[L, R]` that never hold both chemicals of a forbidden pair.


The key fact is simple: **if an interval is safe, every smaller interval inside it is also safe.**


So we move a right pointer `right` from `1` to `n`, and keep a left pointer `left` at the smallest left end that keeps `[left, right]` safe. Every interval from `[left, right]` to `[right, right]` is then safe, so each step adds `right - left + 1` to the answer.


As `right` grows, `left` only moves forward. This makes it a simple **sliding window** (two pointers) solution that runs in `O(n + m)` time, where `m` is the number of forbidden pairs.


## Solution 1: Brute Force

Try every interval and check it against every forbidden pair.

```python
count = 0
for left in range(1, n + 1):
    for right in range(left, n + 1):
        safe = True
        for a, b in zip(poisonous, allergic):
            small, big = min(a, b), max(a, b)
            # this interval holds both chemicals of the pair
            if left <= small and big <= right:
                safe = False
                break
        if safe:
            count += 1
return count
```


This is correct, but it takes `O(n² * m)` time. With `n` and `m` up to `200,000`, it is far too slow.


It wastes work in two ways:

1. It checks intervals one by one, even though safe intervals come in whole blocks that we can count at once.
2. It rescans every pair for every interval, even though adding one chemical can only create danger through that chemical's own pairs.


## Solution 2: Sliding Window (Optimal)

### Why `left` only moves forward

Adding a chemical to the right end of a window can only add danger, never remove it. So a left end that was unsafe for `right` stays unsafe for `right + 1`.


This means `left` never has to move back. One pointer that only moves forward is enough.


### What can break the window

When chemical `right` joins, the old window `[left, right - 1]` was already safe. The only new danger is a pair made of `right` and some chemical on its left.


If `right` has many such partners, only the **closest** one (the largest) matters. Pushing `left` past the closest partner also pushes it past all the farther ones.


So the whole update is one line:

```python
left = max(left, closest_partner[right] + 1)
```

If the closest partner is already outside the window, `left` stays where it is.


### Why a plain list

`closest_partner[x]` holds the largest chemical smaller than `x` that cannot be with `x`. It is `0` when there is no such chemical.


Chemicals are labeled `1` to `n`, so a plain list indexed by label gives instant lookup. We could keep a full list of partners for each chemical, but we only ever use the closest one, so one number per chemical is enough.


### Dry Run

Example 3: `n = 6`, forbidden pairs `(1, 2)`, `(3, 5)`, `(4, 6)`.


This gives `closest_partner[2] = 1`, `closest_partner[5] = 3` and `closest_partner[6] = 4`. All others are `0`.

| right | closest_partner[right] | left | safe intervals ending at right | total |
|:---:|:---:|:---:|---|:---:|
| 1 | 0 | 1 | [1] | 1 |
| 2 | 1 | 2 | [2] | 2 |
| 3 | 0 | 2 | [2, 3], [3] | 4 |
| 4 | 0 | 2 | [2, 4], [3, 4], [4] | 7 |
| 5 | 3 | 4 | [4, 5], [5] | 9 |
| 6 | 4 | 5 | [5, 6], [6] | 11 |

The answer is `11`.


### Edge Cases

- A pair can come in any order, like `(5, 3)`. So we use `min` and `max` to get its `small` and `big` chemical.
- The same pair can appear more than once. Taking the max again changes nothing, so no extra work is needed.


### Python Code

```python
from typing import List


class BiologicalHazards:

    def __init__(self):
        pass

    def countValidIntervals(self, n: int, poisonous: List[int], allergic: List[int]) -> int:
        """
        Counts intervals [L, R] that do not hold both chemicals of any forbidden pair.
        Sliding window: for every right end, keep the smallest safe left end.
        """
        # closest_partner[x] = largest chemical smaller than x that cannot be with x
        # 0 means x has no such partner
        closest_partner = [0] * (n + 1)

        for a, b in zip(poisonous, allergic):
            small, big = min(a, b), max(a, b)
            closest_partner[big] = max(closest_partner[big], small)

        count = 0
        left = 1  # smallest left end that keeps [left, right] safe

        for right in range(1, n + 1):
            # if the closest partner of 'right' is inside the window, move left just past it
            left = max(left, closest_partner[right] + 1)

            # [left, right], [left + 1, right], ..., [right, right] are all safe
            count += right - left + 1

        return count
```


### Complexity

- **Time:** `O(n + m)`, one pass over the pairs and one pass over the chemicals.
- **Space:** `O(n)` for the `closest_partner` list.