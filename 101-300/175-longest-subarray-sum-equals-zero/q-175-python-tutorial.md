# Longest Subarray Sum Equals Zero in Python

#### Problem Statement
[https://codezym.com/question/175-longest-subarray-sum-equals-zero](https://codezym.com/question/175-longest-subarray-sum-equals-zero)

The key idea is the **prefix sum**, which is just a running total of the numbers from the start of the list. If the prefix sum is the same at two different indexes, then the numbers between those indexes add up to `0`. So the task becomes: find two equal prefix sums that are as far apart as possible. A dictionary that remembers the **first** index of every prefix sum lets us do this in a single pass.

We will start with a simple brute force solution, see why it is slow, and then improve it.

## Problem in Short

You are given a list of integers. It can have positive numbers, negative numbers and zeros.

Return the length of the longest contiguous subarray whose sum is exactly `0`. If there is no such subarray, return `0`.

For `[15, -2, 2, -8, 1, 7, 10, 23]` the answer is `5`, because `[-2, 2, -8, 1, 7]` adds up to `0`.

## Approach 1: Brute Force

Try every possible start index. From each start, grow the subarray to the right one number at a time and keep adding to `total`. Whenever `total` becomes `0`, update the answer.

```python
max_length = 0

for start in range(len(numbers)):
    total = 0

    for end in range(start, len(numbers)):
        total += numbers[end]

        if total == 0:
            max_length = max(max_length, end - start + 1)
```

This takes `O(n^2)` time and `O(1)` extra space.

**The problem:** the list can have up to `100,000` numbers. Two nested loops check about `n * n / 2` subarrays, which is around **5 billion** checks. That is far too slow.

For every end index, we try every possible start index. Can we find the best start index instantly instead?

## Approach 2: Prefix Sum + Dictionary (Optimal)

### Key Observation

Let `prefix[i]` be the sum of all numbers from index `0` to index `i`.

Then the sum of the subarray from index `i + 1` to index `j` is:

```
prefix[j] - prefix[i]
```

This is because `prefix[j]` covers everything up to `j`. Removing `prefix[i]` leaves only the numbers from `i + 1` to `j`.

So this subarray has sum `0` exactly when `prefix[j] == prefix[i]`.

> **Two equal prefix sums mean the numbers between them add up to `0`.**

Let's check it on the example above:

| Index      | 0  | 1  | 2  | 3  | 4 | 5  | 6  | 7  |
|------------|----|----|----|----|---|----|----|----|
| Number     | 15 | -2 | 2  | -8 | 1 | 7  | 10 | 23 |
| Prefix sum | 15 | 13 | 15 | 7  | 8 | 15 | 25 | 48 |

The prefix sum `15` appears at index `0` and again at index `5`. So the numbers from index `1` to `5`, which are `[-2, 2, -8, 1, 7]`, add up to `0`. The length is `5 - 0 = 5`.

### Finding the Longest One in a Single Pass

We walk through the list once. At each index `j`, we ask: **have I seen this prefix sum before, and where did I first see it?**

If we first saw it at index `i`, the subarray from `i + 1` to `j` has sum `0`, and its length is `j - i`.

**Why store only the first index?** A smaller `i` gives a longer subarray. In the example, `15` shows up at index `0`, `2` and `5`. At index `5`, pairing with index `0` gives length `5`, but pairing with index `2` gives only `3`. So we save the first index of each prefix sum and never overwrite it.

**Why a dictionary?** We do this lookup at every index, so it must be fast. A dictionary that maps prefix sum -> first index does each lookup in `O(1)` time on average. A plain list would not work here, because prefix sums can be negative or very large.

### Subarrays That Start at Index 0

Take `[3, -3]`. The prefix sums are `3, 0`. The whole list adds up to `0`, but the sum `0` was never seen before index `1`, so we would miss it.

The fix is to start the dictionary as `{0: -1}`. It simply means: before reading any number, the sum is already `0`, at index `-1`.

Now at index `1`, the prefix sum `0` is found at index `-1`, and the length is `1 - (-1) = 2`.

### Large Sums

Each number can be as large as `1,000,000,000` (positive or negative), and there can be `100,000` numbers. So a prefix sum can reach `10^14`.

Python integers grow as large as needed, so no special handling is required.

### Dry Run

Input: `[0, 0, 5, -5, 2]`, expected answer `4`.

| i     | Number | Prefix sum | Seen before? | Action                | max_length |
|-------|--------|------------|--------------|-----------------------|------------|
| start |        | 0          |              | store `0 -> -1`       | 0          |
| 0     | 0      | 0          | yes, at `-1` | length `0 - (-1) = 1` | 1          |
| 1     | 0      | 0          | yes, at `-1` | length `1 - (-1) = 2` | 2          |
| 2     | 5      | 5          | no           | store `5 -> 2`        | 2          |
| 3     | -5     | 0          | yes, at `-1` | length `3 - (-1) = 4` | 4          |
| 4     | 2      | 2          | no           | store `2 -> 4`        | 4          |

The answer is `4`, from the subarray `[0, 0, 5, -5]`.

### Python Code

```python
from typing import List


class LongestZeroSumSubarray:

    def __init__(self):
        pass

    def longestZeroSumSubarray(self, numbers: List[int]) -> int:
        """
        Return the length of the longest contiguous subarray with sum 0.
        Return 0 if no such subarray exists.
        """

        # prefix sum -> first index where this prefix sum appeared.
        # Before reading any number, the sum is 0 at index -1.
        # This lets us count subarrays that start at index 0.
        first_index = {0: -1}

        prefix_sum = 0
        max_length = 0

        for i, number in enumerate(numbers):
            prefix_sum += number

            if prefix_sum in first_index:
                # Same prefix sum seen before, so the numbers
                # after that index, up to i, add up to 0.
                length = i - first_index[prefix_sum]
                max_length = max(max_length, length)
            else:
                # Save only the first index. It gives the longest subarray later.
                first_index[prefix_sum] = i

        return max_length
```

### Complexity

- **Time:** `O(n)`. We visit each number once, and each dictionary operation takes `O(1)` on average.
- **Space:** `O(n)`. The dictionary can hold up to `n + 1` different prefix sums.