# Sorted Fence Heights in Python

#### Problem Statement
[https://codezym.com/question/363-sorted-fence-heights](https://codezym.com/question/363-sorted-fence-heights)

A fence is sorted when no section is shorter than the section on its left. So the only places we need to fix are the **drops**, the spots where the height goes down.

An operation costs `x` no matter how many sections it covers. So when we find a drop, the smart move is to raise the shorter section **and everything after it** by the size of the drop. This fixes the drop and never creates a new one.

This plan pays exactly the size of each drop, and we will see that no plan can pay less. So the answer is simply the **sum of all drops**.

We will start with a slow simulation of this idea, and then turn it into a single pass `O(n)` solution.

## What One Operation Does

Say we raise sections `l` to `r` by `x`. Here is what happens to each pair of neighbours:

- Pairs inside the group keep the same gap, because both sections go up by `x`.
- The pair at the left edge, sections `l - 1` and `l`, gets better by `x`.
- The pair at the right edge, sections `r` and `r + 1`, gets worse by `x`.

A longer group costs the same as a shorter one. So if we always stretch the group to the **last section**, there is no right edge, and the operation can only help.

## Basic Solution: Simulate the Raises

Walk from left to right. When section `i` is shorter than section `i - 1`, raise every section from `i` to the end by the gap, and add the gap to the cost.

Now section `i` is as tall as section `i - 1`, and no pair to its right got worse. So the fence is sorted up to `i`, and we move on.

```python
heights = list(fenceHeights)
n = len(heights)
total_cost = 0

for i in range(1, n):
    if heights[i] < heights[i - 1]:
        gap = heights[i - 1] - heights[i]
        total_cost += gap

        # raise every section from i to the end by gap
        for j in range(i, n):
            heights[j] += gap

return total_cost
```

We work on a copy of the list, so the caller's heights stay unchanged.

Here is Example 1, `[4, 1, 3, 2]`:

| Step | Heights | Total Cost |
|---|---|---|
| Start | `[4, 1, 3, 2]` | 0 |
| Drop of 3 at index 1: raise indices 1 to 3 by 3 | `[4, 4, 6, 5]` | 3 |
| Drop of 1 at index 3: raise index 3 by 1 | `[4, 4, 6, 6]` | 4 |

**Problem:** every fix walks over the rest of the fence. A zig-zag fence like `[2, 1, 2, 1, ...]` with 100,000 sections has 50,000 drops, and each fix can touch tens of thousands of sections. That is billions of steps, so this `O(n^2)` approach is too slow.

## Optimal Solution: Add Up the Drops

The inner loop adds the **same** value to every section from `i` to the end. As we saw above, that never changes the gap between two neighbours inside that part.

So the drop we find at index `i` in the raised fence is exactly the drop at index `i` in the original fence. We can skip the raising completely and just add up every drop in the original heights.

**Why is this the minimum?** An operation of cost `x` can help at most one pair of neighbours, the one at its left edge, and only by `x`. A drop of size `d` needs at least `d` worth of help. So no plan can cost less than the sum of all drops, and our plan costs exactly that.

For Example 3, `[9, 7, 4, 1]`, the drops are `2`, `3` and `3`, so the answer is `8`.

The total can get very large, about `5 * 10^13` for a tall zig-zag fence. Python integers have no size limit, so a plain `int` is enough.

**Complexity:** `O(n)` time and `O(1)` extra space.

### Python Code

```python
from typing import List


class MinimumFenceIncrease:

    def __init__(self):
        pass

    def minimumFenceIncrease(self, fenceHeights: List[int]) -> int:
        """
        Returns the minimum total cost to make the fence non-decreasing.
        Each drop (a section shorter than its left neighbour) is fixed by
        raising that section and everything after it by the drop size,
        so the answer is the sum of all drops.
        """
        total_cost = 0

        for i in range(1, len(fenceHeights)):
            left = fenceHeights[i - 1]
            right = fenceHeights[i]

            # this drop costs exactly its size to fix
            if left > right:
                total_cost += left - right

        return total_cost
```