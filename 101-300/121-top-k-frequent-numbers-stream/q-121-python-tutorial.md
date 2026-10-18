# Top K Frequent Numbers in Python

#### Problem Statement
[https://codezym.com/question/121-top-k-frequent-numbers-stream](https://codezym.com/question/121-top-k-frequent-numbers-stream)

Keep the current top `k` numbers in a small heap, with the weakest selected number at its root. A frequency map counts every insertion. Another map records each selected number's heap index, so updates are fast and membership checks take one map lookup.


## Start with a simple scan

Count insertions in a map. For `isTopK(num)`, reject unseen numbers. Otherwise, scan all distinct numbers and count those with higher frequency, or equal frequency and a smaller value. Return `True` if fewer than `k` numbers rank ahead.


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


An updated selected number becomes stronger. Its parent relationship stays valid, but a weaker child may need to move above it. Use `sift_down` to fix this.


Appending uses `sift_up`. Replacing the root uses `sift_down`. Each helper follows one path through the heap.


`isTopK(num)` checks `position` without changing state. Unseen numbers are absent. If fewer than `k` distinct numbers exist, every inserted number is selected.


## Walk through an example

Let `k = 2` and insert `5, 7, 7, 3, 3`. These lists show ranking order.

- After `5, 7, 7`, the top two are `[7, 5]`, with frequencies `2` and `1`.
- Insert `3`. It ties with `5` at frequency `1` and wins on smaller value. The top two become `[7, 3]`.
- Insert `3` again. Both selected numbers have frequency `2`, so the ranking becomes `[3, 7]`.


Now `isTopK(3)` is `True`. Both `isTopK(5)` and `isTopK(6)` are `False`.


## Why this works

Assume the heap contains the correct selection before an insertion. Only the inserted number improves. All other numbers keep their relative ranking.


A selected number therefore stays selected. An outside number enters exactly when there is room or it outranks the weakest selected number. Heap repair restores the weakest number at the root. This preserves the correct selection after every insertion.


## Complexity

Let `D` be the number of distinct inserted numbers. Map operations take expected constant time, and list appends take amortized constant time.

- `insert`: `O(log(k + 1))` amortized time. A heap repair crosses at most this many levels.
- `isTopK`: expected `O(1)` time.
- Space: `O(D)`. The frequency map stores `D` counts, and the heap and index map each store at most `min(k, D)` entries.


## Python Code

The dataclass creates the constructor `TopKFrequentNumbers(k)`. Each `default_factory` gives a new tracker its own empty map or list.

```python
from dataclasses import dataclass, field


@dataclass
class TopKFrequentNumbers:
    k: int

    # All inserted numbers and their counts.
    frequency: dict[int, int] = field(default_factory=dict, init=False)

    # Only selected numbers, with the weakest at index 0.
    heap: list[int] = field(default_factory=list, init=False)

    # Index of each selected number inside the heap.
    position: dict[int, int] = field(default_factory=dict, init=False)

    def insert(self, num):
        self.frequency[num] = self.frequency.get(num, 0) + 1

        index = self.position.get(num)
        if index is not None:
            # Its rank improved, so it can only move down in this heap.
            self.sift_down(index)
            return

        if len(self.heap) < self.k:
            self.heap.append(num)
            self.position[num] = len(self.heap) - 1
            self.sift_up(len(self.heap) - 1)
            return

        if self.weaker(self.heap[0], num):
            # Replace the weakest selected number with the better candidate.
            del self.position[self.heap[0]]
            self.heap[0] = num
            self.position[num] = 0
            self.sift_down(0)

    def isTopK(self, num):
        return num in self.position

    def weaker(self, first, second):
        first_count = self.frequency[first]
        second_count = self.frequency[second]

        if first_count != second_count:
            return first_count < second_count
        return first > second

    def sift_up(self, index):
        while index > 0:
            parent = (index - 1) // 2
            if not self.weaker(self.heap[index], self.heap[parent]):
                break
            self.swap(index, parent)
            index = parent

    def sift_down(self, index):
        while 2 * index + 1 < len(self.heap):
            child = 2 * index + 1
            right = child + 1

            if (
                right < len(self.heap)
                and self.weaker(self.heap[right], self.heap[child])
            ):
                child = right

            if not self.weaker(self.heap[child], self.heap[index]):
                break
            self.swap(index, child)
            index = child

    def swap(self, first_index, second_index):
        first_number = self.heap[first_index]
        second_number = self.heap[second_index]

        self.heap[first_index] = second_number
        self.heap[second_index] = first_number
        self.position[first_number] = second_index
        self.position[second_number] = first_index
```
