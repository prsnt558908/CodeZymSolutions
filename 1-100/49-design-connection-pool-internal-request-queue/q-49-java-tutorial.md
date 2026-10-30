# Design a Connection Pool with an Internal Request Queue in Java

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

**State.** A request goes through stages: waiting, holding a connection, and done. `releaseConnection()` and `expireRequest()` behave differently at each stage. The State pattern would create one class per stage. But our data structures already tell us the stage: an id in the waiting line is waiting, and an id in the connection map is holding a connection.

---

## Simple Solution (Brute Force)

Let's start with the most direct idea:

- `holder[i]` is the request using connection `i`, or `null` if it is free.
- `waiting` is a list of waiting requests, oldest first.

```java
String[] holder;                             // holder[i] = request using connection i, null if free
List<String> waiting = new ArrayList<>();    // waiting requests, oldest first

public ConnectionPool(int capacity) {
    holder = new String[capacity];
}
```

**Acquire:** scan from index 0 and take the first free connection. If none is free, join the waiting line.

```java
public int acquireConnection(String requestId) {
    for (int i = 0; i < holder.length; i++) {
        if (holder[i] == null) {        // the first free one has the lowest index
            holder[i] = requestId;
            return i;
        }
    }
    waiting.add(requestId);
    return -1;
}
```

**Release:** find the connection this request holds. Hand it to the oldest waiting request, or mark it free.

```java
public boolean releaseConnection(String requestId) {
    for (int i = 0; i < holder.length; i++) {
        if (requestId.equals(holder[i])) {
            // hand it to the oldest waiting request, or mark it free
            holder[i] = waiting.isEmpty() ? null : waiting.remove(0);
            return true;
        }
    }
    return false;
}
```

**Expire and query:** remove the request from the list, and collect and sort all holders.

```java
public void expireRequest(String requestId) {
    waiting.remove(requestId);   // scans the whole list
}

public List<String> getRequestsWithConnection() {
    List<String> result = new ArrayList<>();
    for (int i = 0; i < holder.length; i++) {
        if (holder[i] != null) {
            result.add(holder[i] + "-" + i);
        }
    }
    // '-' comes before every letter and digit, so this sorts by requestId
    Collections.sort(result);
    return result;
}
```

### What is slow here?

Let `C` be the capacity and `W` the number of waiting requests.

- `acquireConnection()` scans every connection to find a free one: O(C).
- `releaseConnection()` scans every connection to find the request: O(C). Then `waiting.remove(0)` shifts the whole list: O(W).
- `expireRequest()` searches the whole waiting line: O(W).
- `getRequestsWithConnection()` scans every connection and sorts again on every call: O(C log C).

With up to 5000 connections and long waiting lines, these scans add up fast.

---

## Optimized Solution

We fix each slow part with one data structure.

| Job | Data structure | Cost |
|---|---|---|
| Find the lowest free connection | `PriorityQueue<Integer>` (min-heap) | O(log C) |
| Find the connection a request holds | `HashMap<String, Integer>` | O(1) |
| List holders sorted by requestId | `TreeSet<String>` (sorted set) | O(log C) to update, no sorting on query |
| Keep the waiting line | `LinkedHashSet<String>` | O(1) to add, remove any, or take the oldest |

Two of these need a short explanation.

**Why a sorted set next to the map?** The map tells us which connection a request holds. The sorted set keeps the same requestIds in ascending order, so `getRequestsWithConnection()` just walks through it. Together they work like a sorted map, built from two simple pieces. A small helper `assign()` always updates both.

**Why a LinkedHashSet for the waiting line?** The line needs two things: give us the oldest request (like a queue), and remove any request fast when it expires (like a set). A plain queue is slow at removing from the middle, and a plain set forgets the order. A `LinkedHashSet` is a set that also remembers the order in which items were added, so it does both in O(1).

### One Key Observation

A request waits only when every connection is busy. This gives us two simple rules:

- **Release with someone waiting:** the released connection is the only free one. So we hand it straight to the oldest waiting request. It never goes back to the heap.
- **Acquire with a free connection:** if a connection is free, nobody is waiting. So a new request can take it right away without jumping the line.

### Edge Cases

- Releasing a request that is still waiting, already released, expired or unknown returns `false`. None of them is in the map.
- Expiring a request that holds a connection does nothing. Only waiting requests are in the waiting set.

### Walkthrough

Example 3 from the problem, with capacity 1:

| Call | Returns | Holders | Waiting line |
|---|---|---|---|
| `acquireConnection("a1")` | `0` | a1-0 | empty |
| `acquireConnection("b1")` | `-1` | a1-0 | b1 |
| `acquireConnection("b2")` | `-1` | a1-0 | b1, b2 |
| `expireRequest("b1")` | nothing | a1-0 | b2 |
| `releaseConnection("a1")` | `true` | b2-0 | empty |
| `getRequestsWithConnection()` | `["b2-0"]` | b2-0 | empty |

On release, connection 0 goes directly from `a1` to `b2`, the oldest request still waiting.

### Complexity

Let `H` be the number of requests holding a connection (at most `C`).

- `acquireConnection()`: O(log C)
- `releaseConnection()`: O(log C)
- `expireRequest()`: O(1)
- `getRequestsWithConnection()`: O(H)
- Space: O(C + W)

---

## Java Code

```java
import java.util.*;

/**
 * A pool of reusable connections with ids 0 to capacity - 1.
 * Requests that arrive while every connection is busy wait in a FIFO line.
 */
public class ConnectionPool {
    // Free connection ids. A min-heap, so the smallest free id is always on top.
    PriorityQueue<Integer> freeConnections = new PriorityQueue<>();

    // requestId -> id of the connection it is holding right now.
    Map<String, Integer> connectionOf = new HashMap<>();

    // requestIds that are holding a connection. A sorted set, so they stay in ascending order.
    TreeSet<String> holders = new TreeSet<>();

    // requestIds waiting for a connection, oldest first.
    // A LinkedHashSet keeps insertion order and can still remove any item in O(1).
    LinkedHashSet<String> waitingRequests = new LinkedHashSet<>();

    public ConnectionPool(int capacity) {
        for (int id = 0; id < capacity; id++) {
            freeConnections.add(id);
        }
    }

    public int acquireConnection(String requestId) {
        if (freeConnections.isEmpty()) {
            waitingRequests.add(requestId);   // every connection is busy, so wait in line
            return -1;
        }
        int connectionId = freeConnections.poll();   // lowest free index
        assign(requestId, connectionId);
        return connectionId;
    }

    public boolean releaseConnection(String requestId) {
        Integer connectionId = connectionOf.remove(requestId);
        if (connectionId == null) {
            return false;   // unknown, still waiting, expired or already released
        }
        holders.remove(requestId);

        if (waitingRequests.isEmpty()) {
            freeConnections.add(connectionId);
        } else {
            // hand the connection straight to the oldest waiting request
            String oldest = waitingRequests.iterator().next();
            waitingRequests.remove(oldest);
            assign(oldest, connectionId);
        }
        return true;
    }

    public void expireRequest(String requestId) {
        // Only waiting requests are in this set, so a holder is never touched.
        waitingRequests.remove(requestId);
    }

    public List<String> getRequestsWithConnection() {
        List<String> result = new ArrayList<>();
        for (String requestId : holders) {   // already sorted by requestId
            result.add(requestId + "-" + connectionOf.get(requestId));
        }
        return result;
    }

    /** Records that requestId now holds connectionId. */
    void assign(String requestId, int connectionId) {
        connectionOf.put(requestId, connectionId);
        holders.add(requestId);
    }
}
```