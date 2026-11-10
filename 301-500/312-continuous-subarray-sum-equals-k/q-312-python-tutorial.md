# Continuous Subarray Sum Equals K in Python

#### Problem Statement
[https://codezym.com/question/312-continuous-subarray-sum-equals-k](https://codezym.com/question/312-continuous-subarray-sum-equals-k)

The core idea is **running sums**, also called prefix sums. The sum of any subarray is simply the running sum at its end minus the running sum just before its start.

So we walk through the list only once and keep a dictionary that counts how many times each running sum has appeared. At every element, the number of earlier running sums equal to the current running sum minus `k` is exactly the number of valid subarrays ending at that element.

We will start with a simple brute force solution, see why it is slow, and then improve it.

---

## Approach 1: Brute Force

The simplest idea is to try every continuous subarray and count the ones whose sum is exactly `k`.

Fix a start index. Then move the end index forward one step at a time and add each new element to a running total. This way we never add up the whole subarray again from scratch.

```python
count = 0

for start in range(len(nums)):
    total = 0
    for end in range(start, len(nums)):
        total += nums[end]  # sum of elements from start to end
        if total == k:
            count += 1

return count
```

### What Is the Problem?

A list of size `n` has about `n * n / 2` subarrays. For `n = 20,000` that is around 200 million subarrays.

Most of this work is repeated. For every new start index, we add up almost the same elements again.

- **Time:** `O(n²)`
- **Space:** `O(1)`

---

## Why Not a Sliding Window?

If all numbers were positive, a sliding window would work: grow the window while the sum is too small, and shrink it when the sum is too big.

But here the list can have negative numbers and zeroes. Adding an element can make the sum smaller, and removing one can make it bigger.

So we can never be sure which side of the window to move. We need a different idea.

---

## Approach 2: Running Sum + Dictionary

### Key Observation

Let `running[i]` be the sum of all elements from index `0` to index `i`. Here it is for `nums = [2, -1, 2, 1]` and `k = 3`:

```
index      0    1    2    3
nums       2   -1    2    1
running    2    1    3    4
```

The sum of the subarray from index `i + 1` to index `j` is `running[j] - running[i]`.

For example, the subarray `[2, 1]` (index `2` to `3`) has sum `running[3] - running[1] = 4 - 1 = 3`.

We want this sum to be `k`:

```
running[j] - running[i] = k
running[i] = running[j] - k
```

So when we stand at index `j`, we only need to know: **how many earlier running sums were equal to `running[j] - k`?** Each of them marks the start of a valid subarray that ends at `j`.

We do not even need the whole `running` list. One variable, `running_sum`, updated as we move forward, is enough.

### Why a Dictionary?

At every index we need to quickly answer: "how many times have I seen this running sum before?"

A `dict` that stores `running sum -> times seen` answers this in `O(1)` time on average.

A `set` is not enough. Because of zeroes and negative numbers, the same running sum can appear many times. Each earlier appearance is a different start point, so it gives a different subarray. We need the count, not just "seen or not seen".

### Why Start the Dictionary With `{0: 1}`?

A subarray that starts at index `0` has nothing before it. Its "earlier running sum" is the sum of an empty prefix, which is `0`.

So we start with `sum_count = {0: 1}` before the loop. Without it, `[2, -1, 2]` in the example above would be missed.

### Why Look Up Before Adding?

At each index we first look up `running_sum - k`, and only then store the current `running_sum` in the dictionary.

If we stored it first, then for `k = 0` the current running sum would match itself. That would count an empty subarray, which is not allowed.

### Dry Run

`nums = [2, -1, 2, 1]`, `k = 3`

| Index | num | running_sum | Look for `running_sum - k` | Times seen | count | Dictionary after this step |
|:---:|:---:|:---:|:---:|:---:|:---:|:---|
| start | - | 0 | - | - | 0 | `{0: 1}` |
| 0 | 2 | 2 | -1 | 0 | 0 | `{0: 1, 2: 1}` |
| 1 | -1 | 1 | -2 | 0 | 0 | `{0: 1, 2: 1, 1: 1}` |
| 2 | 2 | 3 | 0 | 1 | 1 | `{0: 1, 2: 1, 1: 1, 3: 1}` |
| 3 | 1 | 4 | 1 | 1 | 2 | `{0: 1, 2: 1, 1: 1, 3: 1, 4: 1}` |

At index `2`, running sum `0` was seen once (the empty prefix), so `[2, -1, 2]` is counted.

At index `3`, running sum `1` was seen once (after index `1`), so `[2, 1]` is counted.

Final answer: `2`.

One more quick check with `nums = [0, 0, 0]` and `k = 0`. The running sum stays `0` the whole time, so we find it 1, then 2, then 3 times. Answer: `1 + 2 + 3 = 6`.

A set would only give `3` here. This is why we store counts.

### Python Code

```python
from typing import List


class SubarraySumEqualsK:

    def __init__(self):
        pass

    def subarraySum(self, nums: List[int], k: int) -> int:
        """
        Returns how many continuous subarrays have a sum of exactly k.
        Uses running sums and a dict that counts how often each sum was seen.
        """
        # running sum -> how many times we have seen it so far.
        # {0: 1} is the empty prefix, needed for subarrays starting at index 0.
        sum_count = {0: 1}

        running_sum = 0
        count = 0

        for num in nums:
            running_sum += num

            # Each earlier running sum equal to (running_sum - k) marks the
            # start of a subarray that ends here and adds up to exactly k
            count += sum_count.get(running_sum - k, 0)

            # Store the current running sum only after the lookup,
            # so we never count an empty subarray
            sum_count[running_sum] = sum_count.get(running_sum, 0) + 1

        return count
```

### Complexity

- **Time:** `O(n)`. Each element is visited once, and each dictionary operation takes `O(1)` on average.
- **Space:** `O(n)`. If every running sum is different, the dictionary stores up to `n + 1` entries.