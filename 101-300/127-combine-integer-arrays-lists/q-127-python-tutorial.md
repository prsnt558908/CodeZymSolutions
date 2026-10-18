# Combine Integer Arrays / Lists in Python

#### Problem Statement
[https://codezym.com/question/127-combine-integer-arrays-lists](https://codezym.com/question/127-combine-integer-arrays-lists)

Read `nums1` first, then `nums2`, and keep only the first occurrence of each number. We can start by checking the result list before adding a number. To make this faster without a set or dictionary, we can sort the numbers' original positions, mark the earliest occurrence of each value, and build the answer in input order.

## What must the answer preserve?

The answer contains every distinct number exactly once. Its order follows first appearances across `nums1`, then `nums2`.

For `nums1 = [9, 8, 7]` and `nums2 = [7, 8, 9, 10]`, the answer is `[9, 8, 7, 10]`. Sorting the answer would change the required order.

Both lists can be empty. Values can be negative, zero, or positive. Sets and dictionaries are not allowed.

Let `N` be the total number of elements in the two input lists.

## Solution 1: Check the result before adding

Start with an empty result. Visit each number in `nums1`, then each number in `nums2`. Add a number only when `value not in result` is true.

This works because the first occurrence is added immediately. Every later occurrence finds the same value already in the result and is skipped.

**Time:** `O(N²)` in the worst case. Each membership check may scan the entire result, which is slow when many values are distinct.

**Space:** `O(N)` for the returned list and `O(1)` extra space beyond it.

### Python code

```python
from typing import List


class CombineIntegerArrays:
    def __init__(self):
        pass

    def combineArrays(self, nums1: List[int], nums2: List[int]) -> List[int]:
        """Return distinct numbers in first-appearance order."""
        result = []

        # Read nums1 before nums2.
        for value in nums1:
            if value not in result:
                result.append(value)

        for value in nums2:
            if value not in result:
                result.append(value)

        return result
```

## Solution 2: Sort positions and mark first occurrences

Repeated list searches cause the slowdown. Sorting lets us group equal values together, but we must also remember their original positions to preserve the output order.

1. Copy `nums1`, followed by `nums2`, into `combined`.
2. Create `order`, containing positions `0` through `N - 1`.
3. Sort these positions by their values in `combined`. For equal values, put the smaller original position first.
4. Mark the first position in each equal-value group using `keep`.
5. Scan `combined` from left to right and add only marked positions.

`combined` gives us access by position without changing either input. `order` lets us sort positions while leaving `combined` in input order. The boolean list `keep` records which original positions belong in the answer.

The sorting key `(combined[index], index)` compares values first and original positions second.

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

### Python code

```python
from typing import List


class CombineIntegerArrays:
    def __init__(self):
        pass

    def combineArrays(self, nums1: List[int], nums2: List[int]) -> List[int]:
        """Return distinct numbers in first-appearance order."""
        combined = nums1 + nums2
        total = len(combined)

        # Group equal values and put the earliest position first.
        order = sorted(range(total), key=lambda index: (combined[index], index))

        # Mark only the earliest position of each distinct value.
        keep = [False] * total
        for index in range(total):
            position = order[index]
            value = combined[position]
            if index == 0 or value != combined[order[index - 1]]:
                keep[position] = True

        # Read original positions to preserve the required output order.
        result = []
        for index in range(total):
            if keep[index]:
                result.append(combined[index])

        return result
```
