# Sorted Order of Squares in Java

#### Problem Statement
[https://codezym.com/question/230-sorted-order-of-squares](https://codezym.com/question/230-sorted-order-of-squares)

## Core Idea

We need to arrange the numbers by their squares, but return the original numbers.

A bigger square simply means the number is farther from zero. So we can compare absolute values instead of squares. This also avoids overflow, because a square can be as big as 10^18, which does not fit in an `int`.

The input is already sorted. So the numbers closest to zero sit in the middle of the list, where the negative numbers end and the non-negative numbers begin.

From that middle point, walking left gives negative numbers that keep getting farther from zero. Walking right gives non-negative numbers that also keep getting farther from zero.

So we already have two sorted lists. We only need to merge them, just like the merge step of merge sort.

For the k-th element we do not even need the full answer. We can binary search to find how many of the first k values come from each side.

## Handling Ties

Two numbers have the same square only when they are equal, or when they are `x` and `-x`.

Since the input is sorted, `-x` always appears before `x`. So on a tie, the negative number goes first.

If the two numbers are exactly equal, their order does not matter, because they look the same in the output.

## Solution 1: Sort by Absolute Value (Brute Force)

The simplest idea is to copy the list and sort it by absolute value.

Java's `List.sort` is a stable sort. Equal items keep their original order, so the tie rule is handled for free.

```java
public List<Integer> squareSortedOrder(List<Integer> nums) {
    List<Integer> result = new ArrayList<>(nums);
    // stable sort, so equal absolute values keep their input order
    result.sort((a, b) -> Integer.compare(Math.abs(a), Math.abs(b)));
    return result;
}

public int kthSquareSortedElement(List<Integer> nums, int k) {
    return squareSortedOrder(nums).get(k - 1);
}
```

**What is wrong with it?**

- Sorting takes O(n log n) time. It does not use the fact that the input is already sorted.
- To get a single value (the k-th one), we sort all n values.

## Solution 2: Two Pointers from the Middle

**Step 1: Find the middle**

Find `split`, the index of the first value that is `>= 0`. Every value before it is negative.

The list is sorted, so binary search finds `split` in O(log n).

**Step 2: Merge outwards**

Keep two pointers:

- `left = split - 1` walks left over the negative values.
- `right = split` walks right over the non-negative values.

At each step, pick the value that is closer to zero and move that pointer one step outwards.

If both values are equally close, pick the left one (the negative value). If one side is used up, keep picking from the other side.

**Example:** `nums = [-5, -3, -1, 2, 4]`, so `split = 3`

| Step | Left value | Right value | Picked |
|------|------------|-------------|--------|
| 1    | -1         | 2           | -1     |
| 2    | -3         | 2           | 2      |
| 3    | -3         | 4           | -3     |
| 4    | -5         | 4           | 4      |
| 5    | -5         | none        | -5     |

Result: `[-1, 2, -3, 4, -5]`

**The k-th element**

Run the same merge, but stop as soon as k values are picked. The last picked value is the answer.

Both methods share one helper, `firstKInSquareOrder(nums, k)`. For the full answer we simply pass `k = n`.

We store the picked values in an `ArrayList`, because we only add to its end.

```java
import java.util.*;

public class SortedOrderOfSquares {

    public SortedOrderOfSquares() {
    }

    public List<Integer> squareSortedOrder(List<Integer> nums) {
        return firstKInSquareOrder(nums, nums.size());
    }

    public int kthSquareSortedElement(List<Integer> nums, int k) {
        // build only the first k values, the k-th value is the last of them
        List<Integer> firstK = firstKInSquareOrder(nums, k);
        return firstK.get(k - 1);
    }

    /**
     * Merges the two sides of nums, starting from the middle, and stops after k values.
     * Left side: negative values, read from right to left.
     * Right side: non-negative values, read from left to right.
     * Both sides move away from zero, so each side is already in square order.
     */
    List<Integer> firstKInSquareOrder(List<Integer> nums, int k) {
        int n = nums.size();
        int right = firstNonNegativeIndex(nums);
        int left = right - 1;
        List<Integer> result = new ArrayList<>();

        while (result.size() < k) {
            boolean pickLeft;
            if (left < 0) {
                pickLeft = false; // left side is used up
            } else if (right == n) {
                pickLeft = true; // right side is used up
            } else {
                // on a tie the negative value goes first, as it appears earlier in the input
                pickLeft = Math.abs(nums.get(left)) <= nums.get(right);
            }

            if (pickLeft) {
                result.add(nums.get(left));
                left--;
            } else {
                result.add(nums.get(right));
                right++;
            }
        }
        return result;
    }

    /**
     * Binary search for the index of the first value >= 0.
     * Returns nums.size() when all values are negative.
     */
    int firstNonNegativeIndex(List<Integer> nums) {
        int low = 0;
        int high = nums.size();
        while (low < high) {
            int mid = (low + high) / 2;
            if (nums.get(mid) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }
}
```

## Solution 3: Binary Search for the k-th Element

Solution 2 still walks k steps. When k is close to n, that is O(n). We can find the k-th value in O(log n).

**The idea**

The first k values of the answer are some values from the left side plus the rest from the right side.

Say `fromLeft` values come from the left side. Then `k - fromLeft` values come from the right side.

If we know `fromLeft`, the answer is easy. It is the later one of the two last picked values: the one farther from zero, or the non-negative one on a tie. If all k values come from one side, it is simply the last value taken from that side.

**Checking a guess**

For a guess `fromLeft`, look at two values:

- `nextLeft`: the first left value we did **not** take.
- `lastRight`: the last right value we **did** take.

If `Math.abs(nextLeft) <= lastRight`, then `nextLeft` should have been picked before `lastRight`. So we took too few from the left, and the real `fromLeft` is bigger. We use `<=` because on a tie the negative value goes first.

Otherwise, the guess is big enough.

**Why binary search works**

As the guess grows, `nextLeft` never gets closer to zero, and `lastRight` never gets farther from zero.

So once a guess is big enough, every bigger guess is big enough too. We binary search for the smallest guess that is big enough, and that is the real `fromLeft`.

The guess must leave enough values on each side. So it stays between `max(0, k - rightCount)` and `min(k, leftCount)`, where `leftCount` and `rightCount` are the sizes of the two sides.

**Example:** `nums = [-6, -2, -1, 3, 4]`, `k = 4`

The left side gives `-1, -2, -6`. The right side gives `3, 4`.

The right side has only 2 values and the left side has 3, so `fromLeft` is 2 or 3.

Try `fromLeft = 2`: `nextLeft = -6` and `lastRight = 4`. Since 6 > 4, the guess is big enough. It is also the smallest possible guess, so `fromLeft = 2`.

The last picked values are `-2` and `4`. `4` is farther from zero, so it comes later. Answer: `4`.

**Reading values by position**

The left side is read backwards from `split - 1`, so its i-th value (0-based) is `nums.get(split - 1 - i)`. The right side's i-th value is `nums.get(split + i)`.

`squareSortedOrder` uses the same merge as Solution 2. Only `kthSquareSortedElement` changes, and it now needs O(1) extra space.

```java
import java.util.*;

public class SortedOrderOfSquares {

    public SortedOrderOfSquares() {
    }

    /**
     * Merges the two sides of nums, starting from the middle.
     * Left side: negative values, read from right to left.
     * Right side: non-negative values, read from left to right.
     * Both sides move away from zero, so each side is already in square order.
     */
    public List<Integer> squareSortedOrder(List<Integer> nums) {
        int n = nums.size();
        int right = firstNonNegativeIndex(nums);
        int left = right - 1;
        List<Integer> result = new ArrayList<>();

        while (result.size() < n) {
            boolean pickLeft;
            if (left < 0) {
                pickLeft = false; // left side is used up
            } else if (right == n) {
                pickLeft = true; // right side is used up
            } else {
                // on a tie the negative value goes first, as it appears earlier in the input
                pickLeft = Math.abs(nums.get(left)) <= nums.get(right);
            }

            if (pickLeft) {
                result.add(nums.get(left));
                left--;
            } else {
                result.add(nums.get(right));
                right++;
            }
        }
        return result;
    }

    /**
     * The first k values of the answer are fromLeft values from the left side
     * plus (k - fromLeft) values from the right side. We binary search for fromLeft.
     */
    public int kthSquareSortedElement(List<Integer> nums, int k) {
        int n = nums.size();
        int split = firstNonNegativeIndex(nums);
        int leftCount = split; // how many negative values
        int rightCount = n - split; // how many non-negative values

        // fromLeft can not be more than k or leftCount.
        // The right side must give the other k - fromLeft values, so fromLeft >= k - rightCount.
        int low = Math.max(0, k - rightCount);
        int high = Math.min(k, leftCount);

        while (low < high) {
            int mid = (low + high) / 2;
            int nextLeft = nums.get(split - 1 - mid); // first left value we did not take
            int lastRight = nums.get(split + k - mid - 1); // last right value we took
            if (Math.abs(nextLeft) <= lastRight) {
                // nextLeft should come before lastRight, so we took too few from the left
                low = mid + 1;
            } else {
                high = mid;
            }
        }

        int fromLeft = low;
        if (fromLeft == 0) {
            return nums.get(split + k - 1); // all k values came from the right side
        }
        if (fromLeft == k) {
            return nums.get(split - k); // all k values came from the left side
        }

        int lastLeft = nums.get(split - fromLeft);
        int lastRight = nums.get(split + k - fromLeft - 1);
        // the k-th value is the one that comes later in the answer
        if (Math.abs(lastLeft) <= lastRight) {
            return lastRight;
        }
        return lastLeft;
    }

    /**
     * Binary search for the index of the first value >= 0.
     * Returns nums.size() when all values are negative.
     */
    int firstNonNegativeIndex(List<Integer> nums) {
        int low = 0;
        int high = nums.size();
        while (low < high) {
            int mid = (low + high) / 2;
            if (nums.get(mid) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }
}
```

## Complexity

| Solution | `squareSortedOrder` | `kthSquareSortedElement` |
|----------|---------------------|--------------------------|
| 1. Sort by absolute value | O(n log n) | O(n log n) |
| 2. Two pointers | O(n) | O(log n + k) |
| 3. Binary search | O(n) | O(log n) |

`squareSortedOrder` must return all n values, so O(n) is the best possible for it.

Extra space for `kthSquareSortedElement` is O(n) in Solution 1, O(k) in Solution 2 and O(1) in Solution 3.