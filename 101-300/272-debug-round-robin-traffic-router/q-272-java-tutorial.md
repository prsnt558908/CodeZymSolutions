# Debugging: Round Robin Traffic Router in Java

#### Problem Statement
[https://codezym.com/question/272-debug-round-robin-traffic-router](https://codezym.com/question/272-debug-round-robin-traffic-router)

---

## Core Idea

Round robin works like the hand of a clock moving over a circle of backends. We keep the backends in a list, in the order they were added, and one number called `nextIndex` that marks where the next search starts. No design pattern is needed here: one plain class with a few simple data structures is the simplest and best design.

Every `getBackend` call moves forward from `nextIndex`, skips `"UNAVAILABLE"` backends and returns the first `"AVAILABLE"` one. The hand then moves to the spot right after the chosen backend.

If the hand goes around the full circle without finding anything, we return `""` and the hand stays where it was.

We first build this as a simple scan. Then we make it faster with a sorted set of available positions, so we can jump straight to the next available backend. Strategy and State look like good patterns for this problem at first, and near the end we will see why they only add extra classes here.

---

## Solution 1: Simple Round Robin Scan

### What we store and why

**`Backend` class.** Groups the details of one server: id, hostname, port and state. Its `isAvailable()` method is the only place where the text `"AVAILABLE"` is written. One place means only one place to get the spelling right.

**`List<Backend> backends`.** Round robin must follow the order in which backends were added, and a list keeps that order. `add()` always appends at the end, so an existing backend is never replaced.

**`Map<String, Backend> backendById`.** `updateBackendState` receives an id, not a position. The map finds that backend in O(1) instead of searching the whole list.

**`int nextIndex`.** This is the clock hand. It is a field, so it is remembered between calls. Only `getBackend` changes it, so adding or updating a backend never resets the rotation.

### How `getBackend` works

1. Let `n` be the number of backends.
2. For `step = 0, 1, ..., n - 1`, look at position `(nextIndex + step) % n`. The `% n` makes the search wrap from the end of the list back to the start.
3. The first available backend wins. Set `nextIndex` to the position right after it, `(index + 1) % n`, and return its id.
4. If the loop ends without a winner, return `""`. `nextIndex` is not touched.

The loop runs at most `n` times, so every backend is checked at most once. This also means there is no infinite loop when every backend is unavailable.

If no backend was added, `n` is `0`. The loop never runs, so `% n` is never computed with zero, and we return `""`.

`requestId` is not used at all, because it must not affect the choice.

### Dry run of Example 2

Backends: `east` (available), `central` (unavailable), `west` (available). `nextIndex` starts at `0`.

| Request | Backends checked | Returns | `nextIndex` after |
|---|---|---|---|
| `request-a` | east | `east` | 1 |
| `request-b` | central (skipped), west | `west` | 0 |
| `request-c` | east | `east` | 1 |

### Code

```java
import java.util.*;

/**
 * One backend server.
 */
class Backend {
    String backendId;
    String hostname;
    int port;
    String state;

    Backend(String backendId, String hostname, int port, String state) {
        this.backendId = backendId;
        this.hostname = hostname;
        this.port = port;
        this.state = state;
    }

    // The only place that checks the state text, so a typo cannot hide elsewhere.
    boolean isAvailable() {
        return "AVAILABLE".equals(state);
    }
}

public class TrafficRouter {

    // Backends in the order they were added. This is the round-robin order.
    List<Backend> backends;

    // backendId -> backend, so updateBackendState does not have to search the list.
    Map<String, Backend> backendById;

    // Index where the next search starts. Only getBackend changes it.
    int nextIndex;

    public TrafficRouter() {
        backends = new ArrayList<>();
        backendById = new HashMap<>();
        nextIndex = 0;
    }

    public void addBackend(String backendId, String hostname, int port, String state) {
        Backend backend = new Backend(backendId, hostname, port, state);
        backends.add(backend); // add at the end, never replace a backend
        backendById.put(backendId, backend);
    }

    public void updateBackendState(String backendId, String state) {
        backendById.get(backendId).state = state;
    }

    public String getBackend(String requestId) {
        int n = backends.size();

        // Check each backend at most once, starting at nextIndex and wrapping around.
        for (int step = 0; step < n; step++) {
            int index = (nextIndex + step) % n;
            Backend backend = backends.get(index);
            if (backend.isAvailable()) {
                // The next search starts right after this backend.
                nextIndex = (index + 1) % n;
                return backend.backendId;
            }
        }

        // No backends, or all of them are unavailable. nextIndex stays the same.
        return "";
    }
}
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
| Resetting the position on every request | `nextIndex` is a field. It is set to `0` only in the constructor, never at the start of `getBackend`. |
| Advancing the position incorrectly | Move to the spot after the **chosen** backend, `(index + 1) % n`. Using `nextIndex + 1` ignores the skipped backends. |
| Incorrect wrap-around | Use `(nextIndex + step) % n`, so the search goes from the last backend back to the first. |
| Returning unavailable backends | Return a backend only when `isAvailable()` is `true`. |
| Infinite loop when all are unavailable | Loop at most `n` times, never `while (true)`. |
| Overwriting existing backends | Append with `backends.add(...)`. Never use `set(...)` or create a new list inside `addBackend`. |
| Updating the wrong backend | Find the backend by `backendId` in the map, not by position or by the last added backend. |
| Misspelled or inconsistent states | Compare the state in one place only, `isAvailable()`, with `.equals()` (not `==`) and the exact text `"AVAILABLE"`. |

Fix a test only when its expected value breaks these rules. For example, a test expecting `"central"` in Example 2 is wrong, because `"central"` is unavailable.

---

## Solution 2: Jump Straight to the Next Available Backend

### The problem with scanning

Solution 1 is correct, but it can be slow. Imagine 100,000 backends where only the last one is available. Every call walks over 99,999 unavailable backends before it finds the right one. With 100,000 calls, that is about 10 billion checks.

The scan wastes time on backends that cannot be picked. So let us keep a separate record of only the ones that can.

### The idea

The scan in Solution 1 visits positions `nextIndex, nextIndex + 1, ..., n - 1` first, and then `0, 1, ..., nextIndex - 1`. So the backend it finds is always:

- the smallest available position that is `>= nextIndex`, or
- if there is none, the smallest available position overall. This is the wrap-around.

If we keep the positions of available backends in a **sorted set**, both answers come straight from the set:

- `ceiling(nextIndex)` returns the smallest position `>= nextIndex`, or `null` if there is none. That is why the code keeps it in an `Integer`, not an `int`.
- `first()` returns the smallest position.

Both take O(log n), so we jump straight to the answer instead of walking to it.

### What changes

**`TreeSet<Integer> availablePositions`.** Positions of available backends only, kept sorted. `TreeSet` is Java's sorted set, and it gives us `ceiling()` and `first()`.

**`Map<String, Integer> positionById`.** The set stores positions, so when a state changes we need the backend's position. This map replaces `backendById`. The `Backend` object is still easy to reach with `backends.get(position)`.

We keep the set in sync with the states:

- `addBackend`: if the new backend is available, add its position.
- `updateBackendState`: add the position when the backend becomes available, remove it when it becomes unavailable. A set ignores a duplicate add and a missing remove, so no extra checks are needed.

`nextIndex` and the rule for moving it stay exactly the same as in Solution 1.

### Dry run of Example 3

`primary` is at position 0 and `secondary` at position 1. Both are available, so `availablePositions = {0, 1}` and `nextIndex = 0`.

| Step | `availablePositions` | Lookup | Returns | `nextIndex` after |
|---|---|---|---|---|
| `request-1` | {0, 1} | `ceiling(0)` = 0 | `primary` | 1 |
| `secondary` becomes unavailable | {0} | | | 1 |
| `request-2` | {0} | `ceiling(1)` = null, so `first()` = 0 | `primary` | 1 |
| `secondary` becomes available | {0, 1} | | | 1 |
| `request-3` | {0, 1} | `ceiling(1)` = 1 | `secondary` | 0 |

### Code

```java
import java.util.*;

/**
 * One backend server.
 */
class Backend {
    String backendId;
    String hostname;
    int port;
    String state;

    Backend(String backendId, String hostname, int port, String state) {
        this.backendId = backendId;
        this.hostname = hostname;
        this.port = port;
        this.state = state;
    }

    // The only place that checks the state text, so a typo cannot hide elsewhere.
    boolean isAvailable() {
        return "AVAILABLE".equals(state);
    }
}

public class TrafficRouter {

    // Backends in the order they were added. This is the round-robin order.
    List<Backend> backends;

    // backendId -> position of that backend in the list.
    Map<String, Integer> positionById;

    // Positions of AVAILABLE backends only, always kept in sorted order.
    TreeSet<Integer> availablePositions;

    // Position where the next search starts. Only getBackend changes it.
    int nextIndex;

    public TrafficRouter() {
        backends = new ArrayList<>();
        positionById = new HashMap<>();
        availablePositions = new TreeSet<>();
        nextIndex = 0;
    }

    public void addBackend(String backendId, String hostname, int port, String state) {
        Backend backend = new Backend(backendId, hostname, port, state);
        int position = backends.size();
        backends.add(backend); // add at the end, never replace a backend
        positionById.put(backendId, position);
        if (backend.isAvailable()) {
            availablePositions.add(position);
        }
    }

    public void updateBackendState(String backendId, String state) {
        int position = positionById.get(backendId);
        Backend backend = backends.get(position);
        backend.state = state;

        // Keep the sorted set in sync with the new state.
        // A set ignores duplicate adds and missing removes, so no extra checks.
        if (backend.isAvailable()) {
            availablePositions.add(position);
        } else {
            availablePositions.remove(position);
        }
    }

    public String getBackend(String requestId) {
        if (availablePositions.isEmpty()) {
            return ""; // nothing can be picked, nextIndex stays the same
        }

        // First available position at or after nextIndex.
        Integer position = availablePositions.ceiling(nextIndex);
        if (position == null) {
            // Nothing at or after nextIndex, so wrap around to the smallest one.
            position = availablePositions.first();
        }

        // The next search starts right after the chosen backend.
        nextIndex = (position + 1) % backends.size();
        return backends.get(position).backendId;
    }
}
```

### Complexity

- `addBackend`: O(log n)
- `updateBackendState`: O(log n)
- `getBackend`: O(log n), no matter how many backends are unavailable
- Space: O(n)

---

## Do We Need a Design Pattern?

Two patterns look like a good fit at first.

**Strategy.** We could hide round robin behind a `SelectionStrategy` interface, so that random or least connections selection could be plugged in later. But this problem asks only for round robin. An interface with a single implementation adds code and gives nothing back. If more selection rules are added in the future, Strategy is the first pattern to reach for.

**State.** Each backend has a state, so the State pattern sounds natural. But the state answers only one yes or no question: can this backend be picked? One `isAvailable()` method answers it. A separate class for each state would be over-engineering.

So one plain class, with the right data structures, is the best design for this problem.