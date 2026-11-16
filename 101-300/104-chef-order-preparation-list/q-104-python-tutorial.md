# Chef Order Preparation List in Python

#### Problem Statement
[https://codezym.com/question/104-chef-order-preparation-list](https://codezym.com/question/104-chef-order-preparation-list)

---

An order is eligible when it is bigger than all of its current neighbors, like a small **peak** in the list. The chef always removes the smallest eligible order, and every removal changes the list, so new orders can become eligible. The key observation is simple: removing an order only changes the neighbors of the two orders right next to it. Everything else stays the same.


So we keep all eligible orders in a **min-heap**, which always gives us the smallest one quickly. We keep the list itself as a **doubly linked list** built from two plain lists, so we can remove an order and find its new neighbors in `O(1)`. After every removal, we only re-check those two neighbors. We start with a direct brute force simulation and then fix its slow parts one by one.

---

## Understanding the Problem

The problem gives four rules for eligibility. They are really just one rule:


> An order is eligible if it is **bigger than every neighbor it currently has**.


- The first order has no left neighbor, so only its right neighbor matters.
- The last order has no right neighbor, so only its left neighbor matters.
- A middle order must be bigger than both neighbors.
- A single order has no neighbors at all, so it is eligible automatically.

---

## Approach 1: Brute Force

Just do exactly what the chef does:

1. Scan the whole list and find the smallest eligible order.
2. Remove it and add it to the answer.
3. Repeat until the list is empty.

```python
current = list(orderIds)
result = []

while current:
    n = len(current)
    best = -1  # position of the smallest eligible order
    for i in range(n):
        bigger_than_left = i == 0 or current[i] > current[i - 1]
        bigger_than_right = i == n - 1 or current[i] > current[i + 1]
        if bigger_than_left and bigger_than_right:
            if best == -1 or current[i] < current[best]:
                best = i
    result.append(current.pop(best))  # remove by position
```

### Why is it slow?

- Every step scans the whole list to find eligible orders: `O(n)`.
- Removing from the middle of a list with `pop(i)` shifts every element after it: `O(n)`.
- We repeat this `n` times, so the total is `O(n^2)`.


With up to `100,000` orders, that is billions of operations. Far too slow.

---

## Approach 2: Min-Heap + Doubly Linked List

Let's fix the slow parts one at a time.

### Fix 1: Only two orders can change after a removal

Whether an order is eligible depends only on its two neighbors.


When we remove an order, only the order on its left and the order on its right get a new neighbor (they now sit next to each other). Every other order keeps the same neighbors, so its status cannot change.


So after a removal, we re-check just **those two orders**, not the whole list.

### Fix 2: A min-heap to get the smallest eligible order

We need the smallest eligible order at every step, and new eligible orders keep appearing. A **min-heap** (the `heapq` module) is made exactly for this:

- `heappush()` puts a new eligible order in, in `O(log n)`.
- `heappop()` takes out the smallest one, in `O(log n)`.


We push `(order id, position)` pairs. The heap compares pairs by order id first, so the smallest order id is always on top. The position tells us where that order sits in the list.

### Fix 3: A doubly linked list to remove an order in `O(1)`

After a removal we need the new neighbors fast, and we do not want to shift elements around. A **doubly linked list** gives us both. We build it with two plain lists:

- `left[pos]`: position of the nearest remaining order on the left, `-1` if none.
- `right[pos]`: position of the nearest remaining order on the right, `-1` if none.


To remove the order at `pos`, we simply connect its two neighbors to each other (skipping a side if it is `-1`):

```python
self.right[left_pos] = right_pos
self.left[right_pos] = left_pos
```


Why not `collections.deque`? It is fast only at its two ends. Removing from the middle is still `O(n)`.

### Why an eligible order stays eligible

If an order is eligible, both its neighbors are **smaller** than it. A smaller neighbor can never be eligible, because it has a bigger neighbor (our order). So no neighbor can be removed before our order.


This means **once an order becomes eligible, it stays eligible until the chef picks it**. Two nice results:

- The heap never holds an outdated entry, so we never need to skip or delete anything from it.
- The two neighbors we re-check were smaller than the removed order, so they were not eligible before. No order is ever added to the heap twice.

### Putting it together

1. Build the `left` and `right` lists.
2. Add every order that is eligible at the start to the heap.
3. While the heap is not empty:
   - Take out the smallest order and add it to the answer.
   - Unlink it from the list.
   - Re-check its old left and right neighbors. Add any that just became eligible.

### Dry Run

`orderIds = [5, 1, 4, 2, 3]`

| Step | Removed | List after removal | Neighbors re-checked | Heap after step (order ids) |
|------|---------|--------------------|----------------------|-----------------------------|
| Start | | `[5, 1, 4, 2, 3]` | | `3, 4, 5` |
| 1 | `3` | `[5, 1, 4, 2]` | `2`: not eligible | `4, 5` |
| 2 | `4` | `[5, 1, 2]` | `1`: not eligible, `2`: eligible | `2, 5` |
| 3 | `2` | `[5, 1]` | `1`: not eligible | `5` |
| 4 | `5` | `[1]` | `1`: eligible (alone) | `1` |
| 5 | `1` | `[]` | | empty |


Answer: `[3, 4, 2, 5, 1]`

### Python Code

```python
import heapq
from typing import List


class ChefOrderPreparation:

    def __init__(self):
        # For left and right, -1 means there is no neighbor on that side
        self.ids = []    # ids[pos] = order id at position pos
        self.left = []   # left[pos] = nearest remaining position on the left
        self.right = []  # right[pos] = nearest remaining position on the right

    def getPreparationOrder(self, orderIds: List[int]) -> List[int]:
        """
        Repeatedly removes the smallest eligible order and
        returns the order ids in the order they were removed.
        """
        n = len(orderIds)
        self.ids = list(orderIds)

        # Link every position to its neighbors, like a doubly linked list
        self.left = [pos - 1 for pos in range(n)]
        self.right = [pos + 1 if pos + 1 < n else -1 for pos in range(n)]

        # Min-heap of (order id, position) pairs for eligible orders.
        # Pairs compare by order id first, so the smallest id is on top.
        eligible = []
        for pos in range(n):
            if self.isEligible(pos):
                heapq.heappush(eligible, (self.ids[pos], pos))

        result = []
        while eligible:
            order_id, pos = heapq.heappop(eligible)
            result.append(order_id)

            # Remove pos from the list: link its two neighbors to each other
            left_pos = self.left[pos]
            right_pos = self.right[pos]
            if left_pos != -1:
                self.right[left_pos] = right_pos
            if right_pos != -1:
                self.left[right_pos] = left_pos

            # Only these two orders got a new neighbor,
            # so only they can become eligible
            if left_pos != -1 and self.isEligible(left_pos):
                heapq.heappush(eligible, (self.ids[left_pos], left_pos))
            if right_pos != -1 and self.isEligible(right_pos):
                heapq.heappush(eligible, (self.ids[right_pos], right_pos))

        return result

    def isEligible(self, pos: int) -> bool:
        """
        An order is eligible if no current neighbor is bigger than it.
        A missing neighbor (-1) never blocks it.
        """
        left_pos = self.left[pos]
        right_pos = self.right[pos]
        if left_pos != -1 and self.ids[left_pos] > self.ids[pos]:
            return False
        if right_pos != -1 and self.ids[right_pos] > self.ids[pos]:
            return False
        return True
```

### Complexity

- **Time:** `O(n log n)`. Each order is added to the heap once and taken out once. Each linked list update is `O(1)`.
- **Space:** `O(n)` for the lists, the heap and the answer.

---

## Key Takeaways

- An order's status depends only on its neighbors, so after a removal only **two** orders need a re-check.
- "Always pick the smallest" is a strong hint for a **min-heap**.
- "Remove from the middle and find the new neighbors" is a strong hint for a **doubly linked list**. Two lists, `left` and `right`, are the easiest way to build one.