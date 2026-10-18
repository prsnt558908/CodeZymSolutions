# Combine Integer Arrays / Lists in Java

#### Problem Statement
[https://codezym.com/question/127-combine-integer-arrays-lists](https://codezym.com/question/127-combine-integer-arrays-lists)

Read `nums1` first, then `nums2`, and keep only the first occurrence of each number. We can start by checking the result list before adding a number. To make this faster without a set or map, we can sort the numbers' original positions, mark the earliest occurrence of each value, and build the answer in input order.

## What must the answer preserve?

The answer contains every distinct number exactly once. Its order follows first appearances across `nums1`, then `nums2`.

For `nums1 = [9, 8, 7]` and `nums2 = [7, 8, 9, 10]`, the answer is `[9, 8, 7, 10]`. Sorting the answer would change the required order.

Both lists can be empty. Values can be negative, zero, or positive. Sets and maps are not allowed.

Let `N` be the total number of elements in the two input lists.

## Solution 1: Check the result before adding

Start with an empty result. Visit each number in `nums1`, then each number in `nums2`. Add a number only when `result.contains(value)` is false.

This works because the first occurrence is added immediately. Every later occurrence finds the same value already in the result and is skipped.

**Time:** `O(N²)` in the worst case. Each membership check may scan the entire result, which is slow when many values are distinct.

**Space:** `O(N)` for the returned list and `O(1)` extra space beyond it.

### Java code

```java
import java.util.ArrayList;
import java.util.List;

public class CombineIntegerArrays {

    public CombineIntegerArrays() {
    }

    /** Return distinct numbers in first-appearance order. */
    public List<Integer> combineArrays(List<Integer> nums1, List<Integer> nums2) {
        List<Integer> result = new ArrayList<>();

        // Read nums1 before nums2.
        for (int value : nums1) {
            if (!result.contains(value)) {
                result.add(value);
            }
        }

        for (int value : nums2) {
            if (!result.contains(value)) {
                result.add(value);
            }
        }

        return result;
    }
}
```

## Solution 2: Sort positions and mark first occurrences

Repeated list searches cause the slowdown. Sorting lets us group equal values together, but we must also remember their original positions to preserve the output order.

1. Copy `nums1`, followed by `nums2`, into `combined`.
2. Create `order`, containing positions `0` through `N - 1`.
3. Sort these positions by their values in `combined`. For equal values, put the smaller original position first.
4. Mark the first position in each equal-value group using `keep`.
5. Scan `combined` from left to right and add only marked positions.

`combined` gives us fast access by position without changing either input. `order` lets us sort positions while leaving `combined` in input order. The boolean array `keep` records which original positions belong in the answer.

### Walk through an example

For `nums1 = [4, 2, 4]` and `nums2 = [2, 3]`, `combined` is `[4, 2, 4, 2, 3]`.

After sorting positions by value and then by position:

- Value `2` has positions `1, 3`. Mark position `1`.
- Value `3` has position `4`. Mark position `4`.
- Value `4` has positions `0, 2`. Mark position `0`.

The final scan visits marked positions `0, 1, 4`, producing `[4, 2, 3]`.

### Why this works

Sorting puts every occurrence of a value together, with its earliest original position first. We mark exactly that position, so every distinct value is included once. Scanning the marked positions in original order gives the required first-appearance order.

Empty inputs also work because the loops have no positions to process. All helper data is created inside the method, so repeated calls are independent.

**Time:** `O(N log N)`. Sorting takes the most time. Building the lists and scanning the positions take `O(N)`.

**Space:** `O(N)` for the copied list, positions, markers, sorting storage, and result.

### Java code

```java
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CombineIntegerArrays {

    public CombineIntegerArrays() {
    }

    /** Return distinct numbers in first-appearance order. */
    public List<Integer> combineArrays(List<Integer> nums1, List<Integer> nums2) {
        List<Integer> combined = new ArrayList<>(nums1);
        combined.addAll(nums2);
        int total = combined.size();

        Integer[] order = new Integer[total];
        for (int index = 0; index < total; index++) {
            order[index] = index;
        }

        // Group equal values and put the earliest position first.
        Arrays.sort(order, (left, right) -> {
            int byValue = Integer.compare(combined.get(left), combined.get(right));
            if (byValue != 0) {
                return byValue;
            }
            return Integer.compare(left, right);
        });

        // Mark only the earliest position of each distinct value.
        boolean[] keep = new boolean[total];
        for (int index = 0; index < total; index++) {
            int position = order[index];
            int value = combined.get(position);
            if (index == 0 || value != combined.get(order[index - 1])) {
                keep[position] = true;
            }
        }

        // Read original positions to preserve the required output order.
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index < total; index++) {
            if (keep[index]) {
                result.add(combined.get(index));
            }
        }

        return result;
    }
}
```
