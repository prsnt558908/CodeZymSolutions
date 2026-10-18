# Search, Minimum and Rotation Count in a Rotated Sorted Array in Java

#### Problem Statement
[https://codezym.com/question/170-rotation-count-rotated-sorted-array](https://codezym.com/question/170-rotation-count-rotated-sorted-array)

Scan the list once to find where its order drops and the first index of its minimum. The drop divides the list into two sorted sections. Use binary search within each section, checking the earlier section first to return the smallest matching index. Store the minimum index so the other two methods can answer immediately.

## Start with a simple scan

For `searchTarget(target)`, scan from left to right and return the first matching index. For the other methods, scan for the minimum value and its first index.

This works, but each call takes `O(n)` time. The list never changes through these methods, so repeated scans do unnecessary work.

## Improve it: prepare once, then use binary search

### 1. Find the sorted sections

A rotated sorted list has at most one place where a value is smaller than the previous value. Call that position `splitIndex`.

For `[2, 5, 6, 0, 0, 1, 2]`, the drop is from `6` to `0`, at index `3`. The sections are `[2, 5, 6]` and `[0, 0, 1, 2]`.

Both sections are sorted in nondecreasing order. Equal adjacent values do not create a split.

If there is no drop, set `splitIndex` to the list size. The first section then contains the entire list, and the second section is empty.

### 2. Store the first minimum index separately

The problem defines the rotation count as the **first index of the minimum value**. With duplicates, this can differ from `splitIndex`.

For `[1, 1, 2, 3, 1]`, the split is at index `4`, but the first minimum is at index `0`. The required rotation count is `0`.

During the same scan, update `minimumIndex` only when a strictly smaller value appears. Equal values leave the earlier index unchanged.

### 3. Search the earlier section first

Run binary search on indices `0` through `splitIndex - 1`. If it finds the target, return that section's first matching index. Otherwise, search the remaining section.

Inside each binary search, a match is saved, but the search continues to its left. This finds the first occurrence within that section.

Every index in the first section is smaller than every index in the second section. Searching in this order therefore gives the smallest matching index across the whole list.

## Data we store

`numbers` is an `ArrayList` copy of the input. It provides constant-time access by index for binary search and keeps later changes to the caller's list from affecting our stored data.

`splitIndex` marks the second sorted section. `minimumIndex` stores the first minimum position. For an empty list, `minimumIndex` is `-1`.

## Walk through an example

For `[2, 5, 6, 0, 0, 1, 2]`, both stored indices are `3`.

- `searchTarget(2)` returns `0`. The earlier section contains the first occurrence, even though another `2` appears at index `6`.
- `searchTarget(0)` returns `3`. Binary search finds the first `0` in the second section.
- `searchTarget(3)` returns `-1` because neither section contains it.
- `findSmallestElement()` returns `0`.
- `countRotations()` returns `3`.

For an empty list, searching and finding the minimum return `-1`, while the rotation count is `0`. A sorted list or a list of identical values has rotation count `0`.

## Complexity

Let `n` be the list size.

- Constructor: `O(n)` time and `O(n)` space for the stored copy.
- `searchTarget`: `O(log n)` time for a nonempty list, including lists with duplicates. Both searched sections are already sorted.
- `findSmallestElement` and `countRotations`: `O(1)` time.
- Each method uses `O(1)` extra space. Apart from the copied list, only two indices are stored.

## Java code

```java
import java.util.ArrayList;
import java.util.List;

public class RotatedSortedArraySearch {
    List<Integer> numbers;
    int splitIndex;
    int minimumIndex;

    public RotatedSortedArraySearch(List<Integer> numbers) {
        this.numbers = numbers == null
                ? new ArrayList<>()
                : new ArrayList<>(numbers);

        int size = this.numbers.size();
        splitIndex = size;
        minimumIndex = size == 0 ? -1 : 0;

        // Prepare both indices once because the stored list does not change.
        for (int i = 1; i < size; i++) {
            int current = this.numbers.get(i);

            if (current < this.numbers.get(i - 1)) {
                splitIndex = i;
            }

            // A strict comparison preserves the first minimum index.
            if (current < this.numbers.get(minimumIndex)) {
                minimumIndex = i;
            }
        }
    }

    public int searchTarget(int target) {
        // Every index in this section comes before the second section.
        int index = findFirst(target, 0, splitIndex - 1);
        if (index != -1) {
            return index;
        }

        return findFirst(target, splitIndex, numbers.size() - 1);
    }

    public int findSmallestElement() {
        return minimumIndex == -1 ? -1 : numbers.get(minimumIndex);
    }

    public int countRotations() {
        return minimumIndex == -1 ? 0 : minimumIndex;
    }

    // Return the first target index in this sorted, inclusive range.
    int findFirst(int target, int left, int right) {
        int answer = -1;

        while (left <= right) {
            int middle = left + (right - left) / 2;
            int value = numbers.get(middle);

            if (value == target) {
                answer = middle;
                // Keep looking for an earlier occurrence.
                right = middle - 1;
            } else if (value < target) {
                left = middle + 1;
            } else {
                right = middle - 1;
            }
        }

        return answer;
    }
}
```
