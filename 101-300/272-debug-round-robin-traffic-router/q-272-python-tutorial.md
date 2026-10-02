# Debugging: Round Robin Traffic Router in Python

#### Problem Statement
[https://codezym.com/question/272-debug-round-robin-traffic-router](https://codezym.com/question/272-debug-round-robin-traffic-router)

---

## Core Idea

Round robin works like the hand of a clock moving over a circle of backends. We keep the backends in a list, in the order they were added, and one number called `next_index` that marks where the next search starts. No design pattern is needed here: one plain class with a few simple data structures is the simplest and best design.

Every `getBackend` call moves forward from `next_index`, skips `"UNAVAILABLE"` backends and returns the first `"AVAILABLE"` one. The hand then moves to the spot right after the chosen backend.

If the hand goes around the full circle without finding anything, we return `""` and the hand stays where it was.

We first build this as a simple scan. Then we make it faster with a sorted list of available positions, so we can jump straight to the next available backend. Strategy and State look like good patterns for this problem at first, and near the end we will see why they only add extra classes here.

---

## Solution 1: Simple Round Robin Scan

### What we store and why

**`Backend` class.** Groups the details of one server: id, hostname, port and state. Its `is_available()` method is the only place where the text `"AVAILABLE"` is written. One place means only one place to get the spelling right.

**`self.backends` (list).** Round robin must follow the order in which backends were added, and a list keeps that order. `append()` always adds at the end, so an existing backend is never replaced.

**`self.backend_by_id` (dict).** `updateBackendState` receives an id, not a position. The dict finds that backend in O(1) instead of searching the whole list.

**`self.next_index`.** This is the clock hand. It lives on `self`, so it is remembered between calls. Only `getBackend` changes it, so adding or updating a backend never resets the rotation.

### How `getBackend` works

1. Let `n` be the number of backends.
2. For `step = 0, 1, ..., n - 1`, look at position `(next_index + step) % n`. The `% n` makes the search wrap from the end of the list back to the start.
3. The first available backend wins. Set `next_index` to the position right after it, `(index + 1) % n`, and return its id.
4. If the loop ends without a winner, return `""`. `next_index` is not touched.

The loop runs at most `n` times, so every backend is checked at most once. This also means there is no infinite loop when every backend is unavailable.

If no backend was added, `n` is `0`. The loop never runs, so `% n` is never computed with zero, and we return `""`.

`requestId` is not used at all, because it must not affect the choice.

### Dry run of Example 2

Backends: `east` (available), `central` (unavailable), `west` (available). `next_index` starts at `0`.

| Request | Backends checked | Returns | `next_index` after |
|---|---|---|---|
| `request-a` | east | `east` | 1 |
| `request-b` | central (skipped), west | `west` | 0 |
| `request-c` | east | `east` | 1 |

### Code

```python
class Backend:
    """One backend server."""

    def __init__(self, backend_id, hostname, port, state):
        self.backend_id = backend_id
        self.hostname = hostname
        self.port = port
        self.state = state

    def is_available(self):
        # The only place that checks the state text, so a typo cannot hide elsewhere.
        return self.state == "AVAILABLE"


class TrafficRouter:
    def __init__(self):
        # Backends in the order they were added. This is the round-robin order.
        self.backends = []
        # backendId -> Backend, so updateBackendState does not have to search the list.
        self.backend_by_id = {}
        # Index where the next search starts. Only getBackend changes it.
        self.next_index = 0

    def addBackend(self, backendId, hostname, port, state):
        backend = Backend(backendId, hostname, port, state)
        self.backends.append(backend)  # add at the end, never replace a backend
        self.backend_by_id[backendId] = backend

    def updateBackendState(self, backendId, state):
        self.backend_by_id[backendId].state = state

    def getBackend(self, requestId):
        n = len(self.backends)

        # Check each backend at most once, starting at next_index and wrapping around.
        for step in range(n):
            index = (self.next_index + step) % n
            backend = self.backends[index]
            if backend.is_available():
                # The next search starts right after this backend.
                self.next_index = (index + 1) % n
                return backend.backend_id

        # No backends, or all of them are unavailable. next_index stays the same.
        return ""
```

### Complexity

- `addBackend`: O(1)
- `updateBackendState`: O(1)
- `getBackend`: O(n) in the worst case, when most backends are unavailable. O(1) when they are all available.
- Space: O(n)

---

## Bugs to Look For in the Debugging Round

In the interview you get code close to Solution 1, with a few planted bugs. Here is what the correct code does for each common bug.

| Bug | Correct behavior |
|---|---|
| Resetting the position on every request | `self.next_index` is set to `0` only in `__init__`, never at the start of `getBackend`. Writing `next_index = ...` without `self.` creates a local variable and the position is lost. |
| Advancing the position incorrectly | Move to the spot after the **chosen** backend, `(index + 1) % n`. Using `self.next_index + 1` ignores the skipped backends. |
| Incorrect wrap-around | Use `(self.next_index + step) % n`, so the search goes from the last backend back to the first. |
| Returning unavailable backends | Return a backend only when `is_available()` is `True`. |
| Infinite loop when all are unavailable | Loop at most `n` times, never `while True`. |
| Overwriting existing backends | Add with `self.backends.append(...)`. Never assign to an existing index or create a new list inside `addBackend`. |
| Updating the wrong backend | Find the backend by `backendId` in the dict, not by position or by the last added backend. |
| Misspelled or inconsistent states | Compare the state in one place only, `is_available()`, with `==` (not `is`) and the exact text `"AVAILABLE"`. |

Fix a test only when its expected value breaks these rules. For example, a test expecting `"central"` in Example 2 is wrong, because `"central"` is unavailable.

---

## Solution 2: Jump Straight to the Next Available Backend

### The problem with scanning

Solution 1 is correct, but it can be slow. Imagine 100,000 backends where only the last one is available. Every call walks over 99,999 unavailable backends before it finds the right one. With 100,000 calls, that is about 10 billion checks.

The scan wastes time on backends that cannot be picked. So let us keep a separate record of only the ones that can.

### The idea

The scan in Solution 1 visits positions `next_index, next_index + 1, ..., n - 1` first, and then `0, 1, ..., next_index - 1`. So the backend it finds is always:

- the smallest available position that is `>= next_index`, or
- if there is none, the smallest available position overall. This is the wrap-around.

If we keep the positions of available backends in **sorted order**, both answers can be found with binary search instead of a walk.

Python has no built-in sorted set, so we use a plain list that we always keep sorted, together with the `bisect` module:

- `bisect_left(available_positions, next_index)` returns the index of the first position `>= next_index`. If it returns the length of the list, there is no such position, so we wrap to index `0`.
- `insort(available_positions, position)` inserts a position in its sorted place.
- To remove a position, we find its index with `bisect_left` and `pop` it.

### What changes

**`self.available_positions` (sorted list).** Positions of available backends only. It answers "which available backend comes next?" with one binary search.

**`self.position_by_id` (dict).** The list stores positions, so when a state changes we need the backend's position. This dict replaces `backend_by_id`. The `Backend` object is still easy to reach with `self.backends[position]`.

We keep the sorted list in sync with the states:

- `addBackend`: if the new backend is available, append its position. It is the largest position so far, so the list stays sorted.
- `updateBackendState`: insert the position when the backend becomes available, remove it when it becomes unavailable. We touch the list only when availability really changes. Unlike a set, a list would keep a duplicate if we inserted the same position twice, and would remove the wrong item if we removed a position that is not there.

`next_index` and the rule for moving it stay exactly the same as in Solution 1.

### Dry run of Example 3

`primary` is at position 0 and `secondary` at position 1. Both are available, so `available_positions = [0, 1]` and `next_index = 0`.

| Step | `available_positions` | Lookup | Returns | `next_index` after |
|---|---|---|---|---|
| `request-1` | [0, 1] | first position `>= 0` is 0 | `primary` | 1 |
| `secondary` becomes unavailable | [0] | | | 1 |
| `request-2` | [0] | no position `>= 1`, wrap to 0 | `primary` | 1 |
| `secondary` becomes available | [0, 1] | | | 1 |
| `request-3` | [0, 1] | first position `>= 1` is 1 | `secondary` | 0 |

### Code

```python
from bisect import bisect_left, insort


class Backend:
    """One backend server."""

    def __init__(self, backend_id, hostname, port, state):
        self.backend_id = backend_id
        self.hostname = hostname
        self.port = port
        self.state = state

    def is_available(self):
        # The only place that checks the state text, so a typo cannot hide elsewhere.
        return self.state == "AVAILABLE"


class TrafficRouter:
    def __init__(self):
        # Backends in the order they were added. This is the round-robin order.
        self.backends = []
        # backendId -> position of that backend in self.backends.
        self.position_by_id = {}
        # Positions of AVAILABLE backends only, always kept in sorted order.
        self.available_positions = []
        # Position where the next search starts. Only getBackend changes it.
        self.next_index = 0

    def addBackend(self, backendId, hostname, port, state):
        backend = Backend(backendId, hostname, port, state)
        position = len(self.backends)
        self.backends.append(backend)  # add at the end, never replace a backend
        self.position_by_id[backendId] = position
        if backend.is_available():
            # The largest position so far, so appending keeps the list sorted.
            self.available_positions.append(position)

    def updateBackendState(self, backendId, state):
        position = self.position_by_id[backendId]
        backend = self.backends[position]
        was_available = backend.is_available()
        backend.state = state

        # Touch the sorted list only when availability really changes.
        if not was_available and backend.is_available():
            insort(self.available_positions, position)
        elif was_available and not backend.is_available():
            i = bisect_left(self.available_positions, position)
            self.available_positions.pop(i)

    def getBackend(self, requestId):
        if not self.available_positions:
            return ""  # nothing can be picked, next_index stays the same

        # Index of the first available position at or after next_index.
        i = bisect_left(self.available_positions, self.next_index)
        if i == len(self.available_positions):
            # Nothing at or after next_index, so wrap around to the smallest one.
            i = 0

        position = self.available_positions[i]
        # The next search starts right after the chosen backend.
        self.next_index = (position + 1) % len(self.backends)
        return self.backends[position].backend_id
```

### Complexity

- `addBackend`: O(1)
- `getBackend`: O(log n), no matter how many backends are unavailable
- `updateBackendState`: O(log n) to find the spot, plus shifting list items in the worst case, O(n). The shift is one fast memory copy inside Python, so it is far cheaper than looping over backends in Python code.
- Space: O(n)

---

## Do We Need a Design Pattern?

Two patterns look like a good fit at first.

**Strategy.** We could hide round robin behind a `SelectionStrategy` class, so that random or least connections selection could be plugged in later. But this problem asks only for round robin. A strategy with a single implementation adds code and gives nothing back. If more selection rules are added in the future, Strategy is the first pattern to reach for.

**State.** Each backend has a state, so the State pattern sounds natural. But the state answers only one yes or no question: can this backend be picked? One `is_available()` method answers it. A separate class for each state would be over-engineering.

So one plain class, with the right data structures, is the best design for this problem.