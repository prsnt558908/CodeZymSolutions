# Design a Connection Pool with an Internal Request Queue in Python

#### Problem Statement
[https://codezym.com/question/49-design-connection-pool-internal-request-queue](https://codezym.com/question/49-design-connection-pool-internal-request-queue)

## Core Idea

A connection pool is the textbook example of the **Object Pool** design pattern. Creating a connection is costly, so we create a fixed set of connections once and keep reusing them. Object Pool alone is the best fit for this problem, and no other pattern is needed.

Our `ConnectionPool` class is the pool. It lends out the free connection with the lowest index. When every connection is busy, the request waits in a **FIFO line**. When a connection is released, it goes straight to the oldest waiting request, if there is one. A waiting request can also expire and leave the line.

The real challenge is not the design. It is picking the right data structure for each job, so that every method stays fast.

---

## Why Not Other Design Patterns?

Two patterns look useful at first, but here they only add extra classes.

**Observer.** "Tell the waiting requests when a connection is free" sounds like Observer. But only one request, the oldest, should get the connection. And a waiting request is just an id, with no code of its own to run. So the pool simply takes the oldest id from the line.

**State.** A request goes through stages: waiting, holding a connection, and done. `releaseConnection()` and `expireRequest()` behave differently at each stage. The State pattern would create one class per stage. But our data structures already tell us the stage: an id in the waiting line is waiting, and an id in the connection dict is holding a connection.

---

## Simple Solution (Brute Force)

Let's start with the most direct idea:

- `holder[i]` is the request using connection `i`, or `None` if it is free.
- `waiting` is a list of waiting requests, oldest first.

```python
def __init__(self, capacity: int):
    self.holder = [None] * capacity   # holder[i] = request using connection i, None if free
    self.waiting = []                 # waiting requests, oldest first
```

**Acquire:** scan from index 0 and take the first free connection. If none is free, join the waiting line.

```python
def acquireConnection(self, requestId: str) -> int:
    for i in range(len(self.holder)):
        if self.holder[i] is None:    # the first free one has the lowest index
            self.holder[i] = requestId
            return i
    self.waiting.append(requestId)
    return -1
```

**Release:** find the connection this request holds. Hand it to the oldest waiting request, or mark it free.

```python
def releaseConnection(self, requestId: str) -> bool:
    for i in range(len(self.holder)):
        if self.holder[i] == requestId:
            # hand it to the oldest waiting request, or mark it free
            self.holder[i] = self.waiting.pop(0) if self.waiting else None
            return True
    return False
```

**Expire and query:** remove the request from the list, and collect and sort all holders.

```python
def expireRequest(self, requestId: str) -> None:
    if requestId in self.waiting:   # scans the whole list
        self.waiting.remove(requestId)

def getRequestsWithConnection(self) -> List[str]:
    result = []
    for i in range(len(self.holder)):
        if self.holder[i] is not None:
            result.append(f"{self.holder[i]}-{i}")
    # '-' comes before every letter and digit, so this sorts by requestId
    result.sort()
    return result
```

### What is slow here?

Let `C` be the capacity and `W` the number of waiting requests.

- `acquireConnection()` scans every connection to find a free one: O(C).
- `releaseConnection()` scans every connection to find the request: O(C). Then `waiting.pop(0)` shifts the whole list: O(W).
- `expireRequest()` searches the whole waiting line: O(W).
- `getRequestsWithConnection()` scans every connection and sorts again on every call: O(C log C).

With up to 5000 connections and long waiting lines, these scans add up fast.

---

## Optimized Solution

We fix each slow part with one data structure.

| Job | Data structure | Cost |
|---|---|---|
| Find the lowest free connection | list used as a min-heap with `heapq` | O(log C) |
| Find the connection a request holds | `dict` | O(1) |
| List holders sorted by requestId | sorted `list` with `bisect.insort` | a fast list shift to update, no sorting on query |
| Keep the waiting line | `OrderedDict` | O(1) to add, remove any, or take the oldest |

Two of these need a short explanation.

**Why a sorted list next to the dict?** The dict tells us which connection a request holds. Python has no built-in sorted set, so we keep the same requestIds in a plain list in ascending order. `insort` puts a new id at its right place, and `remove` takes one out. Then `getRequestsWithConnection()` just walks through the list. Together they work like a sorted map, built from two simple pieces. A small helper `assign()` always updates both.

**Why an OrderedDict for the waiting line?** The line needs two things: give us the oldest request (like a queue), and remove any request fast when it expires (like a set). A `deque` is slow at removing from the middle, and a `set` forgets the order. An `OrderedDict` remembers the order in which keys were added. So `popitem(last=False)` gives the oldest request and `pop(requestId, None)` removes any request, both in O(1).

A plain `dict` also keeps insertion order, but taking its oldest key again and again gets slow after many deletions. `OrderedDict` stays fast.

### One Key Observation

A request waits only when every connection is busy. This gives us two simple rules:

- **Release with someone waiting:** the released connection is the only free one. So we hand it straight to the oldest waiting request. It never goes back to the heap.
- **Acquire with a free connection:** if a connection is free, nobody is waiting. So a new request can take it right away without jumping the line.

### Edge Cases

- Releasing a request that is still waiting, already released, expired or unknown returns `False`. None of them is in the dict.
- Expiring a request that holds a connection does nothing. Only waiting requests are in the waiting line.

### Walkthrough

Example 3 from the problem, with capacity 1:

| Call | Returns | Holders | Waiting line |
|---|---|---|---|
| `acquireConnection("a1")` | `0` | a1-0 | empty |
| `acquireConnection("b1")` | `-1` | a1-0 | b1 |
| `acquireConnection("b2")` | `-1` | a1-0 | b1, b2 |
| `expireRequest("b1")` | nothing | a1-0 | b2 |
| `releaseConnection("a1")` | `True` | b2-0 | empty |
| `getRequestsWithConnection()` | `["b2-0"]` | b2-0 | empty |

On release, connection 0 goes directly from `a1` to `b2`, the oldest request still waiting.

### Complexity

Let `H` be the number of requests holding a connection (at most `C`).

- `acquireConnection()`: O(log C + H)
- `releaseConnection()`: O(log C + H)
- `expireRequest()`: O(1)
- `getRequestsWithConnection()`: O(H)
- Space: O(C + W)

The `H` part in acquire and release is only the shift inside the sorted list. `H` is at most 5000, and the shift is one fast memory move, so it is very cheap in practice.

---

## Python Code

```python
import heapq
from bisect import insort
from collections import OrderedDict
from typing import List


class ConnectionPool:
    """A pool of reusable connections with ids 0 to capacity - 1.
    Requests that arrive while every connection is busy wait in a FIFO line."""

    def __init__(self, capacity: int):
        # Free connection ids as a min-heap, so the smallest free id is always first.
        # 0, 1, 2, ... is already sorted, and a sorted list is a valid heap.
        self.freeConnections = list(range(capacity))

        # requestId -> id of the connection it is holding right now.
        self.connectionOf = {}

        # requestIds that are holding a connection, always kept sorted.
        self.holders = []

        # requestIds waiting for a connection, oldest first.
        # An OrderedDict keeps insertion order and can still remove any key in O(1).
        # Only its keys are used.
        self.waitingRequests = OrderedDict()

    def acquireConnection(self, requestId: str) -> int:
        if not self.freeConnections:
            self.waitingRequests[requestId] = True   # every connection is busy, so wait in line
            return -1
        connectionId = heapq.heappop(self.freeConnections)   # lowest free index
        self.assign(requestId, connectionId)
        return connectionId

    def releaseConnection(self, requestId: str) -> bool:
        connectionId = self.connectionOf.pop(requestId, None)
        if connectionId is None:
            return False   # unknown, still waiting, expired or already released
        self.holders.remove(requestId)

        if self.waitingRequests:
            # hand the connection straight to the oldest waiting request
            oldest, _ = self.waitingRequests.popitem(last=False)
            self.assign(oldest, connectionId)
        else:
            heapq.heappush(self.freeConnections, connectionId)
        return True

    def expireRequest(self, requestId: str) -> None:
        # Only waiting requests are in here, so a holder is never touched.
        self.waitingRequests.pop(requestId, None)

    def getRequestsWithConnection(self) -> List[str]:
        # holders is already sorted by requestId
        return [f"{requestId}-{self.connectionOf[requestId]}" for requestId in self.holders]

    def assign(self, requestId: str, connectionId: int) -> None:
        """Records that requestId now holds connectionId."""
        self.connectionOf[requestId] = connectionId
        insort(self.holders, requestId)
```