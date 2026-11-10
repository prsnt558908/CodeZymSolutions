# Stack with Constant-Time Middle Access in Python

#### Problem Statement
[https://codezym.com/question/434-stack-with-constant-time-middle-access](https://codezym.com/question/434-stack-with-constant-time-middle-access)

A normal stack only lets us look at the top. To find the middle, we would have to walk through half of the elements, and that is slow.

The good news is that the middle is always at a spot we can compute from the size alone. So we just need a way to reach that spot without walking.

We will see two ways to do it. The first keeps the elements in a Python `list` and reads the middle directly by its index. The second uses a doubly linked list and keeps a `middle` pointer that moves at most one step on every push or pop.


## Where Is the Middle?

The problem says the middle is at position `floor(n / 2) + 1` from the bottom, counting from 1.

If we count from 0 instead, that is simply index `n // 2`.

For example, in `[8, 3, 14, 6]` we have `n = 4`, so the middle is at index `2`, which is `14`. When `n` is even, index `n // 2` picks the middle element closer to the top, exactly as the problem asks.


## Basic Solution: Walk to the Middle

Suppose we use a `deque` as the stack, with its right end as the top. `push`, `pop` and `getTop` are easy because they only touch the top.

The hard part is `getMiddle`. A `deque` is fast at both ends but slow in the middle, so we have to start at the bottom and skip `size // 2` elements.

```python
from collections import deque


class MiddleStack:
    def __init__(self):
        self.stack = deque()  # left end is the bottom, right end is the top

    def getMiddle(self) -> int:
        # walk from the bottom up to the middle
        it = iter(self.stack)
        for _ in range(len(self.stack) // 2):
            next(it)  # skip the elements below the middle
        return next(it)
```

Writing `self.stack[i]` would not help either. A `deque` also walks internally to reach an index in the middle.

**The problem:** every `getMiddle` call walks through half of the stack, so it takes `O(n)` time.

With up to 100,000 method calls, this can add up to more than a billion steps. We need to reach the middle without walking.


## Solution 1: List with Direct Index Access

The basic solution is slow only because a `deque` cannot jump to the middle. A Python `list` can.

So we keep the elements from bottom to top in a `list`:

- `push` appends the value at the end.
- `pop` removes and returns the last element.
- `getTop` reads `items[-1]`.
- `getMiddle` reads `items[len(items) // 2]`.

**Why a `list`?** A Python list is a dynamic array. It keeps the elements in order and reads any index in `O(1)`. Adding and removing at the end is also cheap, and that is all a stack needs.

### Python Code

```python
class MiddleStack:
    def __init__(self):
        # Elements from bottom (index 0) to top (last index)
        self.items = []

    def push(self, value: int) -> None:
        self.items.append(value)

    def pop(self) -> int:
        return self.items.pop()

    def getMiddle(self) -> int:
        # Position floor(n / 2) + 1 (from 1) is index n // 2 (from 0)
        return self.items[len(self.items) // 2]

    def getTop(self) -> int:
        return self.items[-1]
```

### Complexity

- `pop`, `getMiddle`, `getTop`: `O(1)`.
- `push`: `O(1)` amortized. Once in a while the list runs out of room and may copy every element to a bigger block of memory, so that one push is slow.
- Space: `O(n)`.

That occasional slow push is the only weak spot. The next solution never copies elements.


## Solution 2: Doubly Linked List with a Middle Pointer

Instead of searching for the middle when asked, we can **keep a pointer on the middle node at all times**.

Each push or pop changes the size by one, so the middle moves by at most one step. Moving one step is just `middle.next` (up) or `middle.prev` (down).

**Why a doubly linked list?** Each `Node` holds one value and two links: `prev` points to the node below and `next` points to the node above.

- On `pop`, the `top` pointer moves down, so we need `prev`.
- The `middle` pointer moves up on some pushes and down on some pops, so it needs both `next` and `prev`.

A singly linked list only has one direction, so it is not enough.

### When Does the Middle Move?

Let's trace Example 1 and add one extra `pop()` at the end.

| Operation | Stack (bottom to top) | Size | Middle index | Middle pointer |
|---|---|---|---|---|
| `push(8)` | `[8]` | 1 | 0 | set to 8 |
| `push(3)` | `[8, 3]` | 2 | 1 | moves up to 3 |
| `push(14)` | `[8, 3, 14]` | 3 | 1 | stays on 3 |
| `push(6)` | `[8, 3, 14, 6]` | 4 | 2 | moves up to 14 |
| `pop()` | `[8, 3, 14]` | 3 | 1 | moves down to 3 |
| `pop()` | `[8, 3]` | 2 | 1 | stays on 3 |

The middle index is `size // 2`. It goes up only when a push makes the size even, and it goes down only when a pop starts from an even size.

So the rules are:

- **push:** put the new node on top. If the new size is even, move `middle` one step up.
- **pop:** if the current size is even, move `middle` one step down. Then remove the top node.
- The first push makes the new node the middle. When the last node is popped, `middle` becomes `None`.

This also works when the stack has 2 elements and the middle is the top itself. The middle steps down to the bottom node before the top is removed.

### Python Code

```python
class Node:
    """One element of the stack."""

    def __init__(self, value: int):
        self.value = value
        self.prev = None  # element just below
        self.next = None  # element just above


class MiddleStack:
    def __init__(self):
        self.top = None     # most recently pushed element
        self.middle = None  # element at index size // 2 from the bottom
        self.size = 0

    def push(self, value: int) -> None:
        node = Node(value)
        if self.top is None:
            self.middle = node  # the first element is also the middle
        else:
            self.top.next = node
            node.prev = self.top
        self.top = node
        self.size += 1

        # The size just became even, so the middle moves one step up
        if self.size % 2 == 0:
            self.middle = self.middle.next

    def pop(self) -> int:
        value = self.top.value

        # The size is even and will become odd, so the middle moves down
        if self.size % 2 == 0:
            self.middle = self.middle.prev

        self.top = self.top.prev
        if self.top is None:
            self.middle = None    # the stack is empty now
        else:
            self.top.next = None  # unlink the removed node
        self.size -= 1
        return value

    def getMiddle(self) -> int:
        return self.middle.value

    def getTop(self) -> int:
        return self.top.value
```

### Complexity

- `push`, `pop`, `getMiddle`, `getTop`: `O(1)` in the worst case. Each call only changes a few pointers.
- Space: `O(n)` for the nodes.


## Which One Should You Use?

**Solution 1** is the shortest and the easiest to explain. It is a perfectly good answer for this problem.

**Solution 2** is the better pick when every single call must be `O(1)`, or when the interviewer adds a follow-up like "also delete the middle element". The linked list can unlink the middle node in `O(1)`, while a `list` would have to shift elements in `O(n)`.