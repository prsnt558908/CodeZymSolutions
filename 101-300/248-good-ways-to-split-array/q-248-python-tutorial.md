# Good Ways to Split an Array in 3 Contiguous Parts in Python

#### Problem Statement
[https://codezym.com/question/248-good-ways-to-split-array](https://codezym.com/question/248-good-ways-to-split-array)


## Core Idea

The rule `S2 <= S1 + S3` looks like it needs all three sums. It really depends only on the middle one.


`S1 + S2 + S3` is always the sum of the whole list. So the rule simply says: **the middle part can be at most half of the total**.


This means we can forget about the first and last parts and just count the good middle parts.


All numbers are non-negative. So the sum of a middle part never goes down when it grows, and never goes up when it shrinks. That is exactly what a **sliding window** needs, and it lets us count every good middle part in a single pass.


## Making the Rule Simpler

Let `total` be the sum of all numbers. Every number belongs to exactly one part, so:

```
S1 + S2 + S3 = total
S1 + S3      = total - S2
```

Put this into the rule:

```
S2     <= S1 + S3
S2     <= total - S2
2 * S2 <= total
```

So a split is good when **twice the middle sum is not more than the total**.


We compare `2 * S2` with `total` instead of comparing `S2` with `total / 2`. This keeps everything in whole numbers.


## Only the Middle Part Matters

Once we pick where the middle part starts and ends, the whole split is fixed. `A1` is everything before it, and `A3` is everything after it.


`A1` needs at least one number, so the middle part can't start at index `0`.


`A3` needs at least one number, so the middle part can't end at index `n - 1`.


So the question becomes:

> Count the contiguous pieces inside `nums[1 .. n-2]` whose sum `s` satisfies `2 * s <= total`.


For Example 1, `nums = [1, 2, 1, 2]` and `total = 6`. The middle part must come from `[2, 1]`. The pieces `[2]`, `[2, 1]` and `[1]` all pass, so the answer is `3`.


## Approach 1: Brute Force

Try every start of the middle part. Then grow its end one step at a time and keep a running sum.

```python
count = 0
for start in range(1, n - 1):
    middle_sum = 0
    for end in range(start, n - 1):
        middle_sum += nums[end]
        if 2 * middle_sum <= total:
            count += 1
```

This is correct, but it checks every middle part one by one. That is `O(n^2)` time.


For `n = 100,000` there are about **5 billion** middle parts. That is far too slow.


We need a way to count many good middle parts at once.


## Approach 2: Sliding Window (Optimal)

Because every number is `>= 0`, two simple facts are always true:

1. Adding a number to a window never makes its sum smaller.
2. Removing a number from a window never makes its sum bigger.


We keep a window `nums[left .. right]` and its sum in `window_sum`. For every `right` from `1` to `n - 2`:

- Add `nums[right]` to `window_sum`.
- While `2 * window_sum > total`, the window is too big. Remove `nums[left]` and move `left` forward.
- Now `nums[left .. right]` is good. By fact 2, every shorter window that ends at `right` is good too. That is `right - left + 1` good middle parts, and we add them all at once.


**Why `left` never moves back:** a start that was too big for `right` is still too big for `right + 1` (fact 1). So `left` only moves forward, and we never miss a good middle part.


If even `nums[right]` alone is too big, `left` moves past `right`. The window becomes empty and we add `0`.


We don't need a prefix sum array or any extra list. The window only grows on the right and shrinks on the left, so one running sum always tells us the window's sum.


### Dry Run

`nums = [3, 1, 6, 1, 2, 3]`, so `total = 16`. The middle part must come from index `1` to `4`, which is `[1, 6, 1, 2]`.

| right | Window after shrinking | Window sum | Good middle parts ending here |
|-------|------------------------|------------|-------------------------------|
| 1     | [1]                    | 1          | 1                             |
| 2     | [1, 6]                 | 7          | 2                             |
| 3     | [1, 6, 1]              | 8          | 3                             |
| 4     | [1, 2]                 | 3          | 2                             |

At `right = 4`, the window `[1, 6, 1, 2]` has sum `10`, and `2 * 10 > 16`. We drop `1` (sum `9`, still too big) and then `6` (sum `3`, good).


Answer = `1 + 2 + 3 + 2 = 8`.


### About the Modulo

The count can reach about 5 billion. Python integers never overflow, so we just keep adding and take the modulo once, at the end.


### Complexity

- **Time:** `O(n)`. Each index enters the window once and leaves it at most once.
- **Space:** `O(1)`. We only use a few variables.


## Python Code

```python
from typing import List


class GoodWaysToSplitArray:

    def __init__(self):
        self.MOD = 1_000_000_007

    def countGoodSplits(self, nums: List[int]) -> int:
        """
        Counts the ways to split nums into [A1][A2][A3] with S2 <= S1 + S3.

        Since S1 + S2 + S3 = total, the rule is the same as 2 * S2 <= total.
        So we only count middle parts nums[left..right], with
        1 <= left <= right <= n - 2, whose sum is at most half of the total.
        """
        n = len(nums)
        total = sum(nums)

        count = 0
        window_sum = 0  # sum of the middle part nums[left..right]
        left = 1        # middle part starts at 1 or later, so A1 is never empty

        # right goes from 1 to n - 2, so A3 is never empty
        for right in range(1, n - 1):
            window_sum += nums[right]

            # Middle part is too big: drop numbers from its left side
            while 2 * window_sum > total:
                window_sum -= nums[left]
                left += 1

            # Every middle part that ends at 'right' and starts
            # anywhere from 'left' to 'right' is good
            count += right - left + 1

        return count % self.MOD
```