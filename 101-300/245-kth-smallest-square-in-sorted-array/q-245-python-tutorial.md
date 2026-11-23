# Find Kth Smallest Square In Sorted Array in Python

#### Problem Statement
[https://codezym.com/question/245-kth-smallest-square-in-sorted-array](https://codezym.com/question/245-kth-smallest-square-in-sorted-array)


The square of a number depends only on how far that number is from zero. For example, `-6` and `6` both have the square `36`. So the kth smallest square is just the square of the kth number closest to zero.

Our list is already sorted. So the numbers closest to zero sit next to each other, right around the point where the negative numbers end.

We will start with the simple idea of squaring and sorting. Then we will walk outward from zero using two pointers. Finally, we will use binary search to jump straight to the answer.

---

## Brute Force: Square and Sort

Square every number, sort the squares, and return the one at index `k - 1`.

For `nums = [-6, -4, -1, 3, 5]`, the squares are `36, 16, 1, 9, 25`. Sorted, they become `1, 9, 16, 25, 36`. With `k = 2`, the answer is `9`.

```python
squares = sorted(num * num for num in nums)
return squares[k - 1]
```

A square can be as big as `10^18`. Python integers have no size limit, so this is safe.

**The problem:** this ignores that `nums` is already sorted. We sort all `n` squares in `O(n log n)` time and use `O(n)` extra memory, only to read one value.

---

## Solution 1: Two Pointers From the Middle

### Idea

Split the list where the negative numbers end. For `[-6, -4, -1, 3, 5]` we get two parts:

- `[3, 5]` read left to right gives squares `9, 25`. They grow.
- `[-6, -4, -1]` read right to left gives squares `1, 16, 36`. They also grow, because the numbers move further from zero.

So we have two sorted lists of squares, and both start at the split point.

We merge them, just like merging two sorted lists, but stop after `k` picks. The last pick is the answer.

### Steps

1. Find `right`, the index of the first number `>= 0`. The list is sorted, so a simple binary search finds it.
2. Set `left = right - 1`, the index of the last negative number.
3. Repeat `k` times: compare the squares at `left` and `right`, take the smaller one, and move that pointer outward.
4. Return the last square we took.

If one side runs out of numbers, we treat its square as infinity (`float("inf")`). This way we always pick from the other side.

### Dry Run

`nums = [-6, -4, -1, 3, 5]`, `k = 2`. We start with `left = 2` (value `-1`) and `right = 3` (value `3`).

| Pick | Left square | Right square | Take | Pointer moved |
|---|---|---|---|---|
| 1 | 1 | 9 | 1 | left = 1 |
| 2 | 16 | 9 | 9 | right = 4 |

The answer is `9`.

### Code

```python
from typing import List


class KthSmallestSquareFinder:

    def __init__(self):
        pass

    def kthSmallestSquare(self, nums: List[int], k: int) -> int:
        """Returns the kth smallest square.
        Walks outward from zero with two pointers and picks the smaller square each time.
        """
        n = len(nums)

        # right starts at the first non-negative number,
        # left starts at the last negative number
        right = self.firstNonNegativeIndex(nums)
        left = right - 1

        answer = 0
        for _ in range(k):
            # a side with no numbers left gets an "infinite" square,
            # so it is never picked
            left_square = self.square(nums[left]) if left >= 0 else float("inf")
            right_square = self.square(nums[right]) if right < n else float("inf")

            # take the smaller square and move that pointer outward
            if left_square <= right_square:
                answer = left_square
                left -= 1
            else:
                answer = right_square
                right += 1

        return answer

    def firstNonNegativeIndex(self, nums: List[int]) -> int:
        """Returns the index of the first number >= 0.
        Returns len(nums) if every number is negative.
        """
        low, high = 0, len(nums)
        while low < high:
            mid = (low + high) // 2
            if nums[mid] < 0:
                low = mid + 1
            else:
                high = mid
        return low

    def square(self, value: int) -> int:
        return value * value
```

### Complexity

- **Time:** `O(log n + k)`. Binary search finds the split, then we make `k` picks.
- **Space:** `O(1)`.

This is fast when `k` is small. But when `k` is close to `n`, we still walk through almost the whole list. Can we jump straight to the answer?

---

## Solution 2: Binary Search on the Window Start (Optimal)

### Key Observation

Look at the numbers Solution 1 picks. Every pick takes the next number on the left or the next number on the right. So the picked numbers always form one block of neighbors in the list. Let's call this block the **window**.

In the dry run above, the picks were `-1` and `3`. They sit side by side at indices `2` and `3`.

Inside a sorted window, the number furthest from zero is always at one of its two ends. So once we know where the window starts, the answer is:

```
max(square(nums[start]), square(nums[start + k - 1]))
```

Now we only need to find `start`. It can be anything from `0` to `n - k`, and we will find it with binary search.

### Which Way Should the Window Slide?

For a window that starts at `start`, look at two numbers:

- `nums[start]`: the first number inside the window.
- `nums[start + k]`: the first number just outside the window, on the right.

Sliding the window right by one drops `nums[start]` and adds `nums[start + k]`.

Picture both numbers on a number line. If zero is past their midpoint, on the side of `nums[start + k]`, then `nums[start + k]` is at least as close to zero. Sliding right never hurts, so we slide right.

Otherwise `nums[start]` is at least as close to zero, and sliding right cannot help.

Zero is past their midpoint exactly when their sum is negative:

```
nums[start] + nums[start + k] < 0
```

As `start` grows, both numbers grow, so their sum only grows. The answers look like `yes, yes, ..., no, no`. That is exactly the shape binary search needs.

So we binary search for the first `start` with answer `no`. Sliding right never hurts before it and never helps after it, so this is where our window begins.

If every answer is `yes`, the window sits at the very end, `start = n - k`.

> **Why not just compare absolute values?** It can fail when a negative number repeats.
>
> Take `nums = [-2, -2, -2, 0]` and `k = 1`. Comparing `|-2|` with `|-2|` looks like a tie, so the window never slides right and we return `4`. But the correct answer is `0`.
>
> The sum check sees `-2 + -2 < 0`, so it keeps sliding right and reaches `0`.

### Dry Run

`nums = [-6, -4, -1, 3, 5]`, `k = 2`. The window can start anywhere from `0` to `3`.

| low | high | start | nums[start] + nums[start + 2] | Action |
|---|---|---|---|---|
| 0 | 3 | 1 | -4 + 3 = -1 | slide right, low = 2 |
| 2 | 3 | 2 | -1 + 5 = 4 | stay, high = 2 |

The window starts at index `2`, so it holds `[-1, 3]`. The answer is `max(1, 9) = 9`.

### Code

```python
from typing import List


class KthSmallestSquareFinder:

    def __init__(self):
        pass

    def kthSmallestSquare(self, nums: List[int], k: int) -> int:
        """Returns the kth smallest square.
        The k numbers closest to zero form one window in the sorted list.
        We binary search for where that window starts.
        """
        # the window nums[start .. start + k - 1] can start anywhere from 0 to n - k
        low, high = 0, len(nums) - k

        while low < high:
            start = (low + high) // 2

            # zero is past the midpoint of nums[start] and nums[start + k],
            # so nums[start + k] is at least as close to zero: slide right
            if nums[start] + nums[start + k] < 0:
                low = start + 1
            else:
                high = start

        # the biggest square in a sorted window is at one of its two ends
        return max(self.square(nums[low]), self.square(nums[low + k - 1]))

    def square(self, value: int) -> int:
        return value * value
```

### Complexity

- **Time:** `O(log n)`. We binary search over the `n - k + 1` possible window starts.
- **Space:** `O(1)`.

---

## Complexity Summary

| Approach | Time | Extra Space |
|---|---|---|
| Brute Force: Square and Sort | `O(n log n)` | `O(n)` |
| Two Pointers From the Middle | `O(log n + k)` | `O(1)` |
| Binary Search on the Window Start | `O(log n)` | `O(1)` |