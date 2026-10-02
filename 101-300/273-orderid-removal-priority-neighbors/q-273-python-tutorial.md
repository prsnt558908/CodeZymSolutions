# Order ID Removal Priority Based on Neighbors in Python

#### Problem Statement
[https://codezym.com/question/273-orderid-removal-priority-neighbors](https://codezym.com/question/273-orderid-removal-priority-neighbors)

At every step we remove the smallest "peak", an order id that is bigger than all of its current neighbors. The core idea is that removing one order changes the neighbors of only two orders: the one just before it and the one just after it. Every other order keeps the same neighbors, so its eligibility does not change.

So instead of rescanning the whole list after every removal, we only recheck those two orders. We store the list as a doubly linked list built from two plain lists (`left` and `right`), so removing from the middle takes O(1) time. We keep all eligible orders in a min-heap, so the smallest one is always ready on top.

We will start with a simple brute force simulation, see why it is slow, and then fix its slow parts one by one.

---

## Problem in Simple Words

You get a list of unique order ids. Think of each id as the height of a hill. An order is **eligible** when it is a peak, which means it is bigger than every neighbor it currently has.

- An order in the middle must be bigger than both its left and right neighbors.
- The first and last orders have only one neighbor and must be bigger than it.
- If only one order is left, it is eligible.

At each step, remove the **smallest** eligible order. Keep going until the list is empty, and return the ids in the order they were removed.

**Example:** `orderIds = [8, 3, 6, 1, 5]`

| Step | Current list | Eligible orders | Removed |
|------|--------------|-----------------|---------|
| 1 | 8, 3, 6, 1, 5 | 8, 6, 5 | 5 |
| 2 | 8, 3, 6, 1 | 8, 6 | 6 |
| 3 | 8, 3, 1 | 8 | 8 |
| 4 | 3, 1 | 3 | 3 |
| 5 | 1 | 1 | 1 |

Output: `[5, 6, 8, 3, 1]`

Look at step 4. Order 3 was not eligible in the first three steps because a bigger order sat next to it. Once 8 was removed, 3 became the first order, and it is bigger than its only neighbor 1.

One handy fact: the biggest order in the list is always eligible. So there is always something to remove, and the process never gets stuck.

---

## Solution 1: Brute Force Simulation

Just follow the rules literally:

1. Scan the whole list and find the smallest eligible order.
2. Remove it and add it to the answer.
3. Repeat until the list is empty.

We work on a copy of the input, so the caller's list is never changed.

```python
from typing import List


class OrderIdRemovalPriority:
    def __init__(self):
        pass

    def prioritizeOrders(self, orderIds: List[int]) -> List[int]:
        orders = list(orderIds)  # work on a copy so that the input list is not changed
        result = []

        while orders:
            # scan the whole list and remember the smallest eligible order
            best = -1
            for i in range(len(orders)):
                if self.is_eligible(orders, i) and (best == -1 or orders[i] < orders[best]):
                    best = i
            # remove it by index and add it to the answer
            result.append(orders.pop(best))
        return result

    # An order is eligible if it is bigger than every neighbor it currently has
    def is_eligible(self, orders: List[int], i: int) -> bool:
        bigger_than_left = i == 0 or orders[i] > orders[i - 1]
        bigger_than_right = i == len(orders) - 1 or orders[i] > orders[i + 1]
        return bigger_than_left and bigger_than_right
```

### What is slow here?

The list can hold up to 100,000 orders, and we do one step per order.

- **Finding the smallest eligible order** scans the whole list. That is O(n) per step.
- **`orders.pop(best)`** shifts every element after that index one place to the left. That is also O(n) per step.

O(n) work for each of the n steps gives O(n²) in total, which is billions of operations for 100,000 orders. Way too slow.

**Complexity:** Time O(n²), Space O(n).

---

## Solution 2: Linked List + Min-Heap (Optimal)

We fix the slow parts one at a time.

### Step 1: Remove from the middle in O(1) with a doubly linked list

Removing from a linked list is cheap. We just connect the left neighbor directly to the right neighbor, and nothing gets shifted.

We do not need a `Node` class for this. Two plain lists, indexed by the original position, are enough:

- `left[i]`: position of the current left neighbor of order `i`, or `-1` if it has none.
- `right[i]`: position of the current right neighbor of order `i`, or `-1` if it has none.

Removing the order at position `pos` is just two updates:

```python
left_pos = self.left[pos]
right_pos = self.right[pos]
if left_pos != -1:
    self.right[left_pos] = right_pos
if right_pos != -1:
    self.left[right_pos] = left_pos
```

**Why we need it:** after many removals we still need the *current* neighbors of any order, and these two lists give them in O(1).

### Step 2: Recheck only the two neighbors

When an order is removed, who gets a new neighbor? Only the order on its left and the order on its right.

Every other order still has exactly the same neighbors as before, so its eligibility cannot change. So after each removal we recheck only **2 orders** instead of scanning the whole list.

### Step 3: Keep eligible orders in a min-heap

We need the smallest eligible order at every step, and new eligible orders keep showing up. A min-heap (the `heapq` module) fits this perfectly:

- `heappop()` returns the smallest eligible order in O(log n).
- `heappush()` inserts a newly eligible order in O(log n).

We push tuples `(order_id, position)`. Tuples are compared by their first value, so the smallest order id is always on top. Ids are unique, so two tuples never tie. We keep the position in the tuple because we need it to look up `left` and `right`.

A sorted list would also give us the smallest element, but every insert into it shifts elements, which is O(n). The heap does the same job in O(log n).

### Can an order in the heap stop being eligible?

No, and this keeps the code simple. Once an order becomes eligible, it stays eligible until it is removed.

Say order X is eligible. Both its neighbors are smaller than X, so neither of them can be eligible while X sits next to them. That means X's neighbors are never removed before X, and X keeps the same neighbors until its own turn comes.

So the heap never holds an outdated entry, and each order is added to it exactly once.

### Algorithm

1. Copy the ids and build `left` and `right`.
2. Put every order that is eligible right now into the min-heap.
3. While the heap is not empty:
   - Take out the smallest eligible order and add it to the answer.
   - Unlink it from the linked list.
   - Recheck its old left and right neighbors, and push any that became eligible into the heap.
4. Return the answer.

The very last order has no neighbors at all, so it becomes eligible and gets removed too.

### Dry Run

`orderIds = [2, 7, 4, 9, 3, 6]`

| Step | Removed | List after removal | Rechecked | Added to heap | Heap after |
|------|---------|--------------------|-----------|---------------|------------|
| Start | | 2, 7, 4, 9, 3, 6 | all | 7, 9, 6 | 6, 7, 9 |
| 1 | 6 | 2, 7, 4, 9, 3 | 3 | none | 7, 9 |
| 2 | 7 | 2, 4, 9, 3 | 2, 4 | none | 9 |
| 3 | 9 | 2, 4, 3 | 4, 3 | 4 | 4 |
| 4 | 4 | 2, 3 | 2, 3 | 3 | 3 |
| 5 | 3 | 2 | 2 | 2 | 2 |
| 6 | 2 | empty | none | none | empty |

Output: `[6, 7, 9, 4, 3, 2]`

### Code

```python
import heapq
from typing import List


class OrderIdRemovalPriority:
    def __init__(self):
        self.ids = []    # ids[i] = order id at position i of the input
        self.left = []   # left[i] = position of the current left neighbor of i, -1 if none
        self.right = []  # right[i] = position of the current right neighbor of i, -1 if none

    def prioritizeOrders(self, orderIds: List[int]) -> List[int]:
        n = len(orderIds)
        self.ids = list(orderIds)

        # build a doubly linked list where every position knows its two neighbors
        self.left = [i - 1 for i in range(n)]
        self.right = [-1 if i == n - 1 else i + 1 for i in range(n)]

        # min-heap of (order id, position) for eligible orders, the smallest order id stays on top
        eligible = [(self.ids[i], i) for i in range(n) if self.is_eligible(i)]
        heapq.heapify(eligible)

        result = []
        while eligible:
            order_id, pos = heapq.heappop(eligible)
            result.append(order_id)

            # unlink pos, so its two neighbors now sit next to each other
            left_pos = self.left[pos]
            right_pos = self.right[pos]
            if left_pos != -1:
                self.right[left_pos] = right_pos
            if right_pos != -1:
                self.left[right_pos] = left_pos

            # only these two orders got a new neighbor, so only they can become eligible
            if left_pos != -1 and self.is_eligible(left_pos):
                heapq.heappush(eligible, (self.ids[left_pos], left_pos))
            if right_pos != -1 and self.is_eligible(right_pos):
                heapq.heappush(eligible, (self.ids[right_pos], right_pos))
        return result

    # True if the order at position i is bigger than all of its current neighbors
    def is_eligible(self, i: int) -> bool:
        bigger_than_left = self.left[i] == -1 or self.ids[i] > self.ids[self.left[i]]
        bigger_than_right = self.right[i] == -1 or self.ids[i] > self.ids[self.right[i]]
        return bigger_than_left and bigger_than_right
```

### Complexity

- **Time: O(n log n).** Each order enters and leaves the heap exactly once, and each heap operation is O(log n). Unlinking an order and rechecking its two neighbors is O(1).
- **Space: O(n)** for the lists, the heap and the answer.

---

## Summary

| Approach | Time | Space |
|----------|------|-------|
| Brute force simulation | O(n²) | O(n) |
| Linked list + min-heap | O(n log n) | O(n) |

**Key takeaways:**

- If removing an item only affects its direct neighbors, recheck just those neighbors instead of the whole list.
- Two plain lists (`left` and `right`) make a light and fast doubly linked list.
- A min-heap is the natural fit when you keep needing the smallest item from a set that keeps changing.