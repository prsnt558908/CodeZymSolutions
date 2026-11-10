# Longest Subarray Sum Equals Zero in Java

#### Problem Statement
[https://codezym.com/question/175-longest-subarray-sum-equals-zero](https://codezym.com/question/175-longest-subarray-sum-equals-zero)

The key idea is the **prefix sum**, which is just a running total of the numbers from the start of the list. If the prefix sum is the same at two different indexes, then the numbers between those indexes add up to `0`. So the task becomes: find two equal prefix sums that are as far apart as possible. A `HashMap` that remembers the **first** index of every prefix sum lets us do this in a single pass.

We will start with a simple brute force solution, see why it is slow, and then improve it.

## Problem in Short

You are given a list of integers. It can have positive numbers, negative numbers and zeros.

Return the length of the longest contiguous subarray whose sum is exactly `0`. If there is no such subarray, return `0`.

For `[15, -2, 2, -8, 1, 7, 10, 23]` the answer is `5`, because `[-2, 2, -8, 1, 7]` adds up to `0`.

## Approach 1: Brute Force

Try every possible start index. From each start, grow the subarray to the right one number at a time and keep adding to `sum`. Whenever `sum` becomes `0`, update the answer.

```java
int maxLength = 0;

for (int start = 0; start < numbers.size(); start++) {
    long sum = 0; // long, because the sum can go beyond the int range

    for (int end = start; end < numbers.size(); end++) {
        sum += numbers.get(end);

        if (sum == 0) {
            maxLength = Math.max(maxLength, end - start + 1);
        }
    }
}
```

This takes `O(n^2)` time and `O(1)` extra space.

**The problem:** the list can have up to `100,000` numbers. Two nested loops check about `n * n / 2` subarrays, which is around **5 billion** checks. That is far too slow.

For every end index, we try every possible start index. Can we find the best start index instantly instead?

## Approach 2: Prefix Sum + HashMap (Optimal)

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

**Why a HashMap?** We do this lookup at every index, so it must be fast. A `HashMap<Long, Integer>` that maps prefix sum -> first index does each lookup in `O(1)` time on average. A plain array would not work here, because prefix sums can be negative or very large.

### Subarrays That Start at Index 0

Take `[3, -3]`. The prefix sums are `3, 0`. The whole list adds up to `0`, but the sum `0` was never seen before index `1`, so we would miss it.

The fix is to put `0 -> -1` in the map before the loop. It simply means: before reading any number, the sum is already `0`, at index `-1`.

Now at index `1`, the prefix sum `0` is found at index `-1`, and the length is `1 - (-1) = 2`.

### Watch Out for Overflow

Each number can be as large as `1,000,000,000` (positive or negative), and there can be `100,000` numbers. So a prefix sum can reach `10^14`, which does not fit in an `int`.

That is why the prefix sum is a `long` and the map key is `Long`. With an `int`, the sum could wrap around, and two different sums could look equal and give a wrong answer.

### Dry Run

Input: `[0, 0, 5, -5, 2]`, expected answer `4`.

| i     | Number | Prefix sum | Seen before? | Action                | maxLength |
|-------|--------|------------|--------------|-----------------------|-----------|
| start |        | 0          |              | store `0 -> -1`       | 0         |
| 0     | 0      | 0          | yes, at `-1` | length `0 - (-1) = 1` | 1         |
| 1     | 0      | 0          | yes, at `-1` | length `1 - (-1) = 2` | 2         |
| 2     | 5      | 5          | no           | store `5 -> 2`        | 2         |
| 3     | -5     | 0          | yes, at `-1` | length `3 - (-1) = 4` | 4         |
| 4     | 2      | 2          | no           | store `2 -> 4`        | 4         |

The answer is `4`, from the subarray `[0, 0, 5, -5]`.

### Java Code

```java
import java.util.*;

public class LongestZeroSumSubarray {

    public LongestZeroSumSubarray() {
    }

    /**
     * Returns the length of the longest contiguous subarray with sum 0.
     * Returns 0 if no such subarray exists.
     */
    public int longestZeroSumSubarray(List<Integer> numbers) {
        // prefix sum -> first index where this prefix sum appeared
        Map<Long, Integer> firstIndex = new HashMap<>();

        // Before reading any number, the sum is 0 at index -1.
        // This lets us count subarrays that start at index 0.
        firstIndex.put(0L, -1);

        long prefixSum = 0; // long, because the sum can go beyond the int range
        int maxLength = 0;

        for (int i = 0; i < numbers.size(); i++) {
            prefixSum += numbers.get(i);

            if (firstIndex.containsKey(prefixSum)) {
                // Same prefix sum seen before, so the numbers
                // after that index, up to i, add up to 0.
                int length = i - firstIndex.get(prefixSum);
                maxLength = Math.max(maxLength, length);
            } else {
                // Save only the first index. It gives the longest subarray later.
                firstIndex.put(prefixSum, i);
            }
        }

        return maxLength;
    }
}
```

### Complexity

- **Time:** `O(n)`. We visit each number once, and each map operation takes `O(1)` on average.
- **Space:** `O(n)`. The map can hold up to `n + 1` different prefix sums.