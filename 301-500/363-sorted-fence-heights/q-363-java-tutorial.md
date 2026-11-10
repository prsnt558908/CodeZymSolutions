# Sorted Fence Heights in Java

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

```java
int n = fenceHeights.size();
long[] heights = new long[n];
for (int i = 0; i < n; i++) {
    heights[i] = fenceHeights.get(i);
}

long totalCost = 0;
for (int i = 1; i < n; i++) {
    if (heights[i] < heights[i - 1]) {
        long gap = heights[i - 1] - heights[i];
        totalCost += gap;

        // raise every section from i to the end by gap
        for (int j = i; j < n; j++) {
            heights[j] += gap;
        }
    }
}
return totalCost;
```

We copy the heights into a `long` array because the raised heights can grow beyond the `int` range.

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

We keep the answer in a `long`. A zig-zag fence can have 50,000 drops of almost 1,000,000,000 each. That adds up to about `5 * 10^13`, far too big for an `int`.

**Complexity:** `O(n)` time and `O(1)` extra space.

### Java Code

```java
import java.util.*;

public class MinimumFenceIncrease {

    public MinimumFenceIncrease() {
    }

    /**
     * Returns the minimum total cost to make the fence non-decreasing.
     * Each drop (a section shorter than its left neighbour) is fixed by
     * raising that section and everything after it by the drop size,
     * so the answer is the sum of all drops.
     */
    public long minimumFenceIncrease(List<Integer> fenceHeights) {
        long totalCost = 0;

        for (int i = 1; i < fenceHeights.size(); i++) {
            int left = fenceHeights.get(i - 1);
            int right = fenceHeights.get(i);

            // this drop costs exactly its size to fix
            if (left > right) {
                totalCost += left - right;
            }
        }
        return totalCost;
    }
}
```