# Design Locker Management System for Warehouse Packages in Python

#### Problem Statement
[https://codezym.com/question/16-design-locker-management-system-warehouse-packages](https://codezym.com/question/16-design-locker-management-system-warehouse-packages)


## Core Idea

This problem comes down to one question: how do we quickly find the smallest free locker of a given size?

The answer is to keep the ids of all free lockers of each size in a **sorted set**. The smallest free id is then always at the front. Occupying a locker takes its id out of the set, and freeing it puts the id back.

We do not need a design pattern here. A few small classes with one job each, plus the right data structures, keep the code short and fast. Later we will see why patterns like State or Strategy only add extra code for this problem.


## A Small Trap: Lexicographic Order

Locker ids are strings. So "smallest" means dictionary order, not number order.

Strings are compared one character at a time, from the left. Compare `M10` and `M2`: both start with `M`. Next, `1` is smaller than `2`. So `M10` comes before `M2`.

Example: lockers `M1` to `M12` exist and only `M1` is occupied. Now `findSlot("M")` returns `M10`, not `M2`.

Python compares strings in exactly this order (`"M10" < "M2"` is `True`), so we need no extra code for it.


## Basic Solution: Check Every Locker

Let's start simple. Keep every locker in one list. Each `Locker` stores its `id`, its `size` and an `occupied` flag.

To find a free locker, walk the whole list and remember the smallest free id of the asked size:

```python
def findSlot(self, size: str) -> str:
    best = ""
    for locker in self.allLockers:
        if locker.size == size and not locker.occupied:
            if best == "" or locker.id < best:
                best = locker.id
    return best
```

`occupySlot` and `freeSlot` also walk the list to find the locker with the given id.


### What is wrong with it?

Every call looks at every locker. There can be 200,000 lockers. With 200,000 calls, that is 40 billion checks, which is far too slow.

There are two slow parts:

1. Finding a locker by its id.
2. Finding the smallest free locker of a size.

Let's fix both.


## Better Solution: Sorted Set of Free Lockers

**Fix 1: a dictionary from id to locker.** With a `lockers` dictionary, a call like `occupySlot("M3")` gets locker `M3` in one step.

**Fix 2: a sorted set of free ids for each size.** We keep one per size, because `findSlot("M")` should only look at `M` lockers.

Python has no built-in sorted set. So we build one from two simple parts:

- `freeIds`, a normal set: tells us if an id is free right now.
- `heap`, a min-heap from `heapq`: keeps the smallest id on top.

Adding an id is easy: put it in both.


### Removing an id: clean up later

Removing is the tricky part. `occupySlot` can be called on any locker, not only the smallest one. A heap cannot remove an item from the middle quickly.

So when a locker is occupied, we only remove its id from `freeIds`. The old id stays in the heap for now.

`findSlot` cleans up as it goes. It looks at the top of the heap. If that id is not in `freeIds`, it is old, so we pop it and look again. The first id that is in `freeIds` is the answer.

This trick is often called **lazy removal**: we clean the heap only when we actually look at it.

One small detail: if a locker is occupied and then freed before its old id is popped, the id is pushed again and sits in the heap twice. That is fine, because we always check `freeIds` before trusting the top.


### The Classes

**`Locker`**: one locker. It stores `id`, `size` and `occupied`. The `occupied` flag tells us in one step if a locker is in use.

**`LockerGroup`**: all lockers of one size. Every size needs its own counter, `nextNumber`, to make new ids (`M1`, `M2`, ...) and its own sorted set of free ids, built from `freeIds` and `heap`. This class keeps all of them together and hides the heap details from the rest of the code.

**`LockerManager`**: the entry point. It holds `groups` (size to `LockerGroup`) and `lockers` (id to `Locker`). It checks the input, picks the right group or locker, and passes the work on.

`groups` has only the 5 valid sizes as keys. So checking a size is just a dictionary lookup. If the size is not found, it is invalid.


### How Each Call Works

- **`addSlot(size)`**: get the group for `size`. If there is none, the size is invalid, so do nothing. Otherwise create the next locker, for example `M3`, save it in `lockers`, add its id to `freeIds` and push it onto the heap.
- **`findSlot(size)`**: get the group. If there is none, return `""`. Otherwise pop old ids from the top of the heap, then return the top id, or `""` if the heap is empty.
- **`occupySlot(lockerId)`**: get the locker. If it does not exist or is already occupied, return `False`. Otherwise mark it occupied, remove its id from `freeIds` and return `True`.
- **`freeSlot(lockerId)`**: get the locker. If it does not exist or is already free, return `False`. Otherwise mark it free, add its id to `freeIds`, push it onto the heap and return `True`.

Note that `findSlot` only looks. It does not take the locker. A locker is taken only when `occupySlot` is called.


### Dry Run (Example 2)

| Call | Result | `freeIds` | `heap` |
|---|---|---|---|
| `addSlot("S")` | creates `S1` | `{S1}` | `[S1]` |
| `addSlot("S")` | creates `S2` | `{S1, S2}` | `[S1, S2]` |
| `findSlot("S")` | `"S1"` | `{S1, S2}` | `[S1, S2]` |
| `occupySlot("S1")` | `True` | `{S2}` | `[S1, S2]`, `S1` is now old |
| `occupySlot("S1")` | `False`, already occupied | `{S2}` | `[S1, S2]` |
| `findSlot("S")` | pops old `S1`, returns `"S2"` | `{S2}` | `[S2]` |


## Do We Need a Design Pattern?

Two patterns look like a good fit at first. Neither helps here.

**State pattern.** A locker is either free or occupied, so it looks like a small state machine. But there are only two states and two simple rules: you cannot occupy a locker that is already occupied, and you cannot free a locker that is already free. One boolean and one `if` handle this. The State pattern would need a base class and two state classes for the same result.

**Strategy pattern.** It is useful when there are many ways to pick a locker, like nearest first or least used first. Here there is only one rule: smallest id. A strategy class with a single implementation is extra code with no benefit.

If a future version asks for different ways to pick a locker, Strategy would be the first thing to add.


## Complexity

n is the number of lockers.

| Operation | Time |
|---|---|
| `addSlot` | O(log n) |
| `findSlot` | O(log n) on average |
| `occupySlot` | O(1) |
| `freeSlot` | O(log n) |

One `findSlot` call may pop many old ids at once. But each old id is popped only once in total, so over many calls it works out to O(log n) per call.

Space: O(n + k), where k is the number of `occupySlot` calls, because old ids can wait in the heap until they reach the top.


## Python Code

```python
import heapq


class Locker:
    """One locker. It knows its id, its size and if a package is inside."""

    def __init__(self, lockerId: str, size: str):
        self.id = lockerId
        self.size = size
        self.occupied = False


class LockerGroup:
    """
    All lockers of one size.

    Python has no built-in sorted set, so we build one from two parts:
    - freeIds: set of ids that are free right now
    - heap: min-heap of ids, so the smallest id is on top.
      It can still hold ids that became occupied. We skip those later.
    """

    def __init__(self, size: str):
        self.size = size
        self.nextNumber = 1
        self.freeIds = set()
        self.heap = []

    def createLocker(self) -> Locker:
        """Creates the next locker of this size: M1, M2, M3 ..."""
        locker = Locker(self.size + str(self.nextNumber), self.size)
        self.nextNumber += 1
        self.freeIds.add(locker.id)
        heapq.heappush(self.heap, locker.id)
        return locker

    def findFreeLocker(self) -> str:
        """Returns the smallest free id, or "" if no locker is free."""
        # Ids on top that are no longer free are old entries. Drop them.
        while self.heap and self.heap[0] not in self.freeIds:
            heapq.heappop(self.heap)
        return self.heap[0] if self.heap else ""

    def occupy(self, locker: Locker) -> None:
        locker.occupied = True
        # Only the set is updated. The old id leaves the heap later,
        # when findFreeLocker sees it on top.
        self.freeIds.remove(locker.id)

    def free(self, locker: Locker) -> None:
        locker.occupied = False
        self.freeIds.add(locker.id)
        heapq.heappush(self.heap, locker.id)


class LockerManager:

    def __init__(self):
        # size -> all lockers of that size. Only the 5 valid sizes are keys.
        self.groups = {}
        for size in ["S", "M", "L", "XL", "XXL"]:
            self.groups[size] = LockerGroup(size)
        # locker id -> locker, so we can reach any locker in one step
        self.lockers = {}

    def addSlot(self, size: str) -> None:
        group = self.groups.get(size)
        if group is None:  # invalid size, do nothing
            return
        locker = group.createLocker()
        self.lockers[locker.id] = locker

    def findSlot(self, size: str) -> str:
        group = self.groups.get(size)
        if group is None:
            return ""
        return group.findFreeLocker()

    def occupySlot(self, lockerId: str) -> bool:
        locker = self.lockers.get(lockerId)
        if locker is None or locker.occupied:
            return False
        self.groups[locker.size].occupy(locker)
        return True

    def freeSlot(self, lockerId: str) -> bool:
        locker = self.lockers.get(lockerId)
        if locker is None or not locker.occupied:
            return False
        self.groups[locker.size].free(locker)
        return True
```