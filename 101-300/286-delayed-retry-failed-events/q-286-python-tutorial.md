# Delayed Retry Scheduling for Failed Events in Python

#### Problem Statement
[https://codezym.com/question/286-delayed-retry-failed-events](https://codezym.com/question/286-delayed-retry-failed-events)


## Core Idea

We need to park failed events until their retry time, and later hand back every event that is due, sorted by time and then by `eventId`.


Think of it as a waiting room where we always call the person whose turn comes first. A **min-heap** does exactly this. It keeps the earliest retry at the top, so we never have to scan every pending event.


The only tricky part is rescheduling. Removing an old entry from the middle of a heap is slow, so we leave it there.


A **dictionary** (hash map) remembers the latest time of each event. When an old entry reaches the top, the dictionary tells us it is outdated and we simply skip it. This trick is called **lazy deletion**.


---


## Solution 1: Brute Force with a Dictionary

Store every pending event in a dictionary: `eventId -> timestamp`.


Scheduling is a single assignment. It adds a new event or replaces the old time, so rescheduling comes for free.


To get ready events, scan the whole dictionary, keep the events with time `<= currentTimestamp`, sort them and remove them from the dictionary.

```python
def __init__(self):
    # eventId -> scheduled timestamp
    self.scheduledTimes = {}

def scheduleRetry(self, eventId: str, executeAtTimestamp: int) -> None:
    self.scheduledTimes[eventId] = executeAtTimestamp  # adds or replaces

def getEventsToExecuteNow(self, currentTimestamp: int) -> List[str]:
    # 1. Scan every pending event, keep the ready ones as (time, id) pairs
    ready = []
    for eventId, timestamp in self.scheduledTimes.items():
        if timestamp <= currentTimestamp:
            ready.append((timestamp, eventId))

    # 2. Sort by timestamp, then by eventId (tuples compare in this order)
    ready.sort()

    # 3. Remove the returned events
    readyEvents = []
    for timestamp, eventId in ready:
        readyEvents.append(eventId)
        del self.scheduledTimes[eventId]
    return readyEvents
```

### Why this is slow

Every `getEventsToExecuteNow` call scans **all** pending events, even when nothing is ready.


With 50,000 pending events and 50,000 calls, that is 2.5 billion checks. The problem asks us to avoid exactly this full scan.


What we really need is a structure that always knows which event is due next.


---


## Solution 2: Min-Heap + Dictionary (Optimal)

### Step 1: Keep the earliest retry on top

Put every scheduled retry into a min-heap. The `heapq` module keeps a plain list in heap order, so `retryHeap[0]` is always the smallest entry.


We need a small `RetryEntry` class because each heap entry must carry two values, `timestamp` and `eventId`, and be sorted by both. Its `__lt__` method orders entries by timestamp first, and by `eventId` when timestamps are equal.


A `(timestamp, eventId)` tuple would sort the same way. The class just makes the code easier to read, with `entry.timestamp` instead of `entry[0]`.


Now `getEventsToExecuteNow` only needs to look at the top:

- If the top's timestamp is `<= currentTimestamp`, pop it and add it to the result.
- Otherwise, stop. Every other entry has the same or a later timestamp, so nothing else is ready.


Entries come out in exactly the order the problem wants, so no extra sorting is needed.


### Step 2: The rescheduling problem

Say `payment-42` is scheduled at `5000` and then moved to `9000`.


The old entry `(5000, payment-42)` is still inside the heap. A call at time `6000` would find it at the top and return the event too early.


Removing it right away is slow. A heap only gives fast access to its top, so finding an entry in the middle means searching the whole heap. `heapq` has no remove function, so we would need `list.remove()` followed by `heapq.heapify()`, and both take O(n) time.


### Step 3: Fix it with lazy deletion

Leave the old entry in the heap and just push the new one.


Also keep a dictionary, `scheduledTimes`, that stores `eventId -> latest timestamp` for every pending event. It is our single source of truth, and it tells us in O(1) whether a heap entry is still valid.


Each time we pop an entry, we check it against the dictionary:

- **Times match:** this is the real schedule. Add the event to the result and remove it from the dictionary.
- **Times differ, or the event is not in the dictionary:** the entry is stale, because the event was rescheduled or already returned. Skip it.


An old entry is thrown away only when it reaches the top, where removing it is cheap. That is why this trick is called lazy deletion.


### Dry Run

Example 3, plus one extra call at the end:

| Call | Heap (top first) | scheduledTimes | Output |
|---|---|---|---|
| `scheduleRetry("order-sync", 30000)` | (30000, order-sync) | order-sync: 30000 | |
| `scheduleRetry("order-sync", 20000)` | (20000, order-sync), (30000, order-sync) | order-sync: 20000 | |
| `getEventsToExecuteNow(25000)` | (30000, order-sync) | empty | `["order-sync"]` |
| `getEventsToExecuteNow(40000)` | empty | empty | `[]` |


In the first get call, `(20000, order-sync)` matches the dictionary, so it is returned. The next top is `30000`, which is greater than `25000`, so we stop.


In the second get call, `(30000, order-sync)` is popped, but `order-sync` is no longer in the dictionary. The entry is stale and is skipped. Without this check, `order-sync` would be returned a second time.


### Python Code

```python
import heapq
from typing import List


class RetryEntry:
    """
    One entry in the heap: which event to run and when.
    Entries are ordered by timestamp, then by eventId.
    """

    def __init__(self, timestamp: int, eventId: str):
        self.timestamp = timestamp
        self.eventId = eventId

    # Earlier timestamp comes first.
    # Same timestamp: smaller eventId comes first.
    # heapq uses this "<" check to keep the smallest entry on top.
    def __lt__(self, other: "RetryEntry") -> bool:
        if self.timestamp != other.timestamp:
            return self.timestamp < other.timestamp
        return self.eventId < other.eventId


class RetryScheduler:

    def __init__(self):
        # Min-heap: the top is always the next retry that should run.
        # It can also hold old (stale) entries of rescheduled events.
        self.retryHeap = []

        # eventId -> latest scheduled timestamp, only for pending events.
        # It tells us whether a heap entry is still valid.
        self.scheduledTimes = {}

    def scheduleRetry(self, eventId: str, executeAtTimestamp: int) -> None:
        """
        Saves the latest time in the dictionary and pushes a new heap entry.
        If the event was already pending, its old heap entry stays in the heap.
        It is now stale and gets skipped later.
        """
        self.scheduledTimes[eventId] = executeAtTimestamp
        heapq.heappush(self.retryHeap, RetryEntry(executeAtTimestamp, eventId))

    def getEventsToExecuteNow(self, currentTimestamp: int) -> List[str]:
        """
        Pops entries from the top of the heap while they are ready.
        The heap already gives them in the required order:
        timestamp, then eventId.
        """
        readyEvents = []

        while self.retryHeap:
            entry = self.retryHeap[0]  # peek at the earliest retry

            # The earliest retry is not ready, so nothing else is ready either.
            if entry.timestamp > currentTimestamp:
                break

            heapq.heappop(self.retryHeap)

            # Stale entry: the event was rescheduled or is already returned.
            if not self.isLatest(entry):
                continue

            readyEvents.append(entry.eventId)
            del self.scheduledTimes[entry.eventId]

        return readyEvents

    def isLatest(self, entry: RetryEntry) -> bool:
        """A heap entry is valid only if it matches the event's latest time."""
        return self.scheduledTimes.get(entry.eventId) == entry.timestamp
```


### Complexity

Let `N` be the number of entries in the heap, including stale ones. `N` is at most the number of `scheduleRetry` calls.

| Method | Time | Extra Space |
|---|---|---|
| `scheduleRetry` | O(log N) | O(1) |
| `getEventsToExecuteNow` | O((R + S) log N) | O(R) for the result |

Here `R` is the number of events returned and `S` is the number of stale entries skipped.


If the top entry is not ready, `getEventsToExecuteNow` just peeks at it and returns, which is O(1).


Each entry is pushed once and popped at most once. So across all calls, skipping stale entries never costs more than the pushes that created them.


**Total space:** O(N) for the heap and the dictionary. Stale entries use a little extra memory until they are popped. That is the small price we pay for fast rescheduling.