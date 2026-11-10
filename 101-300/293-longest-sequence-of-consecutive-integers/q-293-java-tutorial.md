# Longest Sequence of Consecutive Integers in Java

#### Problem Statement
[https://codezym.com/question/293-longest-sequence-of-consecutive-integers](https://codezym.com/question/293-longest-sequence-of-consecutive-integers)

We get an unsorted list of integers and need the length of the longest sequence of values like `2, 3, 4, 5, 6`, where each value is exactly one more than the one before it.

Sorting would bring the values of each sequence together, but sorting takes `O(n log n)` time and the problem asks for `O(n)`.

The core idea is to replace sorting with a **hash set**, which tells us if a number is present in `O(1)` time.

With the set, we start counting only from numbers that **begin** a sequence, meaning numbers whose `num - 1` is not in the set. This way every sequence is counted exactly once, and the whole solution runs in `O(n)`.

We will build up to it in three small steps: sort first, then swap sorting for a set, then remove the repeated work.

---

## Understanding the Problem

Take `nums = [8, 3, 5, 2, 6, 4, 20]`. In sorted order the values are `2, 3, 4, 5, 6, 8, 20`, which form three sequences:

- `2, 3, 4, 5, 6` : length 5
- `8` : length 1
- `20` : length 1

So the answer is `5`.

Duplicates do not make a sequence longer. `[10, 10, 11]` has the sequence `10, 11`, so its answer is `2`. An empty list has answer `0`.

---

## Approach 1: Sort and Count

After sorting, the values of each sequence sit next to each other. We walk from left to right and compare each value with the one before it:

- **Same value:** it is a duplicate, skip it.
- **One more:** the sequence continues, so its length grows by 1.
- **Anything else:** there is a gap, so a new sequence starts.

```java
public int longestConsecutive(List<Integer> nums) {
    // Copy first, the input list may be read-only (for example List.of)
    List<Integer> sorted = new ArrayList<>(nums);
    Collections.sort(sorted);

    int longest = 0;
    int length = 0;
    for (int i = 0; i < sorted.size(); i++) {
        int num = sorted.get(i);
        if (i > 0 && num == sorted.get(i - 1)) {
            continue; // duplicate: skip it
        }
        if (i > 0 && num == sorted.get(i - 1) + 1) {
            length++; // the sequence continues
        } else {
            length = 1; // gap: a new sequence starts
        }
        longest = Math.max(longest, length);
    }
    return longest;
}
```

**Problem:** sorting costs `O(n log n)`. To reach `O(n)`, sorting has to go.

---

## Approach 2: Hash Set, Count From Every Number

We sorted only to find "the next number" quickly. A `HashSet` can answer "is `x` present?" in `O(1)` time on average, with no sorting at all.

Why a set? A list would need `O(n)` time for every search. We do not need to store anything with each value, so a map is not needed either. As a bonus, the set removes duplicates for us.

So for every number `num`, we check `num + 1`, `num + 2`, ... in the set until one is missing.

```java
public int longestConsecutive(List<Integer> nums) {
    Set<Integer> numSet = new HashSet<>(nums);
    int longest = 0;

    for (int num : numSet) {
        // walk forward from every number, even from the middle of a sequence
        int length = 1;
        while (numSet.contains(num + length)) {
            length++;
        }
        longest = Math.max(longest, length);
    }
    return longest;
}
```

**Problem:** the same sequence gets counted again and again. For `[1, 2, 3, 4, 5]`, we walk 5 steps from `1`, then 4 steps from `2`, then 3 steps from `3`, and so on.

For one long sequence of `n` numbers, this adds up to about `n² / 2` steps. With `n = 100,000` that is around 5 billion steps, which is far too slow.

---

## Approach 3: Count Only From the Start of a Sequence (Optimal)

In the example above, walking from `2`, `3`, `4` or `5` is wasted work, because the walk from `1` already covers the whole sequence.

So we walk only from the **first number of a sequence**. One check is enough to spot it:

> If `num - 1` is in the set, then `num` is not a start. Skip it.

When `num - 1` is missing, `num` is a start, and we walk forward from it just like in Approach 2.

### Dry Run

For `nums = [8, 3, 5, 2, 6, 4, 20]`, the set is `{2, 3, 4, 5, 6, 8, 20}`.

| num | Is `num - 1` in set? | Action | Length |
|:---:|:---:|:---|:---:|
| 2 | No | Start. Walk through 3, 4, 5, 6 | 5 |
| 3 | Yes | Skip | - |
| 4 | Yes | Skip | - |
| 5 | Yes | Skip | - |
| 6 | Yes | Skip | - |
| 8 | No | Start. 9 is missing | 1 |
| 20 | No | Start. 21 is missing | 1 |

The longest length is `5`.

A set does not keep its values in order, and that is fine. Each sequence is counted from its start, no matter when the loop reaches it. The table is sorted only to make it easy to read.

### Loop Over the Set, Not the List

This small detail matters. If we loop over the original list, a start value that appears many times gets walked many times.

For example, if `1` appears 50,000 times and `2` to `50,001` appear once each, we would walk the same long sequence 50,000 times. That is 2.5 billion steps. The set holds each value only once, so this cannot happen.

### Java Code

```java
import java.util.*;

public class LongestConsecutive {

    public LongestConsecutive() {
    }

    /**
     * Returns the length of the longest sequence of consecutive values in nums.
     * Time: O(n), Space: O(n)
     */
    public int longestConsecutive(List<Integer> nums) {
        // The set removes duplicates and answers "is x present?" in O(1)
        Set<Integer> numSet = new HashSet<>(nums);
        int longest = 0;

        // Loop over the set, not the list, so each value is handled only once
        for (int num : numSet) {
            // num - 1 exists, so num is in the middle of a sequence. Skip it.
            if (numSet.contains(num - 1)) {
                continue;
            }

            // num is the start of a sequence.
            // Check num + 1, num + 2, ... until a value is missing.
            int length = 1;
            while (numSet.contains(num + length)) {
                length++;
            }

            longest = Math.max(longest, length);
        }

        return longest;
    }
}
```

### Complexity

- **Time: `O(n)`.** Building the set takes `O(n)`. In the loop, every number does one `num - 1` check. Each forward walk covers one sequence, and no two sequences share a number, so all walks together visit each number only once. That is about `2n` set lookups in total.
- **Space: `O(n)`** for the set.