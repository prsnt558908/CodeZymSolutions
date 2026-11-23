# Removal Order of Valid Numbers in Python

#### Problem Statement
[https://codezym.com/question/281-removal-order-of-valid-numbers](https://codezym.com/question/281-removal-order-of-valid-numbers)

---

## Core Idea

We keep removing the smallest **valid** number, where valid means strictly bigger than both of its current neighbors.


The key observation: removing a number only affects the numbers right next to it. Its left and right neighbors now become neighbors of each other. Every other number keeps the same neighbors, so it stays valid or not valid exactly as before.


So there is no need to scan the whole list again and again. Instead we:

- keep the numbers in a **linked list** built with two plain lists, so finding neighbors and removing a number takes `O(1)`.
- keep all valid numbers in a **min-heap**, so the smallest one is always on top.
- after each removal, check only the two neighbors and push the ones that became valid.


This turns a slow `O(n^2)` simulation into an `O(n log n)` solution.

---

## Solution 1: Brute Force Simulation

### Idea

The rules already tell us what to do, so let's follow them step by step:

1. Scan the current list and find every valid number.
2. Pick the smallest one. On a tie, pick the one with the lowest index.
3. Remove it and repeat until the list is empty.


The problem treats a missing neighbor at either end as `Integer.MIN_VALUE`, which is `-2**31`. We keep it in a constant `MIN_VALUE`. Every input number is bigger than that, so a missing neighbor never stops a number from being valid.


### Code Snippet

```python
items = list(numbers)
order = []

while items:
    best = -1
    for i in range(len(items)):
        left_value = items[i - 1] if i > 0 else MIN_VALUE
        right_value = items[i + 1] if i + 1 < len(items) else MIN_VALUE
        valid = items[i] > left_value and items[i] > right_value

        # strict "<" keeps the lowest index when two valid numbers are equal
        if valid and (best == -1 or items[i] < items[best]):
            best = i
    order.append(items.pop(best))
return order
```


### Why It Is Slow

We remove `n` numbers, and for each one we scan the whole list. Removing from the middle of a Python list with `pop(i)` also shifts every number after it.


That is `O(n^2)` time. For `n = 100,000` it means billions of steps, which is far too slow.


Most of this work is wasted. Look at what one removal really changes:

```
before:  ...  A  X  B  ...
after:   ...  A  B  ...
```


Only `A` and `B` got a new neighbor. Every other number has the same neighbors as before, so checking it again tells us nothing new.

---

## Solution 2: Linked List + Min-Heap

We fix the slow parts one by one.


### Part 1: A Linked List Using Two Lists

We never really delete anything from a list. Instead, every number remembers its current neighbors:

- `left[i]` : index of the number on the left of `i`, or `-1` if there is none.
- `right[i]` : index of the number on the right of `i`, or `-1` if there is none.


Here `i` is always the **original** index from the input list.


To remove `X`, we simply connect its left neighbor `A` and its right neighbor `B`:

```python
right[A] = B
left[B] = A
```


Why not remove items from a normal list or a `deque`? Both need `O(n)` time to remove something from the middle. With these two lists of neighbor indices, we can jump to any number and unlink it in `O(1)`.


### Part 2: A Min-Heap of Valid Numbers

We keep all valid numbers in a min-heap (using `heapq`) as `(value, index)` pairs. Python compares tuples item by item, so the heap orders them by:

1. Smaller value first.
2. On a tie, smaller index first.


Why a heap? Each round we only need the smallest valid number. `heappop` hands it over in `O(log n)`, while a scan costs `O(n)`.


But the tie rule talks about the lowest **current** index, and current indices shift after every removal. Is the original index good enough?


Yes. Removing a number never changes the order of the numbers that remain. If `P` is to the left of `Q` now, it was also to the left of `Q` at the start. So the lower original index is always the lower current index.


### Part 3: A Valid Number Stays Valid

Once a number becomes valid, it stays valid until it is removed.


Why? If `X` is valid, both its neighbors are smaller than `X`. A neighbor smaller than `X` can never be valid, because it is not bigger than `X`. So neither neighbor can be removed, and `X` keeps the same neighbors until `X` itself is removed.


This keeps the code short:

- A number popped from the heap is always still valid. We never need to skip old entries.
- No number is pushed twice, because nothing around it changes while it waits in the heap.


### Putting It Together

1. Link every number to its neighbors.
2. Push every valid number into the heap.
3. Pop the smallest one, add it to the answer and connect its two neighbors.
4. Check those two neighbors. Push any that became valid.
5. Repeat until the heap is empty.


The problem promises that a valid number always exists while the list is not empty. So the heap runs out only after every number is removed.


### Walkthrough

`numbers = [4, 1, 4, 2]`. The heap holds `(value, original index)` pairs.

| Step | Current list | Heap | Pop | After the pop |
|---|---|---|---|---|
| 1 | `[4, 1, 4, 2]` | `(4,0)` `(4,2)` | `(4,0)` | `1` is still not valid, `4` is on its right |
| 2 | `[1, 4, 2]` | `(4,2)` | `(4,2)` | `1` is still not valid, `2` becomes valid |
| 3 | `[1, 2]` | `(2,3)` | `(2,3)` | `1` becomes valid |
| 4 | `[1]` | `(1,1)` | `(1,1)` | nothing left |


Answer: `[4, 4, 2, 1]`.


In step 1 both `4`s are valid and equal, so the heap picks the one with the lower index `0`.


### Code

```python
import heapq
from typing import List

# Value used for a missing neighbor (Integer.MIN_VALUE in the problem).
MIN_VALUE = -(2**31)


class RemovalOrderOfValidNumbers:

    def __init__(self):
        # All indices are original indices from the input list.
        # values[i] : the number at index i
        # left[i]   : index of the current left neighbor of i (-1 if none)
        # right[i]  : index of the current right neighbor of i (-1 if none)
        self.values = []
        self.left = []
        self.right = []

    def findValidNumberOrder(self, numbers: List[int]) -> List[int]:
        """Returns the numbers in the order they get removed.

        Each round removes the smallest valid number (lowest index on a tie).
        """
        n = len(numbers)
        self.values = list(numbers)

        # Link every number to its neighbors, like a doubly linked list.
        self.left = [i - 1 for i in range(n)]
        self.right = [i + 1 if i + 1 < n else -1 for i in range(n)]

        # Min-heap of (value, index) pairs: smaller value first, then smaller index.
        # Removals never change the order of the remaining numbers,
        # so a smaller original index also means a smaller current index.
        valid_heap = []
        for i in range(n):
            if self.isValid(i):
                heapq.heappush(valid_heap, (self.values[i], i))

        order = []

        while valid_heap:
            value, index = heapq.heappop(valid_heap)
            order.append(value)

            # Unlink the removed number, so its two neighbors become adjacent.
            left_neighbor = self.left[index]
            right_neighbor = self.right[index]
            if left_neighbor != -1:
                self.right[left_neighbor] = right_neighbor
            if right_neighbor != -1:
                self.left[right_neighbor] = left_neighbor

            # Only these two numbers got a new neighbor, so only they can turn valid.
            for neighbor in (left_neighbor, right_neighbor):
                if neighbor != -1 and self.isValid(neighbor):
                    heapq.heappush(valid_heap, (self.values[neighbor], neighbor))

        return order

    def isValid(self, index: int) -> bool:
        """A number is valid if it is strictly greater than both current neighbors.

        A missing neighbor counts as MIN_VALUE.
        """
        left_index = self.left[index]
        right_index = self.right[index]
        left_value = self.values[left_index] if left_index != -1 else MIN_VALUE
        right_value = self.values[right_index] if right_index != -1 else MIN_VALUE
        return self.values[index] > left_value and self.values[index] > right_value
```


### Complexity

- **Time:** `O(n log n)`. Each number goes into the heap once and comes out once, and each heap operation costs `O(log n)`. Unlinking and checking neighbors is `O(1)`.
- **Space:** `O(n)` for the three lists and the heap.