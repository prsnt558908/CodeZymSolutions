# Top K Frequent Numbers in Java

#### Problem Statement
[https://codezym.com/question/121-top-k-frequent-numbers-stream](https://codezym.com/question/121-top-k-frequent-numbers-stream)

Keep the current top `k` numbers in a small heap, with the weakest selected number at its root. A frequency map counts every insertion. Another map records each selected number's heap index, so updates are fast and membership checks take one map lookup.


## Start with a simple scan

Count insertions in a map. For `isTopK(num)`, reject unseen numbers. Otherwise, scan all distinct numbers and count those with higher frequency, or equal frequency and a smaller value. Return `true` if fewer than `k` numbers rank ahead.


Insertion takes expected `O(1)` time, but each query takes `O(D)` for `D` distinct numbers. Repeated scans can be too slow across `100000` calls.


## Keep only the top k numbers

Compare a candidate with the weakest selected number. **Weaker** means lower frequency, or a larger value when frequencies tie. For example, `7` is weaker than `3` when both have frequency `2`.


Use three simple data structures:

- `frequency` stores every number's total count, including numbers that leave the top `k`. They may return later.
- `heap` is a list of at most `k` distinct numbers. Each parent is weaker than its children, placing the weakest number at index `0`.
- `position` maps each selected number to its heap index. It supports membership checks and locating numbers for updates.


The index map avoids searching the heap during updates. Every swap updates both stored indices.


The heap uses zero-based indices. The children of index `i` are `2 * i + 1` and `2 * i + 2`. For a non-root index, the parent is `(i - 1) / 2`, rounded down.


## What happens during insert?

Increase `frequency[num]`. Only this number's rank improves, so only this number can newly enter the top `k`.

1. **Already selected.** Repair the heap from its current index.
2. **Empty slot available.** Append it, record its index, and move it upward if needed.
3. **Heap full.** Replace the root only if the candidate ranks better, then repair the heap.


An updated selected number becomes stronger. Its parent relationship stays valid, but a weaker child may need to move above it. Use `siftDown` to fix this.


Appending uses `siftUp`. Replacing the root uses `siftDown`. Each helper follows one path through the heap.


`isTopK(num)` checks `position` without changing state. Unseen numbers are absent. If fewer than `k` distinct numbers exist, every inserted number is selected.


## Walk through an example

Let `k = 2` and insert `5, 7, 7, 3, 3`. These lists show ranking order.

- After `5, 7, 7`, the top two are `[7, 5]`, with frequencies `2` and `1`.
- Insert `3`. It ties with `5` at frequency `1` and wins on smaller value. The top two become `[7, 3]`.
- Insert `3` again. Both selected numbers have frequency `2`, so the ranking becomes `[3, 7]`.


Now `isTopK(3)` is `true`. Both `isTopK(5)` and `isTopK(6)` are `false`.


## Why this works

Assume the heap contains the correct selection before an insertion. Only the inserted number improves. All other numbers keep their relative ranking.


A selected number therefore stays selected. An outside number enters exactly when there is room or it outranks the weakest selected number. Heap repair restores the weakest number at the root. This preserves the correct selection after every insertion.


## Complexity

Let `D` be the number of distinct inserted numbers. Map operations take expected constant time, and list appends take amortized constant time.

- `insert`: `O(log(k + 1))` amortized time. A heap repair crosses at most this many levels.
- `isTopK`: expected `O(1)` time.
- Space: `O(D)`. The frequency map stores `D` counts, and the heap and index map each store at most `min(k, D)` entries.


## Java Code

```java
import java.util.*;

public class TopKFrequentNumbers {
    int k;

    // All inserted numbers and their counts.
    Map<Integer, Integer> frequency = new HashMap<>();

    // Only selected numbers, with the weakest at index 0.
    List<Integer> heap = new ArrayList<>();

    // Index of each selected number inside the heap.
    Map<Integer, Integer> position = new HashMap<>();

    public TopKFrequentNumbers(int k) {
        this.k = k;
    }

    public void insert(int num) {
        frequency.put(num, frequency.getOrDefault(num, 0) + 1);

        Integer index = position.get(num);
        if (index != null) {
            // Its rank improved, so it can only move down in this heap.
            siftDown(index);
            return;
        }

        if (heap.size() < k) {
            heap.add(num);
            position.put(num, heap.size() - 1);
            siftUp(heap.size() - 1);
            return;
        }

        if (weaker(heap.get(0), num)) {
            // Replace the weakest selected number with the better candidate.
            position.remove(heap.get(0));
            heap.set(0, num);
            position.put(num, 0);
            siftDown(0);
        }
    }

    public boolean isTopK(int num) {
        return position.containsKey(num);
    }

    boolean weaker(int first, int second) {
        int firstCount = frequency.get(first);
        int secondCount = frequency.get(second);

        if (firstCount != secondCount) {
            return firstCount < secondCount;
        }
        return first > second;
    }

    void siftUp(int index) {
        while (index > 0) {
            int parent = (index - 1) / 2;
            if (!weaker(heap.get(index), heap.get(parent))) {
                break;
            }
            swap(index, parent);
            index = parent;
        }
    }

    void siftDown(int index) {
        while (2 * index + 1 < heap.size()) {
            int child = 2 * index + 1;
            int right = child + 1;

            if (right < heap.size()
                    && weaker(heap.get(right), heap.get(child))) {
                child = right;
            }

            if (!weaker(heap.get(child), heap.get(index))) {
                break;
            }
            swap(index, child);
            index = child;
        }
    }

    void swap(int firstIndex, int secondIndex) {
        int firstNumber = heap.get(firstIndex);
        int secondNumber = heap.get(secondIndex);

        heap.set(firstIndex, secondNumber);
        heap.set(secondIndex, firstNumber);
        position.put(firstNumber, secondIndex);
        position.put(secondNumber, firstIndex);
    }
}
```
