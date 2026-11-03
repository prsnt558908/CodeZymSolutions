# Maximum Sum Subarray with Equal First and Last Elements in Python

#### Problem Statement
[https://codezym.com/question/140-maximum-sum-subarray-equal-first-last-elements](https://codezym.com/question/140-maximum-sum-subarray-equal-first-last-elements)


The core idea is to combine **prefix sums** and a **dictionary** (hash map). Prefix sums turn the sum of any subarray into one subtraction. The dictionary remembers, for every value, the best place to start a subarray with that value. Together, they find the answer in one pass over the list, in `O(n)` time.

A valid subarray is a continuous part of the list whose first and last values are equal. A single element always counts, because it is both the first and the last element.

We will start with a simple brute force, see why it is slow, and then improve it step by step.


## Solution 1: Brute Force

Pick every start index `i`. Move the end index `j` from `i` to the end of the list and keep a running sum.

Whenever `nums[i]` equals `nums[j]`, the subarray from `i` to `j` is valid. Compare its sum with the best answer so far.

```python
best = float("-inf")

for i in range(len(nums)):
    total = 0

    for j in range(i, len(nums)):
        total += nums[j]

        # valid only when both ends hold the same value
        if nums[i] == nums[j]:
            best = max(best, total)

return best
```

Since `j` starts at `i`, single element subarrays are checked too.

### Why is this slow?

There are about `n * n / 2` pairs of start and end. With `n = 2 * 10^5`, that is around `2 * 10^10` steps, which is far too slow.

There is also wasted work. For an end `j`, only the starts holding the same value as `nums[j]` matter, and among them we only need the best one. Brute force still checks every start.

- **Time:** `O(n^2)`
- **Space:** `O(1)`


## Solution 2: Prefix Sum + Dictionary (Optimal)

We fix the slow parts one at a time.

### Step 1: Get any subarray sum with one subtraction

Let `prefix[k]` be the sum of the first `k` elements, with `prefix[0] = 0`. Then:

```
sum of nums[i..j] = prefix[j + 1] - prefix[i]
```

This splits the sum into a part that depends only on the end (`prefix[j + 1]`) and a part that depends only on the start (`prefix[i]`).

For example, in `[5, -10, 5, 4, -1, 4]` the sum of `[4, -1, 4]` (index `3` to `5`) is `prefix[6] - prefix[3] = 7 - 0 = 7`.

### Step 2: For each end, find the best start

Say the subarray ends at index `j`. Then `prefix[j + 1]` is fixed.

To make `prefix[j + 1] - prefix[i]` as large as possible, we need the **smallest** `prefix[i]` among all starts `i <= j` where `nums[i] == nums[j]`.

### Step 3: Remember only one number per value

We could save every index of every value and try them all. But if all values are the same, that is `O(n^2)` again.

We do not need all of them. Two starts with the same value are both valid for every later end, and the one with the smaller prefix sum is always at least as good. So we keep only the smallest one.

The dictionary `min_prefix` stores:

- **Key:** a number from the list.
- **Value:** the smallest `prefix[i]` seen so far, for an index `i` holding that number.

A dictionary fits well here. Values can be as large as `10^9` and can be negative, so they cannot be used as list indexes. A dictionary finds any of them in `O(1)`.

### Step 4: Walk the list once

We keep `prefix` as the sum of everything before the current element. For each `value`:

1. Save `prefix` in the dictionary for `value`, unless it already holds a smaller number for it.
2. Add `value` to `prefix`. Now it includes the current element.
3. `prefix` minus the dictionary's number for `value` is the best valid sum that ends here. Update the answer with it.

Updating the dictionary before finding the sum is on purpose. It lets a subarray start and end at the same index, so single element subarrays need no special code.

### Dry Run

`nums = [5, -10, 5, 4, -1, 4]`

Each row shows the state after processing that index.

| Index | Value | Prefix Before | Min Prefix for Value | Prefix After | Best Sum Ending Here | Answer |
|:-----:|:-----:|:-------------:|:--------------------:|:------------:|:--------------------:|:------:|
| 0 | 5 | 0 | 0 | 5 | 5 | 5 |
| 1 | -10 | 5 | 5 | -5 | -10 | 5 |
| 2 | 5 | -5 | -5 | 0 | 5 | 5 |
| 3 | 4 | 0 | 0 | 4 | 4 | 5 |
| 4 | -1 | 4 | 4 | 3 | -1 | 5 |
| 5 | 4 | 3 | 0 | 7 | 7 | 7 |

At index `2`, the smallest prefix for value `5` drops from `0` to `-5`. Starting fresh at index `2` (sum `5`) beats the full `[5, -10, 5]` (sum `0`).

At index `5`, the best start for value `4` is index `3`. This gives `[4, -1, 4]` with sum `7`, which is the final answer.

### Big Numbers

A sum can reach `2 * 10^5 * 10^9 = 2 * 10^14`. Python integers never overflow, so no special handling is needed.

### Complexity

- **Time:** `O(n)`, one pass with `O(1)` dictionary work per element.
- **Space:** `O(n)`, at most one dictionary entry per distinct value.

### Code

```python
from typing import List


class MaximumSumSubarrayEqualEnds:

    def __init__(self):
        pass

    def maximumSumSubarray(self, nums: List[int]) -> int:
        """
        Returns the maximum sum of a subarray whose first and last elements are equal.
        Runs in O(n) time with one pass, a running prefix sum and a dictionary.
        """
        # value -> smallest prefix sum seen right before an index holding this value
        min_prefix = {}

        prefix = 0            # sum of all elements before the current index
        best = float("-inf")  # any real sum is larger than this

        for value in nums:
            # The current index can also be the start, so include its prefix first.
            # This handles single element subarrays without any special case.
            smallest = min(min_prefix.get(value, prefix), prefix)
            min_prefix[value] = smallest

            # prefix now includes the current element
            prefix += value

            # best valid subarray that ends at the current index
            best = max(best, prefix - smallest)

        return best
```