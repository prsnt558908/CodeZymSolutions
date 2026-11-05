# Sum of All Good Arithmetic Sequences in Python

#### Problem Statement

[https://codezym.com/question/144-sum-good-arithmetic-sequences](https://codezym.com/question/144-sum-good-arithmetic-sequences)


Group the valid subarrays by where they end. Keep their count and their combined sum. When the next number continues the same step, update that sum without building the subarrays again. When the step changes, keep only the subarrays that still work. This gives a single-pass solution with constant extra space.


## What are we adding?

Every single element is valid. A longer subarray must move by `+1` throughout or by `-1` throughout. For example, `[2, 3, 4]` is valid, but `[2, 3, 2]` is not because its direction changes.

We need the total of the subarray sums, not the number of subarrays. Different positions count separately, even when their values are equal.


## Start with a simple approach

Choose a starting position and extend the subarray to the right. Track its sum and its first step. Stop when a step is not `+1` or `-1`, or when the direction changes.

For one chosen `start`, the main work looks like this. Initialize `answer` to zero before trying all starting positions.

```python
subarray_sum = nums[start]
direction = 0
answer += subarray_sum

for end in range(start + 1, len(nums)):
    difference = nums[end] - nums[end - 1]
    if difference != 1 and difference != -1:
        break
    if end == start + 1:
        direction = difference
    elif difference != direction:
        break

    subarray_sum += nums[end]
    answer += subarray_sum
```

Once a bad step appears, extending farther cannot remove it. That is why stopping is safe.

However, a fully increasing list makes us visit every subarray. There are `n * (n + 1) / 2` of them, so this takes `O(n²)` time. That is too much for `200,000` numbers.


## Improve it by reusing the previous result

Keep two main values for the subarrays ending at the current position.

- `run_length` is their count. It also equals the length of the longest valid subarray ending here.
- `ending_sum` is the sum of all their sums.

We also keep the previous number and `previous_difference` to decide what can continue. A running `answer` collects `ending_sum` at each position. No map or extra list is needed.


### 1. The step is neither +1 nor -1

Only the singleton `[current]` can end here. Reset the count to `1` and the combined sum to `current`.


### 2. The same valid step continues

Suppose the previous count was `k`. Append `current` to all `k` previous subarrays, and also add the new singleton `[current]`.

The new number appears `k + 1` times. Therefore:

```text
new count = k + 1
new combined sum = old combined sum + current * (k + 1)
```

For example, the valid subarrays ending at `3` in `[2, 3]` are `[3]` and `[2, 3]`. Their combined sum is `8`. Appending `4` gives `[3, 4]`, `[2, 3, 4]`, and `[4]`, whose combined sum is `8 + 4 * 3 = 20`.


### 3. A new valid direction starts

If the step is `+1` or `-1` but differs from the previous step, only `[previous, current]` and `[current]` can end here.

Set the count to `2`. Their combined sum is `previous + 2 * current`.

This also handles the first valid pair after a gap or equal neighbors.


## Walk through an example

For `nums = [7, 4, 5, 6, 5]`, group the contributions by their ending position.

- At `7`, `[7]` contributes `7`.
- At `4`, the gap breaks the run. `[4]` contributes `4`.
- At the first `5`, `[5]` and `[4, 5]` contribute `14`.
- At `6`, the increasing run continues. The combined sum becomes `14 + 6 * 3 = 32`.
- At the last `5`, the direction changes. `[5]` and `[6, 5]` contribute `6 + 2 * 5 = 16`.

The answer is `7 + 4 + 14 + 32 + 16 = 73`.


## Why this is correct

Every valid subarray ending here is either the singleton or a previous valid subarray extended by the current number. The three cases above keep exactly those extensions whose steps remain valid and consistent.

Each subarray has one ending position, so adding the combined sum at every position counts it exactly once. Negative numbers need no special treatment because we add every valid sum, even when it is negative.


## Complexity and implementation notes

The final solution takes `O(n)` time and `O(1)` extra space. It does not change the input. The loop reads the list in order without creating slices or copying subarrays.

`SumOfGoodArithmeticSubarrays` is the required entry class. All working values stay inside the method, so repeated calls are independent.

The input is guaranteed to be nonempty. Initialize `previous` to its first value. The first loop iteration then has a difference of zero and correctly adds just that singleton.

Python integers handle the large products and sums directly.


## Final Python code

```python
from typing import List


class SumOfGoodArithmeticSubarrays:
    def __init__(self):
        pass

    def sumOfGoodArithmeticSubarrays(self, nums: List[int]) -> int:
        previous = nums[0]
        previous_difference = 0
        run_length = 0
        ending_sum = 0
        answer = 0

        for current in nums:
            difference = current - previous

            if difference != 1 and difference != -1:
                # Only the singleton is valid, including on the first iteration.
                run_length = 1
                ending_sum = current
            elif difference == previous_difference:
                # Extend every previous valid subarray and add the singleton.
                run_length += 1
                ending_sum += current * run_length
            else:
                # A new direction keeps only the pair and the singleton.
                run_length = 2
                ending_sum = previous + 2 * current

            answer += ending_sum
            previous = current
            previous_difference = difference

        return answer
```
