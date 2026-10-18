# Add Values and Fetch Smallest from Min Heap in Java

#### Problem Statement

[https://codezym.com/question/486-min-heap-add-fetch](https://codezym.com/question/486-min-heap-add-fetch)

Keep the smallest value at the front of a list by treating the list as a binary min-heap. After adding a value, move it upward until its parent is no larger. After removing the smallest value, move the last value to the front and push it downward. Each update follows one path through the heap, giving `O(log n)` amortized time per operation.

## Start with a simple list

We could append every value to an unsorted list. To fetch the smallest value, scan the list and remove one occurrence.

Adding takes `O(1)` amortized time, but fetching takes `O(n)` time. Repeated fetches become expensive, so we need a way to keep the minimum ready without sorting the whole list.

## Store a binary heap in the list

Use a `List<Integer>` backed by an `ArrayList`. We need fast access by index, updates by index, and insertion or removal at the end.

Imagine the values arranged in tree levels, filled from left to right. For a value at index `i`:

- Its parent is at `(i - 1) / 2`, when `i > 0`.
- Its left child is at `2 * i + 1`.
- Its right child is at `2 * i + 2`.

The heap rule is simple: every parent is no greater than its children. Following this rule from the root to any value shows why index `0` holds a minimum.

The rest of the list does not need to be sorted. Duplicate values occupy separate positions, and equal parent and child values are allowed.

## Add a value: move upward

Append the value at the end. This keeps every tree level filled in order, but the new value might be smaller than its parent.

While that happens, swap it with its parent and continue upward. Stop when it reaches the root or its parent is no greater. This is called **sift-up**.

For example, adding `14`, `6`, and `10` produces `[6, 14, 10]`. When `6` is added, it swaps with `14`. Adding `10` needs no swap because its parent is `6`.

## Fetch the smallest value: move downward

Save the value at index `0`, then remove the last value. If the list is now empty, return the saved minimum.

Otherwise, put the removed last value at index `0`. Removing the last position avoids shifting all the remaining elements.

Compare the replacement with its children. If it is larger than the smaller child, swap with that child and continue downward. Stop when it has no children or is no greater than the smaller child. This is called **sift-down**.

Always choose the smaller child. Choosing the larger child could leave the smaller one below a parent that is too large.

For `[6, 14, 10]`, fetching saves `6` and moves `10` to the root. The heap becomes `[10, 14]`. Adding `3` then gives `[3, 14, 10]`, so the remaining fetches return `3`, `10`, and `14`.

## Why this works

Before insertion, all existing parent-child relationships are valid. Appending can break the rule only between the new value and its parent. Sift-up repairs that relationship and continues along the only path that may still need fixing.

During fetching, moving the last value to the root leaves the subtrees valid. Swapping with the smaller child fixes the current parent and moves the possible violation downward. When the process stops, the whole heap follows the rule again.

Therefore, every fetch returns and removes one occurrence of the smallest value. The problem guarantees that `fetchFirst()` is called only on a non-empty heap.

## Complexity

A binary heap has `O(log n)` height. Sift-up and sift-down each follow at most one path of that length.

`add` takes `O(log n)` amortized time because an `ArrayList` occasionally grows its backing array. `fetchFirst` takes `O(log n)` worst-case time. For heaps of size zero or one, the work is constant.

Storage is `O(n)`, with `O(1)` extra working space per operation.

## Java code

```java
import java.util.ArrayList;
import java.util.List;

public class MinHeap {
    List<Integer> data;

    public MinHeap() {
        data = new ArrayList<>();
    }

    public void add(int value) {
        data.add(value);
        int index = data.size() - 1;

        // Move the new value up while its parent is larger.
        while (index > 0) {
            int parent = (index - 1) / 2;
            if (data.get(parent) <= data.get(index)) {
                break;
            }

            swap(index, parent);
            index = parent;
        }
    }

    public int fetchFirst() {
        int minimum = data.get(0);
        int last = data.remove(data.size() - 1);

        // Removing the only value leaves an empty heap.
        if (data.isEmpty()) {
            return minimum;
        }

        data.set(0, last);
        int index = 0;

        // Move the replacement down until the heap rule holds.
        while (2 * index + 1 < data.size()) {
            int left = 2 * index + 1;
            int right = left + 1;
            int smaller = left;

            if (right < data.size() && data.get(right) < data.get(left)) {
                smaller = right;
            }

            if (data.get(index) <= data.get(smaller)) {
                break;
            }

            swap(index, smaller);
            index = smaller;
        }

        return minimum;
    }

    void swap(int first, int second) {
        int value = data.get(first);
        data.set(first, data.get(second));
        data.set(second, value);
    }
}
```
