# Check If Array Can Converge To Same Value - FollowUp in Python

#### Problem Statement
[https://codezym.com/question/244-check-if-array-can-converge-followup](https://codezym.com/question/244-check-if-array-can-converge-followup)

---

Each number can move up or down by at most `x`. So a number `num` can become any value in the range `[num - x, num + x]`.


We need one value `y` that lies inside **all** of these ranges at the same time.


We never have to search for `y` directly. Only two numbers decide the answer: the smallest and the largest. The answer is `True` exactly when `largest - smallest <= 2 * x`. Finding these two numbers takes **O(n)** time and **O(1)** extra space.

---

## Approach 1: Brute Force (Try Every `y`)

`y` must be reachable from the first number too. So `y` can only be a value from `first - x` to `first + x`, where `first` is the first number in the list.


We try each of these values and check if every number is within distance `x` of it.

```python
first = nums[0]

# Try every value that the first number can reach
for y in range(first - x, first + x + 1):
    if all(abs(num - y) <= x for num in nums):
        return True

return False
```

### Why this is slow

There are `2x + 1` possible values of `y`, and for each one we scan all `n` numbers. That is **O(n * x)** time.


With `x` up to 1,000,000 and `n` up to 100,000, this is about 200 billion steps in the worst case. Far too slow.


We are also doing extra work. The problem only asks **if** such a `y` exists. It never asks **which** one.

---

## Approach 2: Overlapping Ranges (Optimal)

### Think in ranges, not values

Instead of testing values of `y` one by one, look at what each number can reach.


A number `num` can reach every value in `[num - x, num + x]`. So the real question is: **do all these ranges overlap at some point?**

### Where do all the ranges overlap?

The common part of many ranges starts at the **biggest start** and ends at the **smallest end**.

- **Biggest start** comes from the largest number: `largest - x`
- **Smallest end** comes from the smallest number: `smallest + x`

So the overlap of all ranges is `[largest - x, smallest + x]`.


Numbers in the middle cannot shrink this overlap. Their ranges start at or before `largest - x` and end at or after `smallest + x`, so they fully cover it.


This is why we don't need a list, set or dictionary. Two variables, `smallest` and `largest`, hold everything we need.

### When does a common `y` exist?

The overlap has at least one value when its start is not after its end:

```
largest - x <= smallest + x
```

This is the same as `largest - smallest <= 2 * x`. In simple words, the smallest and the largest numbers must be able to meet somewhere in the middle.

### Is `y` always an integer?

Yes. All numbers and `x` are integers, so both ends of the overlap are integers too. If the overlap is not empty, then `y = largest - x` is a valid answer.

---

## Dry Run

### Example 3: `nums = [10, 14, 12]`, `x = 2`

```
          8   9  10  11  12  13  14  15  16
10  ->    [===============]
14  ->                    [===============]
12  ->            [===============]
                          ^
                       y = 12
```

- Overlap start = `largest - x` = 14 - 2 = **12**
- Overlap end = `smallest + x` = 10 + 2 = **12**


`12 <= 12`, so the answer is `True`. All three ranges meet at `y = 12`.

### Example 4: `nums = [1, 8, 4]`, `x = 2`

```
         -1   0   1   2   3   4   5   6   7   8   9  10
 1  ->    [===============]
 8  ->                                [===============]
 4  ->                [===============]
```

- Overlap start = `largest - x` = 8 - 2 = **6**
- Overlap end = `smallest + x` = 1 + 2 = **3**


`6 > 3`, so the answer is `False`. The ranges of `1` and `8` never touch, so no common value exists.

---

## Complexity

- **Time:** O(n). `min` and `max` each scan the list once.
- **Space:** O(1). Only a few extra variables.

---

## Python Code

```python
from typing import List


class ArrayEquivalenceChecker:

    def __init__(self):
        pass

    def canMakeEquivalent(self, nums: List[int], x: int) -> bool:
        """
        Returns True if all numbers can be changed to one common value y,
        where each number can move up or down by at most x.
        """
        smallest = min(nums)
        largest = max(nums)

        # Each number can reach [num - x, num + x].
        # The overlap of all these ranges is [largest - x, smallest + x].
        overlap_start = largest - x
        overlap_end = smallest + x

        # A common value y exists only if the overlap is not empty
        return overlap_start <= overlap_end
```