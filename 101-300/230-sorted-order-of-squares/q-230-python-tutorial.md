# Sorted Order of Squares in Python

#### Problem Statement
[https://codezym.com/question/230-sorted-order-of-squares](https://codezym.com/question/230-sorted-order-of-squares)

## Core Idea

We need to arrange the numbers by their squares, but return the original numbers.

A bigger square simply means the number is farther from zero. So we can compare absolute values with `abs(x)` instead of computing squares.

The input is already sorted. So the numbers closest to zero sit in the middle of the list, where the negative numbers end and the non-negative numbers begin.

From that middle point, walking left gives negative numbers that keep getting farther from zero. Walking right gives non-negative numbers that also keep getting farther from zero.

So we already have two sorted lists. We only need to merge them, just like the merge step of merge sort.

For the k-th element we do not even need the full answer. We can binary search to find how many of the first k values come from each side.

## Handling Ties

Two numbers have the same square only when they are equal, or when they are `x` and `-x`.

Since the input is sorted, `-x` always appears before `x`. So on a tie, the negative number goes first.

If the two numbers are exactly equal, their order does not matter, because they look the same in the output.

## Solution 1: Sort by Absolute Value (Brute Force)

The simplest idea is to sort a copy of the list by absolute value.

Python's `sorted` is a stable sort. Equal items keep their original order, so the tie rule is handled for free.

```python
def squareSortedOrder(self, nums: List[int]) -> List[int]:
    # sorted() is stable, so equal absolute values keep their input order
    return sorted(nums, key=abs)

def kthSquareSortedElement(self, nums: List[int], k: int) -> int:
    return self.squareSortedOrder(nums)[k - 1]
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

We store the picked values in a plain `list`, because we only append to its end.

```python
from typing import List


class SortedOrderOfSquares:

    def __init__(self):
        pass

    def squareSortedOrder(self, nums: List[int]) -> List[int]:
        return self.firstKInSquareOrder(nums, len(nums))

    def kthSquareSortedElement(self, nums: List[int], k: int) -> int:
        # build only the first k values, the k-th value is the last of them
        first_k = self.firstKInSquareOrder(nums, k)
        return first_k[k - 1]

    def firstKInSquareOrder(self, nums: List[int], k: int) -> List[int]:
        """
        Merges the two sides of nums, starting from the middle.
        Stops after k values.
        Left side: negative values, read from right to left.
        Right side: non-negative values, read from left to right.
        Both sides move away from zero, so each side is already in square order.
        """
        n = len(nums)
        right = self.firstNonNegativeIndex(nums)
        left = right - 1
        result = []

        while len(result) < k:
            if left < 0:
                pick_left = False  # left side is used up
            elif right == n:
                pick_left = True  # right side is used up
            else:
                # on a tie the negative value goes first,
                # as it appears earlier in the input
                pick_left = abs(nums[left]) <= nums[right]

            if pick_left:
                result.append(nums[left])
                left -= 1
            else:
                result.append(nums[right])
                right += 1

        return result

    def firstNonNegativeIndex(self, nums: List[int]) -> int:
        """
        Binary search for the index of the first value >= 0.
        Returns len(nums) when all values are negative.
        """
        low = 0
        high = len(nums)
        while low < high:
            mid = (low + high) // 2
            if nums[mid] < 0:
                low = mid + 1
            else:
                high = mid
        return low
```

## Solution 3: Binary Search for the k-th Element

Solution 2 still walks k steps. When k is close to n, that is O(n). We can find the k-th value in O(log n).

**The idea**

The first k values of the answer are some values from the left side plus the rest from the right side.

Say `from_left` values come from the left side. Then `k - from_left` values come from the right side.

If we know `from_left`, the answer is easy. It is the later one of the two last picked values: the one farther from zero, or the non-negative one on a tie. If all k values come from one side, it is simply the last value taken from that side.

**Checking a guess**

For a guess `from_left`, look at two values:

- `next_left`: the first left value we did **not** take.
- `last_right`: the last right value we **did** take.

If `abs(next_left) <= last_right`, then `next_left` should have been picked before `last_right`. So we took too few from the left, and the real `from_left` is bigger. We use `<=` because on a tie the negative value goes first.

Otherwise, the guess is big enough.

**Why binary search works**

As the guess grows, `next_left` never gets closer to zero, and `last_right` never gets farther from zero.

So once a guess is big enough, every bigger guess is big enough too. We binary search for the smallest guess that is big enough, and that is the real `from_left`.

The guess must leave enough values on each side. So it stays between `max(0, k - right_count)` and `min(k, left_count)`, where `left_count` and `right_count` are the sizes of the two sides.

**Example:** `nums = [-6, -2, -1, 3, 4]`, `k = 4`

The left side gives `-1, -2, -6`. The right side gives `3, 4`.

The right side has only 2 values and the left side has 3, so `from_left` is 2 or 3.

Try `from_left = 2`: `next_left = -6` and `last_right = 4`. Since 6 > 4, the guess is big enough. It is also the smallest possible guess, so `from_left = 2`.

The last picked values are `-2` and `4`. `4` is farther from zero, so it comes later. Answer: `4`.

**Reading values by position**

The left side is read backwards from `split - 1`, so its i-th value (0-based) is `nums[split - 1 - i]`. The right side's i-th value is `nums[split + i]`.

`squareSortedOrder` uses the same merge as Solution 2. Only `kthSquareSortedElement` changes, and it now needs O(1) extra space.

```python
from typing import List


class SortedOrderOfSquares:

    def __init__(self):
        pass

    def squareSortedOrder(self, nums: List[int]) -> List[int]:
        """
        Merges the two sides of nums, starting from the middle.
        Left side: negative values, read from right to left.
        Right side: non-negative values, read from left to right.
        Both sides move away from zero, so each side is already in square order.
        """
        n = len(nums)
        right = self.firstNonNegativeIndex(nums)
        left = right - 1
        result = []

        while len(result) < n:
            if left < 0:
                pick_left = False  # left side is used up
            elif right == n:
                pick_left = True  # right side is used up
            else:
                # on a tie the negative value goes first,
                # as it appears earlier in the input
                pick_left = abs(nums[left]) <= nums[right]

            if pick_left:
                result.append(nums[left])
                left -= 1
            else:
                result.append(nums[right])
                right += 1

        return result

    def kthSquareSortedElement(self, nums: List[int], k: int) -> int:
        """
        The first k values of the answer are from_left values from the left side
        plus (k - from_left) values from the right side.
        We binary search for from_left.
        """
        n = len(nums)
        split = self.firstNonNegativeIndex(nums)
        left_count = split  # how many negative values
        right_count = n - split  # how many non-negative values

        # from_left can not be more than k or left_count.
        # The right side must give the other k - from_left values,
        # so from_left >= k - right_count.
        low = max(0, k - right_count)
        high = min(k, left_count)

        while low < high:
            mid = (low + high) // 2
            next_left = nums[split - 1 - mid]  # first left value we did not take
            last_right = nums[split + k - mid - 1]  # last right value we took
            if abs(next_left) <= last_right:
                # next_left should come before last_right,
                # so we took too few from the left
                low = mid + 1
            else:
                high = mid

        from_left = low
        if from_left == 0:
            return nums[split + k - 1]  # all k values came from the right side
        if from_left == k:
            return nums[split - k]  # all k values came from the left side

        last_left = nums[split - from_left]
        last_right = nums[split + k - from_left - 1]
        # the k-th value is the one that comes later in the answer
        if abs(last_left) <= last_right:
            return last_right
        return last_left

    def firstNonNegativeIndex(self, nums: List[int]) -> int:
        """
        Binary search for the index of the first value >= 0.
        Returns len(nums) when all values are negative.
        """
        low = 0
        high = len(nums)
        while low < high:
            mid = (low + high) // 2
            if nums[mid] < 0:
                low = mid + 1
            else:
                high = mid
        return low
```

## Complexity

| Solution | `squareSortedOrder` | `kthSquareSortedElement` |
|----------|---------------------|--------------------------|
| 1. Sort by absolute value | O(n log n) | O(n log n) |
| 2. Two pointers | O(n) | O(log n + k) |
| 3. Binary search | O(n) | O(log n) |

`squareSortedOrder` must return all n values, so O(n) is the best possible for it.

Extra space for `kthSquareSortedElement` is O(n) in Solution 1, O(k) in Solution 2 and O(1) in Solution 3.