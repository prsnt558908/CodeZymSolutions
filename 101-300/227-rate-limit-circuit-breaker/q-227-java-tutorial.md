# Design Rate Limit Circuit Breaker in Java

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

```java
String state = "CLOSED";
List<Integer> accepted = new ArrayList<>();
int openedAt;

public String handleRequest(int t) {
    if (state.equals("OPEN") && t - openedAt >= openSeconds) {
        state = "HALF_OPEN";
    }
    String before = state;

    if (state.equals("CLOSED")) {
        // scan every accepted request to count the ones inside the window
        int count = 0;
        for (int x : accepted) {
            if (x > t - windowSeconds) count++;
        }
        if (count < requestLimit) {
            accepted.add(t);
            return "ALLOWED," + before;
        }
        state = "OPEN";
        openedAt = t;
        return "REJECTED," + before;
    }

    if (state.equals("OPEN")) {
        return "REJECTED," + before;
    }

    // HALF_OPEN: allow one probe and start a new window with it
    accepted.clear();
    accepted.add(t);
    state = "CLOSED";
    return "ALLOWED," + before;
}
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

```java
int cutoff = t - windowSeconds;
while (!window.isEmpty() && window.peek() <= cutoff) {
    window.poll();
}
if (window.size() < requestLimit) {
    window.add(t);   // allowed
}
```

Each timestamp is added once and removed at most once, so a request costs O(1) on average. We use `ArrayDeque` as the queue because adding at the back and removing from the front are both O(1).

Also, we only add when the size is below `requestLimit`. So the queue never holds more than `requestLimit` timestamps.


## Improvement 2: State Design Pattern

Now let us clean up the `if-else` block. We create a `BreakerState` interface and one class for each state. Every state answers three questions:

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


## Final Java Code

```java
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Circuit breaker that rejects requests when too many arrive in a short time.
 * It is the "context" of the State pattern: it keeps the settings and the
 * current state, and hands every call to that state.
 */
public class CircuitBreaker {

    int requestLimit;
    int windowSeconds;
    int openSeconds;
    BreakerState state;

    public CircuitBreaker(int requestLimit, int windowSeconds, int openSeconds) {
        this.requestLimit = requestLimit;
        this.windowSeconds = windowSeconds;
        this.openSeconds = openSeconds;
        this.state = new ClosedState();
    }

    public String handleRequest(int timestampSeconds) {
        // first find the effective state at this time
        state.refresh(this, timestampSeconds);
        String stateBefore = state.getName();

        boolean allowed = state.handleRequest(this, timestampSeconds);
        return (allowed ? "ALLOWED," : "REJECTED,") + stateBefore;
    }

    public String getState(int timestampSeconds) {
        state.refresh(this, timestampSeconds);
        return state.getName();
    }
}

/**
 * Common contract for CLOSED, OPEN and HALF_OPEN.
 */
interface BreakerState {

    String getName();

    // moves the breaker to the next state if time alone should change it
    void refresh(CircuitBreaker breaker, int timestampSeconds);

    // returns true if the request is allowed, it may also move the breaker to another state
    boolean handleRequest(CircuitBreaker breaker, int timestampSeconds);
}

/**
 * CLOSED: requests are allowed while the sliding window has room.
 */
class ClosedState implements BreakerState {

    // timestamps of accepted requests, oldest at the front
    Queue<Integer> window = new ArrayDeque<>();

    public String getName() {
        return "CLOSED";
    }

    public void refresh(CircuitBreaker breaker, int timestampSeconds) {
        // time alone never changes a CLOSED breaker
    }

    public boolean handleRequest(CircuitBreaker breaker, int timestampSeconds) {
        // active window is (timestampSeconds - windowSeconds, timestampSeconds]
        int cutoff = timestampSeconds - breaker.windowSeconds;
        while (!window.isEmpty() && window.peek() <= cutoff) {
            window.poll();
        }

        if (window.size() < breaker.requestLimit) {
            window.add(timestampSeconds);
            return true;
        }

        // one more request would cross the limit, so the breaker opens
        breaker.state = new OpenState(timestampSeconds);
        return false;
    }
}

/**
 * OPEN: every request is rejected until openSeconds have passed.
 */
class OpenState implements BreakerState {

    int openedAt;

    OpenState(int openedAt) {
        this.openedAt = openedAt;
    }

    public String getName() {
        return "OPEN";
    }

    public void refresh(CircuitBreaker breaker, int timestampSeconds) {
        if (timestampSeconds - openedAt >= breaker.openSeconds) {
            breaker.state = new HalfOpenState();
        }
    }

    public boolean handleRequest(CircuitBreaker breaker, int timestampSeconds) {
        return false;
    }
}

/**
 * HALF_OPEN: the first request goes through as a probe and the breaker closes again.
 */
class HalfOpenState implements BreakerState {

    public String getName() {
        return "HALF_OPEN";
    }

    public void refresh(CircuitBreaker breaker, int timestampSeconds) {
        // stays HALF_OPEN until the probe request arrives
    }

    public boolean handleRequest(CircuitBreaker breaker, int timestampSeconds) {
        // a fresh CLOSED state starts a new window holding only the probe
        ClosedState closed = new ClosedState();
        closed.window.add(timestampSeconds);
        breaker.state = closed;
        return true;
    }
}
```