# Equal Sum Subsets with K Changes in Java

#### Problem Statement
[https://codezym.com/question/146-equal-sum-subsets-k-changes](https://codezym.com/question/146-equal-sum-subsets-k-changes)


## Core Idea

Both methods in this problem come down to one number: the **smallest gap**. Split the numbers into two groups and look at how far apart their sums are.

Part 1 asks if this gap can be `0`. The follow-up asks if we can close the gap with at most `k` changes. Each change closes the gap by exactly `1`, so it simply asks if the smallest gap is at most `k`.

To find the smallest gap, we use the classic **subset sum** idea: a boolean array that tells us which group sums are possible. The possible sum closest to half of the total gives the smallest gap.


## Step 1: Understand the Gap

Let `total` be the sum of all numbers. If the lighter group has sum `s`, the other group has sum `total - s`.

So the gap between them is:

```
gap = (total - s) - s = total - 2 * s
```

The gap depends only on `s`. The closer `s` is to `total / 2`, the smaller the gap.


## Step 2: What Does One Change Do?

Say the heavier group has sum `a` and the lighter group has sum `b`. The gap is `a - b`.

- Add `1` to any number in the lighter group: `b` goes up by `1`, so the gap shrinks by `1`.
- Subtract `1` from a number in the heavier group: `a` goes down by `1`, so the gap shrinks by `1`.

A change is always exactly `+1` or `-1`, so it can never close more than `1` of the gap. Each number can be changed only once. So a gap of `g` needs `g` changes, on `g` different numbers.

We want the split that needs the fewest changes, and that is the split with the smallest gap. So the follow-up becomes:

**Is the smallest gap at most `k`?**

This also means we never have to try the changes one by one.


### What about the "stay positive" rule?

We cannot subtract `1` from a `1`, because the value must stay positive. Adding `1` is always fine. Luckily, in the best split this rule never blocks us.

**Gap is 1:** we need only one change. Add `1` to any number in the lighter group.

**Gap is 2 or more:** the heavier group cannot contain a `1`. If it did, moving that `1` to the lighter group would shrink the gap by `2`, giving a better split than the best one.

So every number in the heavier group is at least `2` and can go down by `1`, and every number in the lighter group can go up by `1`. Every number can be changed, and since `gap <= k <= nums.size()`, there are always enough of them.


## Solution 1: Brute Force

The simplest way to find the smallest gap is to try every split. Each number goes either into group A or into group B.

```java
// Try every way to place each number in group A or group B.
// Returns the smallest gap between two non-empty groups.
int tryAllSplits(List<Integer> nums, int index, int sumA, int sumB) {
    if (index == nums.size()) {
        if (sumA == 0 || sumB == 0) {
            return Integer.MAX_VALUE; // a group is empty, not allowed
        }
        return Math.abs(sumA - sumB);
    }
    int num = nums.get(index);
    int putInA = tryAllSplits(nums, index + 1, sumA + num, sumB);
    int putInB = tryAllSplits(nums, index + 1, sumA, sumB + num);
    return Math.min(putInA, putInB);
}
```

Part 1 returns `tryAllSplits(nums, 0, 0, 0) == 0` and the follow-up returns `tryAllSplits(nums, 0, 0, 0) <= k`.

**The problem:** with `n` numbers there are `2^n` splits. For `n = 200` that is far too many.


## Solution 2: Subset Sum DP (Optimal)

Many different splits give the **same** group sum. We do not care which numbers are in a group, only what they add up to.

Every number is at most `100` and there are at most `200` numbers, so `total` is at most `20000`. A group sum is always a whole number between `0` and `20000`, which is tiny compared to `2^200` splits.

So instead of trying splits, we track **which sums are possible**.


### The `canMake` array

`canMake[s]` is `true` if some group of numbers adds up to exactly `s`.

A plain boolean array is all we need, because sums are small whole numbers and each sum is simply an index.

We only need sums from `0` to `total / 2`. We only look at the lighter group, and its sum is never more than half.

- Start with `canMake[0] = true`. The empty group has sum `0`.
- For each number `num`: if we could make `s - num` before, now we can also make `s` by adding `num`.

```java
for (int num : nums) {
    for (int s = half; s >= num; s--) {
        if (canMake[s - num]) {
            canMake[s] = true;
        }
    }
}
```


### Why go from high to low?

Each number can be used only once. If we loop from low to high, a number can get used twice in the same round.

For example, say we are adding the number `3` and only `canMake[0]` is `true` so far. Going up, we first set `canMake[3] = true`. Then, in the same round, `canMake[6]` looks at `canMake[3]` and also becomes `true`. That means we used the same `3` twice.

Going from high to low, `canMake[6]` is checked before `canMake[3]` changes, so this cannot happen.


### Finding the smallest gap

Walk down from `half` and stop at the first `s` where `canMake[s]` is `true`. That is the possible sum closest to half, so:

```
smallest gap = total - 2 * s
```

We stop at `s = 1`, not `0`, because an empty group is not allowed. Since there are at least two numbers, the smallest one is always at most half of the total, so we always find some `s >= 1`.

If `total` is odd, the gap is odd too, so it can never be `0`. The code handles this on its own.

Finally:

- `canDivideIntoEqualParts` returns `smallestGap == 0`
- `canDivideIntoEqualPartsWithKChanges` returns `smallestGap <= k`


## Walkthrough

**Example 2:** `canDivideIntoEqualParts([2, 3, 7])`

- `total = 12`, `half = 6`
- Sums we can make up to `6`: `0, 2, 3, 5`
- Closest to half is `5`, so the smallest gap is `12 - 2 * 5 = 2`
- The gap is not `0`, so the answer is `false`

**Example 3:** `canDivideIntoEqualPartsWithKChanges([1, 2, 4, 6], k = 1)`

- `total = 13`, `half = 6`
- Sums we can make up to `6`: `0, 1, 2, 3, 4, 5, 6`
- Closest to half is `6`, for example `[2, 4]`
- The smallest gap is `13 - 2 * 6 = 1`, and `1 <= k`, so the answer is `true`
- Groups are `[2, 4]` (sum 6) and `[1, 6]` (sum 7). Change `4` to `5` and both groups add up to `7`


## Complexity

- **Time:** `O(n * total)`. In the worst case that is about `200 * 10000 = 2,000,000` simple steps.
- **Space:** `O(total)` for the `canMake` array.

Here `n` is the number of elements and `total` is their sum.


## Final Java Code

```java
import java.util.*;

public class EqualSumSubsetsWithKChanges {

    public EqualSumSubsetsWithKChanges() {

    }

    /**
     * Part 1: can nums be split into two non-empty groups with the same sum?
     * This is possible only when the smallest gap is 0.
     */
    public boolean canDivideIntoEqualParts(List<Integer> nums) {
        return getSmallestGap(nums) == 0;
    }

    /**
     * Part 2: we may first change at most k numbers by +1 or -1.
     * Each change closes the gap by exactly 1, so we need gap <= k.
     */
    public boolean canDivideIntoEqualPartsWithKChanges(List<Integer> nums, int k) {
        return getSmallestGap(nums) <= k;
    }

    /**
     * Returns the smallest possible difference between the sums of
     * two non-empty groups, where every number goes into exactly one group.
     */
    int getSmallestGap(List<Integer> nums) {
        int total = 0;
        for (int num : nums) {
            total += num;
        }

        // The lighter group never has more than half of the total.
        int half = total / 2;

        // canMake[s] is true if some group of numbers adds up to exactly s.
        boolean[] canMake = new boolean[half + 1];
        canMake[0] = true; // the empty group has sum 0

        for (int num : nums) {
            // Go from high to low so that each number is used at most once.
            for (int s = half; s >= num; s--) {
                if (canMake[s - num]) {
                    canMake[s] = true;
                }
            }
        }

        // The group sum closest to half gives the smallest gap.
        // Stop at 1 because an empty group is not allowed.
        for (int s = half; s >= 1; s--) {
            if (canMake[s]) {
                return total - 2 * s;
            }
        }

        // Never happens: the smallest number is always <= half.
        return Integer.MAX_VALUE;
    }
}
```