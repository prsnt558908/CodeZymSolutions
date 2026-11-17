# Design Rate Limit Circuit Breaker in Python

#### Problem Statement
[https://codezym.com/question/227-rate-limit-circuit-breaker](https://codezym.com/question/227-rate-limit-circuit-breaker)


## Core Idea

A circuit breaker sits in front of a service and protects it. Unlike the usual circuit breaker, this one does not look at failures. It trips when too many requests arrive in a short time.

The breaker is always in one of three states: `CLOSED`, `OPEN` or `HALF_OPEN`. Each state treats a request in its own way, and the breaker moves between states by itself.

This is exactly what the **State design pattern** is made for, so it is the best fit here. Each state becomes one small class with its own rules, and the breaker just passes every call to its current state.

To count recent requests, we keep a **queue** of accepted timestamps. Old timestamps leave from the front and new ones join at the back. This gives us a fast sliding window.

There is no timer running in the background. Every method call first uses the given timestamp to find the **effective state** (for example, `OPEN` turning into `HALF_OPEN`), and only then does its work.


## How the Three States Behave

| State | What happens to a request | When the state changes |
|---|---|---|
| `CLOSED` | Allowed if the window has room | Moves to `OPEN` when one more request would cross `requestLimit` |
| `OPEN` | Always rejected | Becomes `HALF_OPEN` once `openSeconds` have passed since it opened |
| `HALF_OPEN` | First request is allowed as a probe | Moves back to `CLOSED` right after the probe |


## Basic Solution: One Big If-Else

The most direct way is to keep the state as a string, store every accepted timestamp in a list and write one big `if-else` block.

```python
# inside __init__
self.state = "CLOSED"
self.accepted = []
self.openedAt = 0

def handleRequest(self, t):
    if self.state == "OPEN" and t - self.openedAt >= self.openSeconds:
        self.state = "HALF_OPEN"
    before = self.state

    if self.state == "CLOSED":
        # scan every accepted request to count the ones inside the window
        count = sum(1 for x in self.accepted if x > t - self.windowSeconds)
        if count < self.requestLimit:
            self.accepted.append(t)
            return "ALLOWED," + before
        self.state = "OPEN"
        self.openedAt = t
        return "REJECTED," + before

    if self.state == "OPEN":
        return "REJECTED," + before

    # HALF_OPEN: allow one probe and start a new window with it
    self.accepted = [t]
    self.state = "CLOSED"
    return "ALLOWED," + before
```

This works, but it has two problems:

1. **Slow counting.** Every request scans the whole list, and the list keeps very old timestamps that no longer matter. As requests pile up, each call gets slower and memory keeps growing.
2. **Messy state logic.** The rules of all three states are mixed in one method. The `OPEN` to `HALF_OPEN` check must also be copied into `getState`. Changing one state, like allowing 3 probe requests, means editing this big block and risking the others.

Let us fix them one at a time.


## Improvement 1: Sliding Window with a Queue

Timestamps never go down. So the oldest accepted request is always at the front and the newest is at the back. That is exactly how a **queue** works.

For each request in `CLOSED`:

1. Remove timestamps from the front while they are outside the window, that is `x <= t - windowSeconds`.
2. Everything left is inside the window, so the queue size is the count.
3. If the size is less than `requestLimit`, add `t` at the back and allow the request. Otherwise, reject it and open the breaker.

```python
cutoff = t - self.windowSeconds
while self.window and self.window[0] <= cutoff:
    self.window.popleft()
if len(self.window) < self.requestLimit:
    self.window.append(t)   # allowed
```

Each timestamp is added once and removed at most once, so a request costs O(1) on average.

We use `deque` from `collections` as the queue. Removing from the front of a normal `list` is slow, because every other item has to shift. A `deque` adds at the back and removes from the front in O(1).

Also, we only add when the size is below `requestLimit`. So the queue never holds more than `requestLimit` timestamps.


## Improvement 2: State Design Pattern

Now let us clean up the `if-else` block. We create a `BreakerState` abstract class (built with `ABC`) that works as our interface, and one class for each state. Every state answers three questions:

- `getName()`: what is my name?
- `refresh(...)`: should time alone move me to another state?
- `handleRequest(...)`: should this request be allowed?

`CircuitBreaker` is the **context**. It only keeps the settings and the current state object.

Both public methods start the same way. They call `state.refresh(...)`, so `state` now holds the effective state. Then `getState` returns its name, and `handleRequest` asks it to handle the request.

`handleRequest` saves the state name first, because the answer must show the state from before the request.

Each state also keeps **only the data it needs**:

- **`ClosedState`** owns the queue of accepted timestamps.
- **`OpenState`** owns `openedAt`. Its `refresh` switches the breaker to `HalfOpenState` once `openSeconds` have passed.
- **`HalfOpenState`** needs no data. It allows the probe and replaces itself with a brand new `ClosedState` that holds only the probe. So "start a new window" comes for free.

To change state, a state object simply sets `breaker.state` to the next state.

This is better than the simple `if-else` code. Each state's rules and data live in one place. Changing one state, like allowing 3 probe requests in `HALF_OPEN`, only touches that one class.

**Why not Strategy?** Strategy also puts each behavior in its own class, so it looks similar. But a strategy is chosen from outside and usually stays fixed. Our breaker changes its own behavior as requests and time pass. That self-switching is exactly what the State pattern is for.


## Complexity

- `handleRequest`: O(1) on average.
- `getState`: O(1).
- Memory: O(requestLimit), because the queue never holds more than `requestLimit` timestamps.


## Final Python Code

```python
from abc import ABC, abstractmethod
from collections import deque


class CircuitBreaker:
    """
    Circuit breaker that rejects requests when too many arrive in a short time.
    It is the "context" of the State pattern: it keeps the settings and the
    current state, and hands every call to that state.
    """

    def __init__(self, requestLimit: int, windowSeconds: int, openSeconds: int):
        self.requestLimit = requestLimit
        self.windowSeconds = windowSeconds
        self.openSeconds = openSeconds
        self.state = ClosedState()

    def handleRequest(self, timestampSeconds: int) -> str:
        # first find the effective state at this time
        self.state.refresh(self, timestampSeconds)
        stateBefore = self.state.getName()

        allowed = self.state.handleRequest(self, timestampSeconds)
        return ("ALLOWED," if allowed else "REJECTED,") + stateBefore

    def getState(self, timestampSeconds: int) -> str:
        self.state.refresh(self, timestampSeconds)
        return self.state.getName()


class BreakerState(ABC):
    """Common contract for CLOSED, OPEN and HALF_OPEN."""

    @abstractmethod
    def getName(self) -> str:
        """Returns the state name, like "CLOSED"."""

    @abstractmethod
    def refresh(self, breaker: CircuitBreaker, timestampSeconds: int) -> None:
        """Moves the breaker to the next state if time alone should change it."""

    @abstractmethod
    def handleRequest(self, breaker: CircuitBreaker, timestampSeconds: int) -> bool:
        """Returns True if the request is allowed. It may also move the breaker to another state."""


class ClosedState(BreakerState):
    """CLOSED: requests are allowed while the sliding window has room."""

    def __init__(self):
        # timestamps of accepted requests, oldest at the front
        self.window = deque()

    def getName(self) -> str:
        return "CLOSED"

    def refresh(self, breaker: CircuitBreaker, timestampSeconds: int) -> None:
        # time alone never changes a CLOSED breaker
        pass

    def handleRequest(self, breaker: CircuitBreaker, timestampSeconds: int) -> bool:
        # active window is (timestampSeconds - windowSeconds, timestampSeconds]
        cutoff = timestampSeconds - breaker.windowSeconds
        while self.window and self.window[0] <= cutoff:
            self.window.popleft()

        if len(self.window) < breaker.requestLimit:
            self.window.append(timestampSeconds)
            return True

        # one more request would cross the limit, so the breaker opens
        breaker.state = OpenState(timestampSeconds)
        return False


class OpenState(BreakerState):
    """OPEN: every request is rejected until openSeconds have passed."""

    def __init__(self, openedAt: int):
        self.openedAt = openedAt

    def getName(self) -> str:
        return "OPEN"

    def refresh(self, breaker: CircuitBreaker, timestampSeconds: int) -> None:
        if timestampSeconds - self.openedAt >= breaker.openSeconds:
            breaker.state = HalfOpenState()

    def handleRequest(self, breaker: CircuitBreaker, timestampSeconds: int) -> bool:
        return False


class HalfOpenState(BreakerState):
    """HALF_OPEN: the first request goes through as a probe and the breaker closes again."""

    def getName(self) -> str:
        return "HALF_OPEN"

    def refresh(self, breaker: CircuitBreaker, timestampSeconds: int) -> None:
        # stays HALF_OPEN until the probe request arrives
        pass

    def handleRequest(self, breaker: CircuitBreaker, timestampSeconds: int) -> bool:
        # a fresh CLOSED state starts a new window holding only the probe
        closed = ClosedState()
        closed.window.append(timestampSeconds)
        breaker.state = closed
        return True
```