# Continuous Subarray Sum Equals K in Java

#### Problem Statement
[https://codezym.com/question/312-continuous-subarray-sum-equals-k](https://codezym.com/question/312-continuous-subarray-sum-equals-k)

The core idea is **running sums**, also called prefix sums. The sum of any subarray is simply the running sum at its end minus the running sum just before its start.

So we walk through the list only once and keep a map that counts how many times each running sum has appeared. At every element, the number of earlier running sums equal to the current running sum minus `k` is exactly the number of valid subarrays ending at that element.

We will start with a simple brute force solution, see why it is slow, and then improve it.

---

## Approach 1: Brute Force

The simplest idea is to try every continuous subarray and count the ones whose sum is exactly `k`.

Fix a start index. Then move the end index forward one step at a time and add each new element to a running total. This way we never add up the whole subarray again from scratch.

```java
int count = 0;

for (int start = 0; start < nums.size(); start++) {
    int sum = 0;
    for (int end = start; end < nums.size(); end++) {
        sum += nums.get(end); // sum of elements from start to end
        if (sum == k) {
            count++;
        }
    }
}

return count;
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

## Approach 2: Running Sum + HashMap

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

We do not even need the whole `running` array. One variable, `runningSum`, updated as we move forward, is enough.

### Why a HashMap?

At every index we need to quickly answer: "how many times have I seen this running sum before?"

A `HashMap<Integer, Integer>` that stores `running sum -> times seen` answers this in `O(1)` time on average.

A `HashSet` is not enough. Because of zeroes and negative numbers, the same running sum can appear many times. Each earlier appearance is a different start point, so it gives a different subarray. We need the count, not just "seen or not seen".

### Why Start the Map With `0 -> 1`?

A subarray that starts at index `0` has nothing before it. Its "earlier running sum" is the sum of an empty prefix, which is `0`.

So we put `0 -> 1` in the map before the loop starts. Without it, `[2, -1, 2]` in the example above would be missed.

### Why Look Up Before Adding?

At each index we first look up `runningSum - k`, and only then store the current `runningSum` in the map.

If we stored it first, then for `k = 0` the current running sum would match itself. That would count an empty subarray, which is not allowed.

### Dry Run

`nums = [2, -1, 2, 1]`, `k = 3`

| Index | num | runningSum | Look for `runningSum - k` | Times seen | count | Map after this step |
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

### Java Code

```java
import java.util.*;

public class SubarraySumEqualsK {

    public SubarraySumEqualsK() {
    }

    /**
     * Returns how many continuous subarrays have a sum of exactly k.
     * Uses running sums and a map that counts how often each running sum was seen.
     */
    public int subarraySum(List<Integer> nums, int k) {
        // running sum -> how many times we have seen it so far
        Map<Integer, Integer> sumCount = new HashMap<>();

        // Empty prefix with sum 0, needed for subarrays starting at index 0
        sumCount.put(0, 1);

        int runningSum = 0;
        int count = 0;

        for (int num : nums) {
            runningSum += num;

            // Each earlier running sum equal to (runningSum - k) marks the start
            // of a subarray that ends here and adds up to exactly k
            count += sumCount.getOrDefault(runningSum - k, 0);

            // Store the current running sum only after the lookup,
            // so we never count an empty subarray
            sumCount.put(runningSum, sumCount.getOrDefault(runningSum, 0) + 1);
        }

        return count;
    }
}
```

### Complexity

- **Time:** `O(n)`. Each element is visited once, and each map operation takes `O(1)` on average.
- **Space:** `O(n)`. If every running sum is different, the map stores up to `n + 1` entries.

**Why `int` is enough:** the running sum always stays between `-20,000,000` and `20,000,000`, and the answer is at most `n * (n + 1) / 2`, which is about 200 million. Both fit easily in an `int`.